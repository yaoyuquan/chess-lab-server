package com.github.chess.llm.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.ObjectMappers;
import com.anthropic.core.http.Headers;
import com.anthropic.core.http.Interceptor;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.*;
import com.github.chess.config.AiPlayer;
import com.github.chess.config.ProviderConfig;
import com.github.chess.llm.JsonMoveChoiceParser;
import com.github.chess.llm.LlmCallException;
import com.github.chess.llm.LlmClient;
import com.github.chess.llm.LlmClientRegistry;
import com.github.chess.llm.LlmPayloadLogger;
import com.github.chess.llm.MoveChoice;
import com.github.chess.llm.MoveChoiceSchema;
import com.github.chess.llm.MoveQuery;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import tools.jackson.databind.ObjectMapper;

/**
 * 通过 Anthropic 官方 Java SDK 调用 Claude 选着法。
 * <p>
 * 用结构化输出约束模型只返回 {index, reason}；系统提示词带上缓存标记，
 * 因为同一位棋手每一手的系统提示词完全相同，缓存命中率很高。
 * <p>
 * 一个实例对应一条具名连接，由 {@link LlmClientRegistry} 按需创建。
 *
 * @author yaoyuquan
 */
public class AnthropicLlmClient implements LlmClient {

    /**
     * 单次回复的 token 上限。
     * <p>
     * 推理模型的思考过程也计在这个预算里，给得越宽模型越舍得想，一手棋就越慢。
     * 这里真正需要的输出只有 {index, reason} 不到 50 token，预算主要是留给思考的，
     * 给得宽一些避免高 effort 下频繁截断。截断了会在 parse 里明确报出来。
     */
    private static final long MAX_TOKENS = 131072L;

    private final ObjectMapper objectMapper;
    private final LlmPayloadLogger payloadLogger;
    private final AnthropicClient client;

    public AnthropicLlmClient(ProviderConfig config, ObjectMapper objectMapper,
                              LlmPayloadLogger payloadLogger) {
        this.objectMapper = objectMapper;
        this.payloadLogger = payloadLogger;
        this.client = buildClient(config, payloadLogger);
    }

    /**
     * 构建 SDK 客户端。没有配置密钥时返回 null，isAvailable() 会据此让调用方走兜底。
     * <p>
     * 超时传 {@link Duration#ZERO}，SDK 就按不超时处理：request/read/write 三档全部不限时，
     * 只有建连仍保留 SDK 默认的一分钟——连都连不上是网络问题，跟模型想多久无关，那一档必须留着。
     * 重试次数配 0：一次重试就是再等模型把整个推理过程跑完一遍，代价远高于普通接口的重试。
     * <p>
     * 装一个拦截器只为打日志：真实地址与最终请求头（含 SDK 自己加的那些）是 SDK 内部拼的，
     * 只有到这一层才看得到。
     */
    private static AnthropicClient buildClient(ProviderConfig config, LlmPayloadLogger payloadLogger) {
        if (config == null || !config.hasApiKey()) {
            return null;
        }
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                .apiKey(config.apiKey())
                .timeout(Duration.ZERO)
                .maxRetries(0)
                .addInterceptor(Interceptor.syncOnly((httpClient, request, requestOptions) -> {
                    payloadLogger.logHttpRequest(request.method().name(), request.url(), headersOf(request.headers()));
                    return httpClient.execute(request, requestOptions);
                }));
        if (config.hasBaseUrl()) {
            builder.baseUrl(config.baseUrl());
        }
        return builder.build();
    }

    /**
     * SDK 的请求头结构转成有序的 name -> value，同名多值的用逗号连起来。
     */
    private static Map<String, String> headersOf(Headers headers) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String name : headers.names()) {
            result.put(name, String.join(", ", headers.values(name)));
        }
        return result;
    }

    @Override
    public boolean isAvailable() {
        return client != null;
    }

    @Override
    public MoveChoice choose(MoveQuery query) {
        if (client == null) {
            throw new LlmCallException("未配置 Anthropic API Key");
        }
        AiPlayer player = query.player();
        String systemPrompt = query.chat().system();
        String userPrompt = query.chat().user();
        MessageCreateParams params = MessageCreateParams.builder()
                .model(player.model())
                .maxTokens(MAX_TOKENS)
                // 思考过程和正文共用 max_tokens 预算，推理模型一想起来就能把 4096 全填满，
                // 正文还没开始写就撞上限，回来的 JSON 是残缺的。这里只是从候选编号里挑一个，
                // 用不上长链推理，直接把思考关掉，预算全留给正文
                // 注意：官方模型在 effort 为 xhigh/max 时不接受关闭思考，会返回 400，配置里别往上调
                .thinking(ThinkingConfigDisabled.builder().build())
                .outputConfig(OutputConfig.builder()
                        .effort(effortOf(player))
                        .format(MoveChoiceSchema.asAnthropicFormat())
                        .build())
                .systemOfTextBlockParams(List.of(
                        TextBlockParam.builder()
                                .text(systemPrompt)
                                .cacheControl(CacheControlEphemeral.builder().build())
                                .build()))
                .addUserMessage(userPrompt)
                .build();

        // SDK 内部封装了 HTTP，这里用它自带的 mapper 把请求体序列化成与实际报文一致的 JSON
        payloadLogger.logRequest("messages.create", toJson(params._body()));

        // 一手棋慢在哪，光看请求体是看不出来的，所以把这一次调用的墙上时间量出来
        long startedAt = System.nanoTime();
        Message message;
        try {
            message = client.messages().create(params);
        } catch (AnthropicServiceException e) {
            payloadLogger.logTiming("messages.create", elapsedMillis(startedAt), "调用失败");
            payloadLogger.logFailure("接口返回错误：" + e.getMessage(), e);
            throw new LlmCallException("Anthropic 接口返回错误：" + e.getMessage(), e);
        } catch (RuntimeException e) {
            payloadLogger.logTiming("messages.create", elapsedMillis(startedAt), "调用失败");
            payloadLogger.logFailure("请求未能完成：" + e.getMessage(), e);
            throw new LlmCallException("调用 Anthropic 失败：" + e.getMessage(), e);
        }
        payloadLogger.logTiming("messages.create", elapsedMillis(startedAt), usageOf(message));
        return parse(message);
    }

    /**
     * 从起始时刻算到现在的毫秒数。
     */
    private static long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    /**
     * 把响应里的 token 用量摘成一行。
     * <p>
     * 推理模型慢在输出端，输出 token 数就是最直接的证据：
     * 输入才一千出头却回了上千 token，说明时间都花在思考上，该往下调 effort。
     */
    private static String usageOf(Message message) {
        try {
            return "in=" + message.usage().inputTokens()
                    + " out=" + message.usage().outputTokens()
                    + " stop=" + message.stopReason().map(Object::toString).orElse("-");
        } catch (RuntimeException e) {
            return "用量未知";
        }
    }

    /**
     * 用 SDK 自带的 Jackson mapper 序列化，得到与实际报文一致的 JSON。
     * <p>
     * SDK 走 Jackson 2，本项目注入的 ObjectMapper 是 Jackson 3，注解体系不通用，所以这里不能复用它。
     * 打日志出问题不该影响对局，序列化失败时退回对象自身的 toString。
     */
    private static String toJson(Object value) {
        try {
            return ObjectMappers.jsonMapper().writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    /**
     * 从响应里取出文本块并解析成着法选择。
     */
    private MoveChoice parse(Message message) {
        String text = message.content().stream()
                .map(ContentBlock::text)
                .filter(Optional::isPresent)
                .map(block -> block.get().text())
                .reduce("", String::concat);
        payloadLogger.logResponse(-1, toJson(message));
        if (text.isBlank()) {
            throw new LlmCallException("Anthropic 返回内容为空，stopReason=" + message.stopReason());
        }
        // 撞到 MAX_TOKENS 时 JSON 是残缺的，直接报出来比让解析器抛个含糊的错强
        if (isTruncated(message)) {
            throw new LlmCallException("回复在 " + MAX_TOKENS + " token 处被截断。"
                    + "本地已关闭思考，仍被截断说明服务端没认这个开关，"
                    + "请确认连接指向的服务是否支持 thinking 参数，或放宽 MAX_TOKENS");
        }
        return JsonMoveChoiceParser.parse(objectMapper, text);
    }

    /**
     * 回复是否因为撞到 token 上限而被截断。
     */
    private static boolean isTruncated(Message message) {
        return message.stopReason()
                .map(reason -> "max_tokens".equalsIgnoreCase(reason.toString()))
                .orElse(false);
    }

    /**
     * 棋手配置的 effort 字符串转 SDK 枚举，非法值退回 LOW。
     * <p>
     * 默认取最低档：候选着法已经由后端算好合法性，模型只需从二十来个编号里挑一个，
     * 再高的思考强度换不来多少棋力，却会让一手棋多等十几秒。
     */
    private OutputConfig.Effort effortOf(AiPlayer player) {
        String effort = player.effort();
        if (effort == null) {
            return OutputConfig.Effort.LOW;
        }
        return switch (effort.toLowerCase(Locale.ROOT)) {
            case "medium" -> OutputConfig.Effort.MEDIUM;
            case "high" -> OutputConfig.Effort.HIGH;
            case "xhigh" -> OutputConfig.Effort.XHIGH;
            case "max" -> OutputConfig.Effort.MAX;
            default -> OutputConfig.Effort.LOW;
        };
    }
}

package com.github.chess.llm.claude;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.Headers;
import com.anthropic.core.http.HttpRequest;
import com.anthropic.core.http.HttpRequestBody;
import com.anthropic.core.http.HttpResponse;
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

    /**
     * 开启思考时的思考预算。
     * <p>
     * 接口要求这个值至少 1024 且小于 max_tokens，这里取一个宽裕但留得下正文的数：
     * 预算是上限不是配额，模型想得短就花得少，给宽一点只是别让它在半路被掐断。
     * 没做成配置项是因为开关本身已经够用——真正的分野是想不想，不是想多久。
     */
    private static final long THINKING_BUDGET_TOKENS = 32768L;

    private final ObjectMapper objectMapper;
    private final LlmPayloadLogger payloadLogger;
    private final AnthropicClient client;

    /** 是否开启扩展思考，来自连接配置 */
    private final boolean thinkingEnabled;

    public AnthropicLlmClient(ProviderConfig config, ObjectMapper objectMapper,
                              LlmPayloadLogger payloadLogger) {
        this.objectMapper = objectMapper;
        this.payloadLogger = payloadLogger;
        this.thinkingEnabled = config != null && config.thinkingEnabled();
        this.client = buildClient(config, payloadLogger);
    }

    /**
     * 构建 SDK 客户端。没有配置密钥时返回 null，isAvailable() 会据此让调用方走兜底。
     * <p>
     * 超时传 {@link Duration#ZERO}，SDK 就按不超时处理：request/read/write 三档全部不限时，
     * 只有建连仍保留 SDK 默认的一分钟——连都连不上是网络问题，跟模型想多久无关，那一档必须留着。
     * 重试次数配 0：一次重试就是再等模型把整个推理过程跑完一遍，代价远高于普通接口的重试。
     * <p>
     * 装一个拦截器只为打日志：真实地址、SDK 自己加的请求头与原始报文，只有到这一层才看得到。
     * 报文打的是真正收发的字节，不是把参数对象重新序列化出来的近似结果——排查「对端认不认某个字段」
     * 这类问题时，两者的差别就是能不能定案。
     * <p>
     * 请求头只到 SDK 这一层为止：{@code anthropic-version}、{@code Content-Type} 这些是更底层补的，
     * 日志里看不到。所以别拿日志当「这个头没发」的证据。
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
                    try(HttpRequestBody requestBody = request.body()) {
                        // JSON 报文的 body 本身就是 repeatable，buffered() 会原样返回它。
                        // 这一步是留给将来换成流式 body 的情况：那时不先缓存，打印就把流读空了
                        HttpRequest logged = requestBody == null
                                ? request
                                : request.toBuilder().body(requestBody.buffered()).build();
                        payloadLogger.logRequest(logged.url(), headersOf(logged.headers()), bodyOf(logged));

                        // buffered() 之后 body() 每次都返回新的流，所以读完打日志，SDK 还能照常解析。
                        // 4xx/5xx 也会走到这里，错误响应的原文同样落进日志
                        HttpResponse response = httpClient.execute(logged, requestOptions).buffered();
                        payloadLogger.logResponse(response.statusCode(), readBody(response));
                        return response;
                    }

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

    /**
     * 请求体的原始字节。
     * <p>
     * 调用方必须先把 body 换成 buffered 的那一份，否则这里写一遍就把不可重复的流耗尽了。
     */
    private static String bodyOf(HttpRequest request) {
        try(HttpRequestBody body = request.body()) {
            if (body == null) {
                return "";
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            body.writeTo(buffer);
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }

    /**
     * 响应体的原始字节。
     * <p>
     * 同样要求传进来的是 buffered 过的响应，否则读完 SDK 就没得解析了。
     * 打日志出问题不该影响对局，读失败时只记一句，让调用链继续走。
     */
    private static String readBody(HttpResponse response) {
        try (InputStream in = response.body()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return "<读取响应体失败：" + e.getMessage() + ">";
        }
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
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(player.model())
                .maxTokens(MAX_TOKENS)
                .outputConfig(OutputConfig.builder()
                        .effort(effortOf(player))
                        .format(MoveChoiceSchema.asAnthropicFormat())
                        .build())
                .systemOfTextBlockParams(List.of(
                        TextBlockParam.builder()
                                .text(systemPrompt)
                                .cacheControl(CacheControlEphemeral.builder().build())
                                .build()))
                .addUserMessage(userPrompt);
        applyThinking(builder);
        MessageCreateParams params = builder.build();

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
     * 按连接配置决定开不开扩展思考。
     * <p>
     * 默认关：思考过程和正文共用 max_tokens 预算，推理模型一想起来就能把预算填满，
     * 正文还没开始写就撞上限，回来的 JSON 是残缺的。这条链路只是从候选编号里挑一个，
     * 用不上长链推理，关掉能让一手棋快十几秒。
     * <p>
     * 留这个开关是因为两件事：想对比「让模型真想一遍」和「直接挑」的棋力差别，
     * 只能靠它；另外官方模型在 effort 为 xhigh/max 时不接受关闭思考，会直接返回 400，
     * 想把 effort 配到那两档，就必须同时把思考打开。
     */
    private void applyThinking(MessageCreateParams.Builder builder) {
        if (thinkingEnabled) {
            builder.thinking(ThinkingConfigEnabled.builder()
                    .budgetTokens(THINKING_BUDGET_TOKENS)
                    .build());
            return;
        }
        builder.thinking(ThinkingConfigDisabled.builder().build());
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
     * 从响应里取出文本块并解析成着法选择。
     */
    private MoveChoice parse(Message message) {
        String text = message.content().stream()
                .map(ContentBlock::text)
                .filter(Optional::isPresent)
                .map(block -> block.get().text())
                .reduce("", String::concat);
        if (text.isBlank()) {
            throw new LlmCallException("Anthropic 返回内容为空，stopReason=" + message.stopReason());
        }
        // 撞到 MAX_TOKENS 时 JSON 是残缺的，直接报出来比让解析器抛个含糊的错强
        if (isTruncated(message)) {
            throw new LlmCallException("回复在 " + MAX_TOKENS + " token 处被截断。" + truncationHint());
        }
        return JsonMoveChoiceParser.parse(objectMapper, text);
    }

    /**
     * 截断时该往哪儿查。
     * <p>
     * 同样是截断，开着思考和关着思考要查的方向完全相反：开着就是思考把预算吃光了，
     * 关着却还截断，说明这个开关对端根本没认。把这句话分开写，省得排查时先自己判断一遍。
     */
    private String truncationHint() {
        if (thinkingEnabled) {
            return "本地开着思考，思考预算 " + THINKING_BUDGET_TOKENS
                    + " token，多半是思考把预算吃光了正文没写完，"
                    + "把连接的 thinking 关掉，或者放宽 MAX_TOKENS";
        }
        return "本地已关闭思考，仍被截断说明服务端没认这个开关，"
                + "请确认连接指向的服务是否支持 thinking 参数，或放宽 MAX_TOKENS";
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

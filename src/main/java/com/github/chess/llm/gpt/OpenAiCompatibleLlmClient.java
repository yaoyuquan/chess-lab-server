package com.github.chess.llm.gpt;

import com.github.chess.config.AiPlayer;
import com.github.chess.config.ProviderConfig;
import com.github.chess.llm.JsonHttpClient;
import com.github.chess.llm.JsonHttpResponse;
import com.github.chess.llm.JsonMoveChoiceParser;
import com.github.chess.llm.LlmCallException;
import com.github.chess.llm.LlmClient;
import com.github.chess.llm.LlmClientRegistry;
import com.github.chess.llm.LlmPayloadLogger;
import com.github.chess.llm.MoveChoice;
import com.github.chess.llm.MoveChoiceSchema;
import com.github.chess.llm.MoveQuery;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 通过 OpenAI 兼容接口（/v1/chat/completions）调用模型选着法。
 * <p>
 * 这条路径的 wire 格式与 Anthropic 不同，不能复用官方 SDK，所以直接发 JSON。
 * <p>
 * 完全按 OpenAI 官方的字段来：结构化输出发完整的 json_schema + strict，
 * 思考深度发 reasoning_effort。对端不认这两个字段就是接错了地方，该当场暴露出来。
 * <p>
 * 一个实例对应一条具名连接，由 {@link LlmClientRegistry} 按需创建。
 *
 * @author yaoyuquan
 */
public class OpenAiCompatibleLlmClient implements LlmClient {

    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String PATH = "/chat/completions";

    /**
     * 采样温度。
     * <p>
     * 不放进棋手配置：三家里只有这条路认这个字段，为它在 AiPlayer 上留一格，
     * 另外两家看着都是噪音。取值本身也没什么可调的——模型只是从二十来个编号里挑一个，
     * 高了纯属乱走，低了两个席位每盘棋一模一样，0.6 是留一点变化又不至于发癫。
     */
    private static final double TEMPERATURE = 0.6;

    private final ObjectMapper objectMapper;
    private final LlmPayloadLogger payloadLogger;
    private final JsonHttpClient http;

    public OpenAiCompatibleLlmClient(ProviderConfig config, ObjectMapper objectMapper,
                                     LlmPayloadLogger payloadLogger) {
        this.objectMapper = objectMapper;
        this.payloadLogger = payloadLogger;
        this.http = new JsonHttpClient(config, DEFAULT_BASE_URL);
    }

    @Override
    public boolean isAvailable() {
        return http.isAvailable();
    }

    @Override
    public MoveChoice choose(MoveQuery query) {
        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(
                    buildBody(query.player(), query.chat().system(), query.chat().user()));
        } catch (Exception e) {
            throw new LlmCallException("请求体序列化失败：" + e.getMessage(), e);
        }
        payloadLogger.logRequest(http.endpoint(PATH), http.headers(), requestJson);

        // 一手棋慢在哪，光看请求体是看不出来的，所以把这一次调用的墙上时间量出来
        long startedAt = System.nanoTime();
        JsonHttpResponse response;
        try {
            response = http.post(PATH, requestJson);
        } catch (LlmCallException e) {
            payloadLogger.logTiming(PATH, elapsedMillis(startedAt), "调用失败");
            payloadLogger.logFailure(e.getMessage(), e);
            throw new LlmCallException("调用 OpenAI 兼容接口失败：" + e.getMessage(), e);
        }
        payloadLogger.logResponse(response.status(), response.body());
        payloadLogger.logTiming(PATH, elapsedMillis(startedAt), usageOf(response.body()));

        if (response.isError()) {
            throw new LlmCallException(
                    "OpenAI 兼容接口返回 HTTP " + response.status() + "：" + brief(response.body()));
        }
        return parse(response.body());
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
     * reasoning 单独摘出来是这条路最要紧的一个数：这里真正需要的输出只有 {index, reason} 不到
     * 50 token，剩下的全是推理。配着 low 却仍回来几千 reasoning token，说明对端没认
     * reasoning_effort，再往下调档也是白调——那是换模型或改 effort 取值的信号，不是等下去的理由。
     */
    private String usageOf(String raw) {
        try {
            JsonNode usage = objectMapper.readTree(raw).path("usage");
            if (usage.isMissingNode()) {
                return "响应未带 usage";
            }
            return "in=" + usage.path("prompt_tokens").asInt(-1)
                    + " out=" + usage.path("completion_tokens").asInt(-1)
                    + " reasoning=" + usage.path("completion_tokens_details")
                            .path("reasoning_tokens").asInt(-1);
        } catch (RuntimeException e) {
            return "用量未知";
        }
    }

    /**
     * 拼请求体。response_format 固定发完整的 json_schema，推理强度固定发 reasoning_effort。
     */
    Map<String, Object> buildBody(AiPlayer player, String systemPrompt, String userPrompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", player.model());
        body.put("temperature", TEMPERATURE);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));
        body.put("response_format", Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", MoveChoiceSchema.NAME,
                        "strict", true,
                        "schema", MoveChoiceSchema.asMap())));

        // effort 原样透传，不做枚举映射：官方是 minimal/low/medium/high，
        // 还会新增档位，写死反而挡了路。棋手没配就整个字段不带，用服务端默认值
        String effort = normalizedEffort(player);
        if (effort != null) {
            body.put("reasoning_effort", effort);
        }
        return body;
    }

    /**
     * 棋手配置的 effort，去掉首尾空白并转小写。没配时返回 null，调用方据此不带这个字段。
     */
    private static String normalizedEffort(AiPlayer player) {
        String effort = player.effort();
        if (effort == null || effort.isBlank()) {
            return null;
        }
        return effort.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 从 chat/completions 响应里取出第一条消息内容并解析。
     */
    private MoveChoice parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new LlmCallException("OpenAI 兼容接口返回内容为空");
        }
        JsonNode content;
        try {
            content = objectMapper.readTree(raw).path("choices").path(0).path("message").path("content");
        } catch (Exception e) {
            throw new LlmCallException("OpenAI 兼容接口响应无法解析：" + brief(raw), e);
        }
        if (content.isMissingNode() || content.isNull()) {
            throw new LlmCallException("OpenAI 兼容接口响应缺少 message.content：" + brief(raw));
        }
        return JsonMoveChoiceParser.parse(objectMapper, content.asString());
    }

    /**
     * 异常消息里只带一小段响应，完整内容看日志。
     */
    private static String brief(String raw) {
        if (raw == null) {
            return "<null>";
        }
        String text = raw.strip();
        return text.length() <= 300 ? text : text.substring(0, 300) + "…";
    }
}

package com.yaoyuquan.jungle.llm;

import com.yaoyuquan.jungle.config.AiPlayer;
import com.yaoyuquan.jungle.config.ProviderConfig;
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

        JsonHttpResponse response;
        try {
            response = http.post(PATH, requestJson);
        } catch (LlmCallException e) {
            payloadLogger.logFailure(e.getMessage(), e);
            throw new LlmCallException("调用 OpenAI 兼容接口失败：" + e.getMessage(), e);
        }
        payloadLogger.logResponse(response.status(), response.body());

        if (response.isError()) {
            throw new LlmCallException(
                    "OpenAI 兼容接口返回 HTTP " + response.status() + "：" + brief(response.body()));
        }
        return parse(response.body());
    }

    /**
     * 拼请求体。response_format 固定发完整的 json_schema，推理强度固定发 reasoning_effort。
     */
    Map<String, Object> buildBody(AiPlayer player, String systemPrompt, String userPrompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", player.model());
        body.put("temperature", player.temperature() == null ? 1.0 : player.temperature());
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

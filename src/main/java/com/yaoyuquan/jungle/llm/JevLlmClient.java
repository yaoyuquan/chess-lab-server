package com.yaoyuquan.jungle.llm;

import com.yaoyuquan.jungle.config.ProviderConfig;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 通过 TypeSafe 的 System One 接口调用 Jev 选着法。
 * <p>
 * Jev 与对话型模型的根本区别：它不生成文本，而是在给定的候选项里做一个带概率的判断。
 * 所以这里用 choice 原语，把候选着法摆成 criteria，模型直接返回选中的 key 与整个概率分布，
 * 天然不可能"编造"一个不存在的着法——对话型模型那套「返回越界编号就重试」在这条路上用不上。
 * <p>
 * 因为没有文本输出，对局记录里的理由由概率分布合成：置信度 + 次选。
 *
 * @author yaoyuquan
 */
public class JevLlmClient implements LlmClient {

    private static final String DEFAULT_BASE_URL = "https://api.typesafe.ai/v1";
    private static final String PATH = "/systemone";
    private static final String DEFAULT_MODEL = "jev-latest";

    /** 判断的 id，只在请求与响应之间对应，不发给模型 */
    private static final String QUESTION_ID = "best_move";

    private final ObjectMapper objectMapper;
    private final LlmPayloadLogger payloadLogger;
    private final JsonHttpClient http;

    public JevLlmClient(ProviderConfig config, ObjectMapper objectMapper, LlmPayloadLogger payloadLogger) {
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
        JevPrompt prompt = query.jev();
        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(buildBody(query));
        } catch (Exception e) {
            throw new LlmCallException("请求体序列化失败：" + e.getMessage(), e);
        }
        payloadLogger.logRequest(http.endpoint(PATH), http.headers(), requestJson);

        JsonHttpResponse response;
        try {
            response = http.post(PATH, requestJson);
        } catch (LlmCallException e) {
            payloadLogger.logFailure(e.getMessage(), e);
            throw new LlmCallException("调用 Jev 失败：" + e.getMessage(), e);
        }
        payloadLogger.logResponse(response.status(), response.body());

        if (response.isError()) {
            throw new LlmCallException("Jev 接口返回 HTTP " + response.status() + "：" + brief(response.body()));
        }
        return parse(response.body(), prompt.criteria());
    }

    /**
     * 拼 System One 请求体：一条 choice 判断，候选着法即 criteria。
     */
    Map<String, Object> buildBody(MoveQuery query) {
        JevPrompt prompt = query.jev();
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("type", "choice");
        question.put("instructions", prompt.instructions());
        question.put("criteria", prompt.criteria());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelOf(query));
        body.put("state", prompt.state());
        body.put("questions", Map.of(QUESTION_ID, question));
        return body;
    }

    /**
     * 棋手没配 model 时用 jev-latest。
     */
    private static String modelOf(MoveQuery query) {
        String model = query.player().model();
        return model == null || model.isBlank() ? DEFAULT_MODEL : model;
    }

    /**
     * 解析响应，把选中的 key 还原成候选着法编号，并用概率分布合成一句理由。
     */
    private MoveChoice parse(String raw, Map<String, String> criteria) {
        JsonNode answer;
        try {
            answer = objectMapper.readTree(raw).path("answers").path(QUESTION_ID);
        } catch (Exception e) {
            throw new LlmCallException("Jev 响应无法解析：" + brief(raw), e);
        }
        JsonNode choiceNode = answer.path("choice");
        if (choiceNode.isMissingNode() || choiceNode.isNull()) {
            throw new LlmCallException("Jev 响应缺少 answers." + QUESTION_ID + ".choice：" + brief(raw));
        }
        String key = choiceNode.asString();
        int index = JevPrompt.indexOf(key);
        if (index < 0) {
            throw new LlmCallException("Jev 返回了无法识别的候选项 " + key);
        }
        return new MoveChoice(index, describe(answer, key, criteria));
    }

    /**
     * Jev 不给文字理由，就用它给的概率分布说明这步棋有多笃定。
     */
    private static String describe(JsonNode answer, String chosenKey, Map<String, String> criteria) {
        StringBuilder sb = new StringBuilder("Jev 判断");
        JsonNode confidence = answer.path("confidence");
        if (confidence.isNumber()) {
            sb.append("，置信度 ").append(format(confidence.asDouble()));
        }
        JsonNode probabilities = answer.path("probabilities");
        if (probabilities.isObject()) {
            JsonNode chosen = probabilities.path(chosenKey);
            if (chosen.isNumber()) {
                sb.append("，本手概率 ").append(format(chosen.asDouble()));
            }
            runnerUp(probabilities, chosenKey).ifPresent(entry -> sb
                    .append("，次选 ")
                    .append(criteria.getOrDefault(entry.getKey(), entry.getKey()))
                    .append(' ')
                    .append(format(entry.getValue())));
        }
        return sb.toString();
    }

    /**
     * 概率第二高的候选项。
     */
    private static java.util.Optional<Map.Entry<String, Double>> runnerUp(JsonNode probabilities, String chosenKey) {
        Map<String, Double> others = new LinkedHashMap<>();
        probabilities.properties().forEach(entry -> {
            if (!entry.getKey().equals(chosenKey) && entry.getValue().isNumber()) {
                others.put(entry.getKey(), entry.getValue().asDouble());
            }
        });
        return others.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .max(Comparator.comparingDouble(Map.Entry::getValue));
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static String brief(String raw) {
        if (raw == null) {
            return "<null>";
        }
        String text = raw.strip();
        return text.length() <= 300 ? text : text.substring(0, 300) + "…";
    }
}

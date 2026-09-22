package com.yaoyuquan.jungle.llm;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.JsonOutputFormat;
import java.util.List;
import java.util.Map;

/**
 * 着法选择的 JSON Schema，两个服务商共用同一份定义。
 *
 * @author yaoyuquan
 */
public final class MoveChoiceSchema {

    public static final String NAME = "move_choice";

    private MoveChoiceSchema() {
    }

    /**
     * 纯 Java 结构的 schema，供 OpenAI 兼容服务的 response_format 使用。
     */
    public static Map<String, Object> asMap() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "index", Map.of(
                                "type", "integer",
                                "description", "选中的候选着法编号"),
                        "reason", Map.of(
                                "type", "string",
                                "description", "一句中文理由，不超过 40 字")),
                "required", List.of("index", "reason"),
                "additionalProperties", false);
    }

    /**
     * Anthropic SDK 形态的 schema。
     */
    public static JsonOutputFormat asAnthropicFormat() {
        JsonOutputFormat.Schema schema = JsonOutputFormat.Schema.builder()
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(Map.of(
                        "index", Map.of(
                                "type", "integer",
                                "description", "选中的候选着法编号"),
                        "reason", Map.of(
                                "type", "string",
                                "description", "一句中文理由，不超过 40 字"))))
                .putAdditionalProperty("required", JsonValue.from(List.of("index", "reason")))
                .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                .build();
        return JsonOutputFormat.builder().schema(schema).build();
    }
}

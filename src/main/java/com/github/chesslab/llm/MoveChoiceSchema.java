package com.github.chesslab.llm;

import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.JsonOutputFormat;
import java.util.List;
import java.util.Map;

/**
 * 着法选择的 JSON Schema，两个服务商共用同一份定义。
 * <p>
 * schema 只在 {@link #asMap()} 里写一遍，Anthropic 那份由它派生。
 * 两边各写一份的话，改了一处忘了另一处，两家的输出约束就会悄悄不一致，
 * 而这种漂移在单测里不一定看得出来。
 * <p>
 * 字段的 description 是发给模型看的，不是给人看的注释：它随 schema 一起进请求体，
 * 直接影响模型往里填什么。想改理由的长短，动的是这里，不是 PromptBuilder。
 *
 * @author yaoyuquan
 */
public final class MoveChoiceSchema {

    public static final String NAME = "move_choice";

    private MoveChoiceSchema() {
    }

    /**
     * 纯 Java 结构的 schema，也是这份定义的唯一出处。
     * <p>
     * required 列全所有字段、additionalProperties 关掉，既是业务约束，
     * 也是 OpenAI strict 模式的硬性要求，少一条直接报错。
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
     * Anthropic SDK 形态的 schema，逐个关键字从 {@link #asMap()} 搬过来。
     * <p>
     * SDK 没给 JSON Schema 的关键字建 typed 方法——schema 的内容本来就是任意的，建不出来——
     * 只留了 putAdditionalProperty 这个口子，所以只能一个关键字一次。
     * NAME 在这边用不上：Anthropic 的 JsonOutputFormat 没有名字这一项，只有 OpenAI 那边必填。
     */
    public static JsonOutputFormat asAnthropicFormat() {
        JsonOutputFormat.Schema.Builder schema = JsonOutputFormat.Schema.builder();
        asMap().forEach((key, value) -> schema.putAdditionalProperty(key, JsonValue.from(value)));
        return JsonOutputFormat.builder().schema(schema.build()).build();
    }
}

package com.yaoyuquan.jungle.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 模型返回文本解析的单元测试。
 *
 * @author yaoyuquan
 */
class JsonMoveChoiceParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("解析标准 JSON")
    void parsesPlainJson() {
        MoveChoice choice = JsonMoveChoiceParser.parse(objectMapper,
                "{\"index\": 3, \"reason\": \"先拿下狼\"}");
        assertThat(choice.index()).isEqualTo(3);
        assertThat(choice.reason()).isEqualTo("先拿下狼");
    }

    @Test
    @DisplayName("模型裹了 Markdown 代码块也能解析")
    void parsesFencedJson() {
        String raw = "好的，我的选择是：\n```json\n{\"index\": 5, \"reason\": \"冲巢\"}\n```\n";
        assertThat(JsonMoveChoiceParser.parse(objectMapper, raw).index()).isEqualTo(5);
    }

    @Test
    @DisplayName("缺少 reason 时按空字符串处理")
    void toleratesMissingReason() {
        assertThat(JsonMoveChoiceParser.parse(objectMapper, "{\"index\":0}").reason()).isEmpty();
    }

    @Test
    @DisplayName("缺少 index 字段时抛出异常以触发重试")
    void rejectsMissingIndex() {
        assertThatThrownBy(() -> JsonMoveChoiceParser.parse(objectMapper, "{\"reason\":\"忘了给编号\"}"))
                .isInstanceOf(LlmCallException.class)
                .hasMessageContaining("index");
    }

    @Test
    @DisplayName("完全不是 JSON 时抛出异常")
    void rejectsNonJson() {
        assertThatThrownBy(() -> JsonMoveChoiceParser.parse(objectMapper, "我觉得应该走豹"))
                .isInstanceOf(LlmCallException.class);
    }
}

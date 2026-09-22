package com.github.chess.llm.gpt;

import com.github.chess.config.AiPlayer;
import com.github.chess.config.ProviderConfig;
import com.github.chess.llm.LlmPayloadLogger;
import com.github.chess.llm.MoveChoiceSchema;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import tools.jackson.databind.ObjectMapper;

/**
 * OpenAI 兼容客户端的请求体构造测试。
 *
 * @author yaoyuquan
 */
class OpenAiCompatibleLlmClientTest {

    private static final AiPlayer PLAYER = new AiPlayer(
            "shentong", "深瞳", "SHENTONG",
            "gpt", "gpt-4o", "medium", 0.7);

    /**
     * 造一个客户端。带 key 才会真正建出 RestClient，但这里只调 buildBody 不发请求。
     */
    private static OpenAiCompatibleLlmClient client() {
        ProviderConfig config = new ProviderConfig("openai", "https://example.test/v1", "test-key");
        return new OpenAiCompatibleLlmClient(config, new ObjectMapper(), new LlmPayloadLogger("test"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> responseFormat(Map<String, Object> body) {
        return (Map<String, Object>) body.get("response_format");
    }

    @Test
    @DisplayName("response_format 固定发完整 schema 与 strict")
    void buildsJsonSchemaFormat() {
        Map<String, Object> format = responseFormat(client().buildBody(PLAYER, "sys", "user"));

        assertThat(format).containsEntry("type", "json_schema");
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) format.get("json_schema");
        assertThat(schema).containsEntry("name", MoveChoiceSchema.NAME);
        assertThat(schema).containsEntry("strict", true);
        assertThat(schema).containsKey("schema");
    }

    @Test
    @DisplayName("model 与 temperature 取自棋手配置，temperature 缺省为 1.0")
    void carriesPlayerSettings() {
        Map<String, Object> body = client().buildBody(PLAYER, "sys", "user");
        assertThat(body).containsEntry("model", "gpt-4o");
        assertThat(body).containsEntry("temperature", 0.7);

        AiPlayer noTemp = new AiPlayer("x", "X", "X", "gpt", "gpt-4o", null, null);
        assertThat(client().buildBody(noTemp, "sys", "user")).containsEntry("temperature", 1.0);
    }

    @Test
    @DisplayName("棋手的 effort 原样发成 reasoning_effort")
    void sendsReasoningEffort() {
        assertThat(client().buildBody(PLAYER, "sys", "user"))
                .containsEntry("reasoning_effort", "medium");
    }

    @Test
    @DisplayName("effort 的大小写与首尾空白都容忍")
    void normalizesEffort() {
        AiPlayer loud = new AiPlayer("x", "X", "X", "gpt", "gpt-4o", "  HIGH  ", 0.7);

        assertThat(client().buildBody(loud, "sys", "user")).containsEntry("reasoning_effort", "high");
    }

    @Test
    @DisplayName("棋手没配 effort 就整个字段不带，交给服务端默认值")
    void omitsReasoningEffortWhenBlank() {
        AiPlayer noEffort = new AiPlayer("x", "X", "X", "gpt", "gpt-4o", "  ", 0.7);

        assertThat(client().buildBody(noEffort, "sys", "user")).doesNotContainKey("reasoning_effort");
    }
}

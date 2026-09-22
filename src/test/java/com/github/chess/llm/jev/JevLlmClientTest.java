package com.github.chess.llm.jev;

import com.github.chess.config.AiPlayer;
import com.github.chess.config.ProviderConfig;
import com.github.chess.llm.ChatPrompt;
import com.github.chess.llm.LlmPayloadLogger;
import com.github.chess.llm.MoveQuery;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import tools.jackson.databind.ObjectMapper;

/**
 * Jev 客户端的请求体构造与候选项 key 约定测试。
 *
 * @author yaoyuquan
 */
class JevLlmClientTest {

    private static final AiPlayer PLAYER = new AiPlayer(
            "jianshi", "见识", "JEV-J",
            "jev", "jev-latest", null, null);

    private static JevLlmClient client() {
        ProviderConfig config = new ProviderConfig("jev", "https://example.test/v1", "test-key");
        return new JevLlmClient(config, new ObjectMapper(), new LlmPayloadLogger("test"));
    }

    private static MoveQuery query() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("我方阵营", "红方");
        state.put("棋盘", "<棋盘文本>");

        Map<String, Object> instructions = new LinkedHashMap<>();
        instructions.put("任务", "选出最好的一手");

        Map<String, String> criteria = new LinkedHashMap<>();
        criteria.put(JevPrompt.optionKey(0), "象 D5→D6");
        criteria.put(JevPrompt.optionKey(1), "象 D5→E5 吃狼");

        return new MoveQuery(PLAYER, new ChatPrompt("sys", "user"),
                new JevPrompt(state, instructions, criteria));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> question(Map<String, Object> body) {
        Map<String, Object> questions = (Map<String, Object>) body.get("questions");
        return (Map<String, Object>) questions.values().iterator().next();
    }

    @Test
    @DisplayName("请求体是 System One 的形状：model + state + questions")
    void buildsSystemOneBody() {
        Map<String, Object> body = client().buildBody(query());

        assertThat(body).containsKeys("model", "state", "questions");
        assertThat(body).containsEntry("model", "jev-latest");
        assertThat(body.get("state")).isInstanceOf(Map.class);
    }

    @Test
    @DisplayName("判断用 choice 原语，候选着法就是 criteria")
    void usesChoicePrimitive() {
        Map<String, Object> q = question(client().buildBody(query()));

        assertThat(q).containsEntry("type", "choice");
        assertThat(q).containsKey("instructions");
        @SuppressWarnings("unchecked")
        Map<String, String> criteria = (Map<String, String>) q.get("criteria");
        assertThat(criteria).containsExactly(
                Map.entry("move_0", "象 D5→D6"),
                Map.entry("move_1", "象 D5→E5 吃狼"));
    }

    @Test
    @DisplayName("棋手没配 model 时用 jev-latest")
    void defaultsModel() {
        AiPlayer bare = new AiPlayer("x", "X", "X", "jev", null, null, null);
        MoveQuery q = new MoveQuery(bare, query().chat(), query().jev());
        assertThat(client().buildBody(q)).containsEntry("model", "jev-latest");
    }

    @Test
    @DisplayName("候选项 key 与候选编号互相还原")
    void optionKeyRoundTrips() {
        assertThat(JevPrompt.optionKey(12)).isEqualTo("move_12");
        assertThat(JevPrompt.indexOf("move_12")).isEqualTo(12);
        assertThat(JevPrompt.indexOf("move_0")).isZero();
    }

    @Test
    @DisplayName("无法识别的候选项 key 返回 -1")
    void rejectsBadOptionKey() {
        assertThat(JevPrompt.indexOf("billing")).isEqualTo(-1);
        assertThat(JevPrompt.indexOf("move_abc")).isEqualTo(-1);
        assertThat(JevPrompt.indexOf(null)).isEqualTo(-1);
    }
}

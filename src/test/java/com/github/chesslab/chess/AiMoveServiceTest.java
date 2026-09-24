package com.github.chesslab.chess;

import com.github.chesslab.chess.web.dto.AiMoveRequest;
import com.github.chesslab.chess.web.dto.AiMoveResponse;
import com.github.chesslab.chess.web.dto.LegalMove;
import com.github.chesslab.config.AiPlayer;
import com.github.chesslab.config.AiProperties;
import com.github.chesslab.config.ProviderConfig;
import com.github.chesslab.config.UnknownPlayerException;
import com.github.chesslab.config.UnknownProviderException;
import com.github.chesslab.llm.LlmCallException;
import com.github.chesslab.llm.LlmClient;
import com.github.chesslab.llm.LlmClientRegistry;
import com.github.chesslab.llm.MoveChoice;
import com.github.chesslab.llm.MoveQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import tools.jackson.databind.ObjectMapper;

/**
 * 国际象棋 AI 着法决策服务的单元测试，用桩件替代真实大模型。
 *
 * @author yaoyuquan
 */
class AiMoveServiceTest {

    private static final AiPlayer PLAYER = new AiPlayer(
            "xuanji", "玄机", "XUANJI",
            "claude", "claude-opus-5", "xhigh");

    /**
     * 固定返回同一个结果的大模型桩件，并记下收到的提示词。
     */
    private static final class StubLlmClient implements LlmClient {

        private final Supplier<MoveChoice> answer;
        private final boolean available;
        private final List<MoveQuery> queries = new ArrayList<>();

        StubLlmClient(boolean available, Supplier<MoveChoice> answer) {
            this.available = available;
            this.answer = answer;
        }

        @Override
        public MoveChoice choose(MoveQuery query) {
            queries.add(query);
            return answer.get();
        }

        @Override
        public boolean isAvailable() {
            return available;
        }
    }

    private static AiProperties properties() {
        return new AiProperties(
                Map.of("claude", new ProviderConfig("anthropic", "", "test-key", false)),
                List.of(PLAYER));
    }

    private static AiMoveService service(LlmClient llmClient) {
        LlmClientRegistry registry = new LlmClientRegistry(properties(), new ObjectMapper()) {
            @Override
            public LlmClient clientFor(AiPlayer player) {
                return llmClient;
            }
        };
        return new AiMoveService(properties(), new PromptBuilder(), new BoardRenderer(),
                new FallbackPicker(), registry);
    }

    /**
     * 白方能用兵吃后，也能随手走一步王。
     */
    private static AiMoveRequest sampleRequest(String playerId) {
        return new AiMoveRequest(playerId, "w", "4k3/8/8/3q4/2P5/8/8/4K3 w - - 0 1",
                List.of("e4"),
                List.of(new LegalMove(0, "c4d5", "cxd5"), new LegalMove(1, "e1d2", "Kd2")));
    }

    @Test
    @DisplayName("模型返回合法编号时直接采用，提示词里带着局面与候选")
    void acceptsValidChoice() {
        StubLlmClient llm = new StubLlmClient(true, () -> new MoveChoice(1, "先让王出来"));
        AiMoveResponse response = service(llm).decide(sampleRequest("xuanji"));

        assertThat(response).isEqualTo(new AiMoveResponse(1, "先让王出来", false));
        assertThat(llm.queries).hasSize(1);
        assertThat(llm.queries.getFirst().chat().system()).contains("国际象棋");
        assertThat(llm.queries.getFirst().chat().user()).contains("[0] cxd5（c4d5）");
    }

    @Test
    @DisplayName("模型返回越界编号时不重问，直接兜底")
    void fallsBackOnOutOfRangeIndex() {
        StubLlmClient llm = new StubLlmClient(true, () -> new MoveChoice(99, "自创的一步"));
        AiMoveResponse response = service(llm).decide(sampleRequest("xuanji"));

        assertThat(llm.queries).hasSize(1);
        assertThat(response.fallback()).isTrue();
        assertThat(response.index()).isEqualTo(0);
    }

    @Test
    @DisplayName("调用失败后落到启发式兜底")
    void fallsBackOnCallFailure() {
        StubLlmClient llm = new StubLlmClient(true, () -> {
            throw new LlmCallException("连接被重置");
        });
        AiMoveResponse response = service(llm).decide(sampleRequest("xuanji"));

        assertThat(response.fallback()).isTrue();
        assertThat(response.reason()).contains("兜底");
        assertThat(response.index()).isEqualTo(0);
    }

    @Test
    @DisplayName("没有配置密钥时跳过大模型直接兜底")
    void skipsLlmWhenUnavailable() {
        StubLlmClient llm = new StubLlmClient(false, () -> new MoveChoice(1, "不该被调用"));
        AiMoveResponse response = service(llm).decide(sampleRequest("xuanji"));

        assertThat(llm.queries).isEmpty();
        assertThat(response.fallback()).isTrue();
        assertThat(response.reason()).contains("未配置模型密钥");
    }

    @Test
    @DisplayName("棋手引用了未定义的连接时兜底而不是中断对局")
    void fallsBackOnUnknownProvider() {
        LlmClientRegistry broken = new LlmClientRegistry(properties(), new ObjectMapper()) {
            @Override
            public LlmClient clientFor(AiPlayer player) {
                throw new UnknownProviderException(player.id(), "typo-provider");
            }
        };
        AiMoveService service = new AiMoveService(properties(), new PromptBuilder(),
                new BoardRenderer(), new FallbackPicker(), broken);

        AiMoveResponse response = service.decide(sampleRequest("xuanji"));
        assertThat(response.fallback()).isTrue();
        assertThat(response.reason()).contains("连接不可用");
    }

    @Test
    @DisplayName("棋手 id 不存在时抛出异常")
    void rejectsUnknownPlayer() {
        StubLlmClient llm = new StubLlmClient(true, () -> new MoveChoice(0, ""));
        assertThatThrownBy(() -> service(llm).decide(sampleRequest("nobody")))
                .isInstanceOf(UnknownPlayerException.class)
                .hasMessageContaining("nobody");
    }
}

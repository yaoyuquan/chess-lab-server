package com.github.chess.jungle;

import com.github.chess.config.AiPlayer;
import com.github.chess.config.AiProperties;
import com.github.chess.config.ProviderConfig;
import com.github.chess.config.UnknownPlayerException;
import com.github.chess.config.UnknownProviderException;
import com.github.chess.jungle.web.dto.AiMoveRequest;
import com.github.chess.jungle.web.dto.AiMoveResponse;
import com.github.chess.jungle.web.dto.BoardCell;
import com.github.chess.jungle.web.dto.LegalMove;
import com.github.chess.llm.LlmCallException;
import com.github.chess.llm.LlmClient;
import com.github.chess.llm.LlmClientRegistry;
import com.github.chess.llm.MoveChoice;
import com.github.chess.llm.MoveQuery;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import tools.jackson.databind.ObjectMapper;

/**
 * AI 着法决策服务的单元测试，用桩件替代真实大模型。
 *
 * @author yaoyuquan
 */
class AiMoveServiceTest {

    private static final AiPlayer PLAYER = new AiPlayer(
            "xuanji", "玄机", "XUANJI",
            "claude", "claude-opus-5", "xhigh");

    /**
     * 按脚本依次返回结果的大模型桩件。
     */
    private static final class StubLlmClient implements LlmClient {

        private final Deque<Supplier<MoveChoice>> script = new ArrayDeque<>();
        private final boolean available;
        private int calls;

        StubLlmClient(boolean available) {
            this.available = available;
        }

        StubLlmClient returning(MoveChoice choice) {
            script.add(() -> choice);
            return this;
        }

        StubLlmClient failing(String message) {
            script.add(() -> {
                throw new LlmCallException(message);
            });
            return this;
        }

        @Override
        public MoveChoice choose(MoveQuery query) {
            calls++;
            if (script.isEmpty()) {
                throw new LlmCallException("脚本已用尽");
            }
            return script.poll().get();
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

    /**
     * 固定返回同一个桩件的注册表。
     */
    private static LlmClientRegistry registryOf(LlmClient stub) {
        return new LlmClientRegistry(properties(), new ObjectMapper()) {
            @Override
            public LlmClient clientFor(AiPlayer player) {
                return stub;
            }
        };
    }

    private static AiMoveService service(LlmClient llmClient) {
        return new AiMoveService(properties(), new PromptBuilder(), new BoardRenderer(),
                new FallbackPicker(), registryOf(llmClient));
    }

    private static AiMoveRequest sampleRequest() {
        List<List<BoardCell>> board = TestBoards.empty();
        TestBoards.put(board, 4, 3, 5, "r");
        TestBoards.put(board, 4, 2, 3, "b");
        List<LegalMove> moves = List.of(
                TestBoards.move(0, 4, 3, 4, 2, "豹 D5→C5 吃狼"),
                TestBoards.move(1, 4, 3, 5, 3, "豹 D5→D4"));
        return TestBoards.request("xuanji", "r", board, moves);
    }

    @Test
    @DisplayName("模型返回合法编号时直接采用")
    void acceptsValidChoice() {
        StubLlmClient llm = new StubLlmClient(true).returning(new MoveChoice(1, "先稳住阵脚"));
        AiMoveResponse response = service(llm).decide(sampleRequest());

        assertThat(response.index()).isEqualTo(1);
        assertThat(response.reason()).isEqualTo("先稳住阵脚");
        assertThat(response.fallback()).isFalse();
        assertThat(llm.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("模型返回越界编号时不重问，直接兜底")
    void fallsBackOnOutOfRangeIndex() {
        StubLlmClient llm = new StubLlmClient(true)
                .returning(new MoveChoice(99, "自创的一步"))
                .returning(new MoveChoice(0, "吃掉狼"));
        AiMoveResponse response = service(llm).decide(sampleRequest());

        assertThat(llm.calls).isEqualTo(1);
        assertThat(response.fallback()).isTrue();
        assertThat(response.index()).isIn(0, 1);
    }

    @Test
    @DisplayName("调用失败后落到启发式兜底")
    void fallsBackOnCallFailure() {
        StubLlmClient llm = new StubLlmClient(true).failing("连接被重置");
        AiMoveResponse response = service(llm).decide(sampleRequest());

        assertThat(llm.calls).isEqualTo(1);
        assertThat(response.fallback()).isTrue();
        assertThat(response.index()).isIn(0, 1);
        assertThat(response.reason()).contains("兜底");
    }

    @Test
    @DisplayName("没有配置密钥时跳过大模型直接兜底")
    void skipsLlmWhenUnavailable() {
        StubLlmClient llm = new StubLlmClient(false);
        AiMoveResponse response = service(llm).decide(sampleRequest());

        assertThat(llm.calls).isZero();
        assertThat(response.fallback()).isTrue();
        assertThat(response.reason()).contains("未配置模型密钥");
        // 兜底应当选中吃狼那一步
        assertThat(response.index()).isEqualTo(0);
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

        AiMoveResponse response = service.decide(sampleRequest());
        assertThat(response.fallback()).isTrue();
        assertThat(response.reason()).contains("连接不可用");
        assertThat(response.index()).isIn(0, 1);
    }

    @Test
    @DisplayName("棋手 id 不存在时抛出异常")
    void rejectsUnknownPlayer() {
        AiMoveRequest request = TestBoards.request("nobody", "r", TestBoards.empty(),
                List.of(TestBoards.move(0, 4, 3, 4, 2, "豹 D5→C5")));
        assertThatThrownBy(() -> service(new StubLlmClient(true)).decide(request))
                .isInstanceOf(UnknownPlayerException.class)
                .hasMessageContaining("nobody");
    }
}

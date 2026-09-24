package com.github.chesslab.chess;

import com.github.chesslab.chess.web.dto.AiMoveRequest;
import com.github.chesslab.chess.web.dto.LegalMove;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 国际象棋提示词构造的单元测试。
 *
 * @author yaoyuquan
 */
class PromptBuilderTest {

    private final PromptBuilder builder = new PromptBuilder();
    private final BoardRenderer renderer = new BoardRenderer();

    @Test
    @DisplayName("系统提示词含人设、规则、思考节奏与输出约定")
    void systemPromptCarriesAllSections() {
        String prompt = builder.buildSystemPrompt();
        assertThat(prompt).contains("你是一位国际象棋棋手");
        assertThat(prompt).contains("不需要再核对合法性");
        assertThat(prompt).contains("# 表示走完将杀");
        assertThat(prompt).contains(PromptBuilder.QUIET_CHAR_BUDGET + " 字以内");
        assertThat(prompt).contains(PromptBuilder.CONTACT_CHAR_BUDGET + " 字以内");
        assertThat(prompt).contains("\"index\"");
    }

    @Test
    @DisplayName("用户提示词带执方、FEN、棋谱与编号后的候选着法")
    void userPromptCarriesPositionAndCandidates() {
        String fen = "rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2";
        AiMoveRequest request = new AiMoveRequest("xuanji", "b", fen,
                List.of("e4", "e5", "Nf3"),
                List.of(new LegalMove(0, "b8c6", "Nc6"), new LegalMove(3, "d7d6", "d6")));
        String prompt = builder.buildUserPrompt(request, renderer.render(ChessBoard.parse(fen)));

        assertThat(prompt).startsWith("你执黑方，对手是白方。");
        assertThat(prompt).contains("1. e4 e5 2. Nf3");
        assertThat(prompt).contains("FEN：" + fen);
        assertThat(prompt).contains("[0] Nc6（b8c6）");
        assertThat(prompt).contains("[3] d6（d7d6）");
        // 最高级说法没有终止条件，会让推理模型一直找下去
        assertThat(prompt).doesNotContain("最好的一手");
    }

    @Test
    @DisplayName("没有棋谱时不输出棋谱段落")
    void omitsEmptyHistory() {
        AiMoveRequest request = new AiMoveRequest("xuanji", "w", ChessBoardTest.START_FEN, null,
                List.of(new LegalMove(0, "e2e4", "e4")));
        String prompt = builder.buildUserPrompt(request, "");
        assertThat(prompt).doesNotContain("本局至今的着法");
    }

    @Test
    @DisplayName("棋谱按回合编号，奇数长度时最后一手是白方")
    void formatsHistory() {
        assertThat(PromptBuilder.formatHistory(List.of("d4", "d5", "c4")))
                .isEqualTo("1. d4 d5 2. c4");
    }
}

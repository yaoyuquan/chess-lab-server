package com.github.chess.jungle;

import com.github.chess.config.AiPlayer;
import com.github.chess.jungle.web.dto.AiMoveRequest;
import com.github.chess.jungle.web.dto.BoardCell;
import com.github.chess.jungle.web.dto.LegalMove;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 提示词构造的单元测试。
 *
 * @author yaoyuquan
 */
class PromptBuilderTest {

    private final PromptBuilder builder = new PromptBuilder();
    private final BoardRenderer renderer = new BoardRenderer();

    private static final AiPlayer PLAYER = new AiPlayer(
            "xuanji", "玄机", "XUANJI",
            "claude", "claude-opus-5", "xhigh");

    @Test
    @DisplayName("系统提示词含人设、规则与输出约定")
    void systemPromptCarriesPersonaAndRules() {
        String prompt = builder.buildSystemPrompt();
        assertThat(prompt).contains("盯住通往对方兽巢的那条线");
        assertThat(prompt).contains("鼠能吃象");
        assertThat(prompt).contains("\"index\"");
    }

    @Test
    @DisplayName("规则文本把水域、跳河落点、陷阱都枚举到格，不留给模型现推")
    void systemPromptEnumeratesBoardFacts() {
        String prompt = builder.buildSystemPrompt();
        assertThat(prompt).contains("B4 B5 B6 C4 C5 C6 E4 E5 E6 F4 F5 F6");
        assertThat(prompt).contains("A4↔D4");
        assertThat(prompt).contains("F3↔F7");
        assertThat(prompt).contains("红方陷阱 C9、D8、E9");
    }

    @Test
    @DisplayName("系统提示词带决策顺序与终止条件，用来压住推理模型的思考长度")
    void systemPromptCarriesDecisionProcedure() {
        String prompt = builder.buildSystemPrompt();
        assertThat(prompt).contains("一旦命中就立即返回该着法，不要再看其余候选");
        assertThat(prompt).contains("不要推演三步以上的计划");
    }

    @Test
    @DisplayName("决策顺序不进 Jev：判断型模型没有要压的思考长度")
    void jevPromptOmitsDecisionProcedure() {
        List<List<BoardCell>> board = TestBoards.empty();
        TestBoards.put(board, 4, 3, 5, "b");
        List<LegalMove> moves = List.of(TestBoards.move(0, 4, 3, 4, 2, "豹 D5→C5"));
        AiMoveRequest request = TestBoards.request("qingyun", "b", board, moves);

        String instructions = builder.buildJevPrompt(request, renderer.render(board))
                .instructions().toString();
        assertThat(instructions).contains("盯住通往对方兽巢的那条线");
        assertThat(instructions).doesNotContain("不要再看其余候选");
    }

    @Test
    @DisplayName("系统提示词含小写 json 字样，json_object 模式要求提示词里出现它")
    void systemPromptMentionsJsonKeyword() {
        assertThat(builder.buildSystemPrompt()).contains("json");
    }

    @Test
    @DisplayName("用户提示词列出全部候选编号并说明目标兽巢")
    void userPromptListsCandidates() {
        List<List<BoardCell>> board = TestBoards.empty();
        TestBoards.put(board, 4, 3, 5, "r");
        List<LegalMove> moves = List.of(
                TestBoards.move(0, 4, 3, 4, 2, "豹 D5→C5"),
                TestBoards.move(1, 4, 3, 5, 3, "豹 D5→D4"));
        AiMoveRequest request = TestBoards.request("xuanji", "r", board, moves);

        String prompt = builder.buildUserPrompt(request, renderer.render(board));
        assertThat(prompt).contains("你执红方");
        // 红方的目标是第 8 行的蓝巢，棋谱记作 D1
        assertThat(prompt).contains("蓝方的兽巢（D1）");
        assertThat(prompt).contains("[0] 豹 D5→C5");
        assertThat(prompt).contains("[1] 豹 D5→D4");
    }

    @Test
    @DisplayName("着法缺少中文描述时用坐标兜底")
    void userPromptFallsBackToCoordinates() {
        List<List<BoardCell>> board = TestBoards.empty();
        TestBoards.put(board, 4, 3, 5, "b");
        List<LegalMove> moves = List.of(TestBoards.move(0, 4, 3, 4, 2, null));
        AiMoveRequest request = TestBoards.request("qingyun", "b", board, moves);
        assertThat(builder.buildUserPrompt(request, renderer.render(board))).contains("[0] D5→C5");
    }
}

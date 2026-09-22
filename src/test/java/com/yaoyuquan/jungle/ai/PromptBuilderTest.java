package com.yaoyuquan.jungle.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.yaoyuquan.jungle.config.AiPlayer;
import com.yaoyuquan.jungle.web.dto.AiMoveRequest;
import com.yaoyuquan.jungle.web.dto.BoardCell;
import com.yaoyuquan.jungle.web.dto.LegalMove;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
            "claude", "claude-opus-5", "xhigh", 0.2);

    @Test
    @DisplayName("系统提示词含人设、规则与输出约定")
    void systemPromptCarriesPersonaAndRules() {
        String prompt = builder.buildSystemPrompt();
        assertThat(prompt).contains("盯住通往对方兽巢的那条线");
        assertThat(prompt).contains("鼠能吃象");
        assertThat(prompt).contains("\"index\"");
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

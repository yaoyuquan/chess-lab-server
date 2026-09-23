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
        assertThat(prompt).contains("你是一位斗兽棋棋手");
        assertThat(prompt).contains("鼠能吃象");
        assertThat(prompt).contains("\"index\"");
    }

    @Test
    @DisplayName("规则里的兽力顺序与棋盘渲染用的等级映射一致")
    void rankOrderMatchesPieceNames() {
        StringBuilder order = new StringBuilder("兽力由大到小：");
        for (int rank = 8; rank >= 1; rank--) {
            if (rank < 8) {
                order.append(" > ");
            }
            order.append(JungleBoard.nameOf(rank)).append(rank);
        }
        // 这里对不上时，模型会看到「狗吃狼」却读到狼比狗大，只能在思考里反复核对
        assertThat(builder.buildSystemPrompt()).contains(order);
    }

    @Test
    @DisplayName("规则文本把水域、跳河落点、陷阱都枚举到格，不留给模型现推")
    void systemPromptEnumeratesBoardFacts() {
        String prompt = builder.buildSystemPrompt();
        assertThat(prompt).contains("B4 B5 B6 C4 C5 C6 E4 E5 E6 F4 F5 F6");
        assertThat(prompt).contains("A4↔D4");
        assertThat(prompt).contains("F3↔F7");
        assertThat(prompt).contains("红方陷阱 C9、D8、E9");
        // 「失去全部威势」会被读成「也不能吃子」，与前端 canCapture 不符
        assertThat(prompt).contains("它自己吃子不受影响");
        assertThat(prompt).doesNotContain("失去全部威势");
    }

    @Test
    @DisplayName("系统提示词按平静 / 接触分开给思考的宽度、深度与字数上限")
    void systemPromptCarriesThinkingPace() {
        String prompt = builder.buildSystemPrompt();
        assertThat(prompt).contains("凭棋感直接选一手，不推演变化，思考控制在 "
                + PromptBuilder.QUIET_CHAR_BUDGET + " 字以内");
        assertThat(prompt).contains("圈出最有希望的两手，每一手都要沿一条主变推三步");
        assertThat(prompt).contains("不展开分支");
        assertThat(prompt).contains("思考控制在 " + PromptBuilder.CONTACT_CHAR_BUDGET + " 字以内");
    }

    @Test
    @DisplayName("逼近兽巢也算接触，找对方应手时先看对方能不能一步进我的巢")
    void systemPromptWatchesOwnDen() {
        String prompt = builder.buildSystemPrompt();
        // 只按棋子相邻判接触时，对方的子沿一列走进兽巢，模型每一手都说「双方未接触」
        assertThat(prompt).contains("或者任何一方有棋子离对方兽巢三步以内");
        assertThat(prompt).contains("先看对方有没有棋子能一步走进我的兽巢");
    }

    @Test
    @DisplayName("没有好解时让模型停手，不在不存在的解里打转")
    void systemPromptStopsWhenNothingSaves() {
        // 实战里对方已贴着兽巢、谁都够不着它，模型为这一手想了 200 多秒
        assertThat(builder.buildSystemPrompt()).contains("这一手就是没有好解，\n不要继续找");
    }

    @Test
    @DisplayName("接触局面要求逐个检查落点四周的对方棋子，这是实测里保住棋力的那一句")
    void systemPromptAsksToScanNeighbours() {
        // 没有这句时，模型知道要找对方应手，却会漏看贴身的鼠，把象送进鼠口
        assertThat(builder.buildSystemPrompt())
                .contains("把落点四周的对方棋子逐个过一遍，并对照规则里的例外（鼠吃象、狮虎跳河）");
    }

    @Test
    @DisplayName("提示词只给思考节奏不给棋理，不出现着法优先级清单")
    void systemPromptCarriesNoStrategyChecklist() {
        String prompt = builder.buildSystemPrompt();
        // 这几句是上一版的棋理清单与人设里的棋理提示。它们能让模型想得快，
        // 但下出来的是我们的棋而不是模型的棋，谁把它们加回来，这条会失败
        assertThat(prompt).doesNotContain("一旦命中就立即返回");
        assertThat(prompt).doesNotContain("白吃");
        assertThat(prompt).doesNotContain("占住中路");
        assertThat(prompt).doesNotContain("不做亏损的换子");
    }

    @Test
    @DisplayName("思考节奏不进 Jev：判断型模型没有要压的思考长度")
    void jevPromptOmitsThinkingPace() {
        List<List<BoardCell>> board = TestBoards.empty();
        TestBoards.put(board, 4, 3, 5, "b");
        List<LegalMove> moves = List.of(TestBoards.move(0, 4, 3, 4, 2, "豹 D5→C5"));
        AiMoveRequest request = TestBoards.request("qingyun", "b", board, moves);

        String instructions = builder.buildJevPrompt(request, renderer.render(board))
                .instructions().toString();
        assertThat(instructions).contains("你是一位斗兽棋棋手");
        assertThat(instructions).doesNotContain("沿一条主变推三步");
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
        // 自己的巢也要点明，否则模型从不防守，任由对方的子一路走进去
        assertThat(prompt).contains("你的兽巢是 D9：对方任何棋子走进 D9，你立即输棋。");
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

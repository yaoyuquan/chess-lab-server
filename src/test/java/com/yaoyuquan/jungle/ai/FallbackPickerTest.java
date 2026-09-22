package com.yaoyuquan.jungle.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.yaoyuquan.jungle.web.dto.AiMoveRequest;
import com.yaoyuquan.jungle.web.dto.BoardCell;
import com.yaoyuquan.jungle.web.dto.LegalMove;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 启发式兜底选择器的单元测试。
 *
 * @author yaoyuquan
 */
class FallbackPickerTest {

    private final FallbackPicker picker = new FallbackPicker();

    @Test
    @DisplayName("能入对方兽巢时优先入巢")
    void picksDenEntry() {
        List<List<BoardCell>> board = TestBoards.empty();
        TestBoards.put(board, 7, 3, 3, "r");
        TestBoards.put(board, 5, 0, 8, "b");
        List<LegalMove> moves = List.of(
                TestBoards.move(0, 7, 3, 7, 2, "狼 D2→C2"),
                TestBoards.move(1, 7, 3, 8, 3, "狼 D2→D1 入巢"),
                TestBoards.move(2, 7, 3, 7, 4, "狼 D2→E2"));
        AiMoveRequest request = TestBoards.request("xuanji", "r", board, moves);
        assertThat(picker.pick(request).i()).isEqualTo(1);
    }

    @Test
    @DisplayName("没有入巢机会时优先吃价值最高的子")
    void picksHighestValueCapture() {
        List<List<BoardCell>> board = TestBoards.empty();
        TestBoards.put(board, 4, 3, 8, "r");
        TestBoards.put(board, 4, 2, 2, "b");
        TestBoards.put(board, 3, 3, 7, "b");
        List<LegalMove> moves = List.of(
                TestBoards.move(0, 4, 3, 4, 2, "象 D5→C5 吃猫"),
                TestBoards.move(1, 4, 3, 3, 3, "象 D5→D6 吃狮"));
        AiMoveRequest request = TestBoards.request("xuanji", "r", board, moves);
        assertThat(picker.pick(request).i()).isEqualTo(1);
    }

    @Test
    @DisplayName("无子可吃时朝对方兽巢推进")
    void advancesTowardsDen() {
        List<List<BoardCell>> board = TestBoards.empty();
        TestBoards.put(board, 4, 3, 5, "r");
        List<LegalMove> moves = List.of(
                TestBoards.move(0, 4, 3, 3, 3, "豹 D5→D6"),
                TestBoards.move(1, 4, 3, 5, 3, "豹 D5→D4"));
        AiMoveRequest request = TestBoards.request("xuanji", "r", board, moves);
        // 红方要打进第 8 行的蓝巢，所以行号变大的那一步更好
        assertThat(picker.pick(request).i()).isEqualTo(1);
    }

    @Test
    @DisplayName("选出的着法一定来自候选列表")
    void alwaysReturnsCandidate() {
        List<List<BoardCell>> board = TestBoards.empty();
        TestBoards.put(board, 4, 3, 5, "b");
        List<LegalMove> moves = List.of(
                TestBoards.move(7, 4, 3, 4, 2, "豹 D5→C5"),
                TestBoards.move(9, 4, 3, 4, 4, "豹 D5→E5"));
        AiMoveRequest request = TestBoards.request("qingyun", "b", board, moves);
        assertThat(picker.pick(request).i()).isIn(7, 9);
    }
}

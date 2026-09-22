package com.yaoyuquan.jungle.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.yaoyuquan.jungle.web.dto.BoardCell;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 棋盘文本渲染的单元测试。
 *
 * @author yaoyuquan
 */
class BoardRendererTest {

    private final BoardRenderer renderer = new BoardRenderer();

    @Test
    @DisplayName("渲染结果含行列号、地形标记与双方子力清单")
    void rendersGridAndPieceList() {
        List<List<BoardCell>> board = TestBoards.empty();
        TestBoards.put(board, 0, 0, 7, "r");
        TestBoards.put(board, 8, 6, 7, "b");
        String text = renderer.render(board);

        assertThat(text).contains("A").contains("G");
        assertThat(text).contains("红巢").contains("蓝巢").contains("红陷").contains("蓝陷");
        assertThat(text).contains("～水");
        assertThat(text).contains("红方子力：狮 A9");
        assertThat(text).contains("蓝方子力：狮 G1");
    }

    @Test
    @DisplayName("水中的鼠与踩进对方陷阱的子会被标注出来")
    void annotatesWaterAndTrap() {
        List<List<BoardCell>> board = TestBoards.empty();
        TestBoards.put(board, 4, 1, 1, "b");
        TestBoards.put(board, 0, 2, 8, "b");
        String text = renderer.render(board);

        assertThat(text).contains("鼠 B5（在水中）");
        assertThat(text).contains("象 C9（踩在对方陷阱里，可被任意棋子吃掉）");
    }

    @Test
    @DisplayName("一方无子时标注已被全歼")
    void marksWipedOutSide() {
        List<List<BoardCell>> board = TestBoards.empty();
        TestBoards.put(board, 4, 3, 5, "r");
        assertThat(renderer.render(board)).contains("蓝方子力：已被全歼");
    }
}

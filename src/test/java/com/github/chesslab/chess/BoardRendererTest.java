package com.github.chesslab.chess;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 国际象棋局面渲染的单元测试。
 *
 * @author yaoyuquan
 */
class BoardRendererTest {

    private final BoardRenderer renderer = new BoardRenderer();

    @Test
    @DisplayName("网格白方在下，第 8 横线在最上面")
    void rendersGridWhiteAtBottom() {
        String text = renderer.render(ChessBoard.parse(ChessBoardTest.START_FEN));
        assertThat(text).contains("8  r n b q k b n r");
        assertThat(text).contains("1  R N B Q K B N R");
        assertThat(text.indexOf("8  r")).isLessThan(text.indexOf("1  R"));
        assertThat(text).contains("   a b c d e f g h");
    }

    @Test
    @DisplayName("子力清单王排第一，并给出不含王的子力合计")
    void listsPiecesWithKingFirst() {
        String text = renderer.render(ChessBoard.parse("4k3/8/8/8/8/8/4P3/R3K3 w Q - 0 1"));
        assertThat(text).contains("白方子力：王 e1、车 a1、兵 e2（子力合计 600）");
        assertThat(text).contains("黑方子力：王 e8（子力合计 0）");
    }

    @Test
    @DisplayName("易位权、过路兵格、五十步计数翻成中文")
    void rendersStateLine() {
        String text = renderer.render(ChessBoard.parse(
                "rnbqkbnr/ppp1pppp/8/3pP3/8/8/PPPP1PPP/RNBQKBNR w Kq d6 0 3"));
        assertThat(text).contains("轮到白方走");
        assertThat(text).contains("白方短易位、黑方长易位");
        assertThat(text).contains("吃过路兵落到 d6");
        assertThat(text).contains("已连续 0 个半回合");
    }

    @Test
    @DisplayName("没有易位权时明确写出双方都已不能易位")
    void rendersNoCastling() {
        String text = renderer.render(ChessBoard.parse("4k3/8/8/8/8/8/8/4K3 b - - 12 40"));
        assertThat(text).contains("双方都已不能易位");
        assertThat(text).doesNotContain("吃过路兵");
    }
}

package com.github.chesslab.chess;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * FEN 解析的单元测试。
 *
 * @author yaoyuquan
 */
class ChessBoardTest {

    static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    @Test
    @DisplayName("初始局面的棋子落在正确的格子上")
    void parsesStartPosition() {
        ChessBoard board = ChessBoard.parse(START_FEN);
        assertThat(board.at("e1")).isEqualTo('K');
        assertThat(board.at("d8")).isEqualTo('q');
        assertThat(board.at("a2")).isEqualTo('P');
        assertThat(board.at("e4")).isEqualTo((char) 0);
        assertThat(board.activeColor()).isEqualTo("w");
        assertThat(board.castling()).isEqualTo("KQkq");
        assertThat(board.halfmoveClock()).isEqualTo("0");
    }

    @Test
    @DisplayName("代数坐标与行列互转一致")
    void convertsCoordinates() {
        assertThat(ChessBoard.toRowCol("a8")).containsExactly(0, 0);
        assertThat(ChessBoard.toRowCol("h1")).containsExactly(7, 7);
        assertThat(ChessBoard.toSquare(4, 4)).isEqualTo("e4");
        assertThat(ChessBoard.toRowCol("z9")).isNull();
    }

    @Test
    @DisplayName("FEN 写坏时不抛异常，看不懂的部分当空")
    void toleratesMalformedFen() {
        ChessBoard board = ChessBoard.parse("8/8/XX/99999/k7");
        assertThat(board.at("a4")).isEqualTo('k');
        assertThat(board.activeColor()).isEqualTo("w");
        assertThat(board.castling()).isEqualTo("-");
        assertThat(board.halfmoveClock()).isNull();
    }
}

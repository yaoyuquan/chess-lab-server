package com.github.chesslab.chess;

import com.github.chesslab.chess.web.dto.AiMoveRequest;
import com.github.chesslab.chess.web.dto.LegalMove;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 国际象棋启发式兜底选择器的单元测试。
 *
 * @author yaoyuquan
 */
class FallbackPickerTest {

    private final FallbackPicker picker = new FallbackPicker();

    private static AiMoveRequest request(String side, String fen, LegalMove... moves) {
        return new AiMoveRequest("xuanji", side, fen, null, List.of(moves));
    }

    @Test
    @DisplayName("有将杀时直接将杀，哪怕另一手能吃后")
    void picksMate() {
        AiMoveRequest request = request("w", "6k1/5ppp/8/8/8/8/q7/R3K3 w - - 0 1",
                new LegalMove(0, "a1a2", "Rxa2"),
                new LegalMove(1, "a1a8", "Ra8#"));
        assertThat(picker.pick(request).i()).isEqualTo(1);
    }

    @Test
    @DisplayName("没有将杀时先吃价值最高的子")
    void picksHighestValueCapture() {
        AiMoveRequest request = request("w", "4k3/8/8/3q4/2P1n3/8/8/4K3 w - - 0 1",
                new LegalMove(0, "c4d5", "cxd5"),
                new LegalMove(1, "c4c5", "c5"),
                new LegalMove(2, "e1d2", "Kd2"));
        assertThat(picker.pick(request).i()).isEqualTo(0);
    }

    @Test
    @DisplayName("吃同一个子时用价值小的子去吃")
    void prefersCheaperAttacker() {
        AiMoveRequest request = request("w", "4k3/8/8/3r4/2P5/8/8/3QK3 w - - 0 1",
                new LegalMove(0, "d1d5", "Qxd5"),
                new LegalMove(1, "c4d5", "cxd5"));
        assertThat(picker.pick(request).i()).isEqualTo(1);
    }

    @Test
    @DisplayName("落点为空但标了吃子的按吃过路兵计分")
    void scoresEnPassantAsCapture() {
        AiMoveRequest request = request("w", "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1",
                new LegalMove(0, "e5d6", "exd6"),
                new LegalMove(1, "e5e6", "e6"));
        assertThat(picker.pick(request).i()).isEqualTo(0);
    }

    @Test
    @DisplayName("升变优先于普通吃子")
    void prefersPromotion() {
        AiMoveRequest request = request("w", "4k3/1P6/8/8/8/8/5n2/4K3 w - - 0 1",
                new LegalMove(0, "b7b8q", "b8=Q+"),
                new LegalMove(1, "e1f2", "Kxf2"));
        assertThat(picker.pick(request).i()).isEqualTo(0);
    }

    @Test
    @DisplayName("平静局面优先易位，不乱走王")
    void prefersCastlingOverKingWalk() {
        AiMoveRequest request = request("w", "4k3/8/8/8/8/8/8/4K2R w K - 0 1",
                new LegalMove(0, "e1f1", "Kf1"),
                new LegalMove(1, "e1g1", "O-O"));
        assertThat(picker.pick(request).i()).isEqualTo(1);
    }

    @Test
    @DisplayName("选出的着法一定来自候选列表")
    void alwaysReturnsCandidate() {
        AiMoveRequest request = request("b", ChessBoardTest.START_FEN,
                new LegalMove(7, "b8c6", "Nc6"),
                new LegalMove(9, "g8f6", "Nf6"));
        assertThat(picker.pick(request).i()).isIn(7, 9);
    }
}

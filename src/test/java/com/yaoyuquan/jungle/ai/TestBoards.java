package com.yaoyuquan.jungle.ai;

import com.yaoyuquan.jungle.web.dto.AiMoveRequest;
import com.yaoyuquan.jungle.web.dto.BoardCell;
import com.yaoyuquan.jungle.web.dto.LegalMove;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 测试用的棋盘与请求构造工具。
 *
 * @author yaoyuquan
 */
public final class TestBoards {

    private TestBoards() {
    }

    /**
     * 造一张全空的 9×7 棋盘。
     */
    public static List<List<BoardCell>> empty() {
        List<List<BoardCell>> board = new ArrayList<>();
        for (int r = 0; r < JungleBoard.ROWS; r++) {
            board.add(new ArrayList<>(Arrays.asList(new BoardCell[JungleBoard.COLS])));
        }
        return board;
    }

    /**
     * 在 (r,c) 放一枚棋子。
     */
    public static void put(List<List<BoardCell>> board, int r, int c, int rank, String side) {
        board.get(r).set(c, new BoardCell(rank, side));
    }

    /**
     * 构造一条候选着法。
     */
    public static LegalMove move(int i, int fr, int fc, int tr, int tc, String text) {
        return new LegalMove(i, List.of(fr, fc), List.of(tr, tc), text);
    }

    /**
     * 构造一个 AI 着法请求。
     */
    public static AiMoveRequest request(String playerId, String side,
                                        List<List<BoardCell>> board, List<LegalMove> moves) {
        return new AiMoveRequest(playerId, side, board, 1, moves);
    }
}

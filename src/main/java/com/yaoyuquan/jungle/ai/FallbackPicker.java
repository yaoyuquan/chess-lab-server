package com.yaoyuquan.jungle.ai;

import com.yaoyuquan.jungle.web.dto.AiMoveRequest;
import com.yaoyuquan.jungle.web.dto.BoardCell;
import com.yaoyuquan.jungle.web.dto.LegalMove;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/**
 * 启发式兜底着法选择器。
 * <p>
 * 大模型不可用或连续返回非法编号时由它接管，保证对局永远不会卡死。
 * 打分优先级：入巢 &gt; 吃高价值子 &gt; 向对方兽巢推进。
 *
 * @author yaoyuquan
 */
@Component
public class FallbackPicker {

    private static final int DEN_SCORE = 1_000_000;
    private static final int CAPTURE_BASE = 1_000;
    private static final int ADVANCE_WEIGHT = 5;

    /**
     * 从合法着法中挑一条。请求保证 legalMoves 非空。
     */
    public LegalMove pick(AiMoveRequest request) {
        List<LegalMove> moves = request.legalMoves();
        LegalMove best = moves.get(0);
        int bestScore = Integer.MIN_VALUE;
        for (LegalMove move : moves) {
            int score = score(request, move);
            if (score > bestScore) {
                bestScore = score;
                best = move;
            }
        }
        return best;
    }

    /**
     * 给一条着法打分。同分时靠随机微扰打散，避免兜底棋路一成不变。
     */
    private int score(AiMoveRequest request, LegalMove move) {
        int toRow = move.to().get(0);
        int toCol = move.to().get(1);
        String side = request.side();
        int score = ThreadLocalRandom.current().nextInt(ADVANCE_WEIGHT);

        // 能直接进对方兽巢就不必再想别的
        String den = JungleBoard.denOf(toRow, toCol);
        if (den != null && !den.equals(side)) {
            return DEN_SCORE;
        }

        BoardCell target = JungleBoard.at(request.board(), toRow, toCol);
        if (target != null && !side.equals(target.side())) {
            score += CAPTURE_BASE + JungleBoard.valueOf(target.rank());
        }

        // 离对方兽巢越近越好
        int denRow = JungleBoard.targetDenRow(side);
        int distance = Math.abs(toRow - denRow) + Math.abs(toCol - 3);
        score += (16 - distance) * ADVANCE_WEIGHT;

        // 别主动踩进对方陷阱
        String trap = JungleBoard.trapOf(toRow, toCol);
        if (trap != null && !trap.equals(side)) {
            score -= 40;
        }
        return score;
    }
}

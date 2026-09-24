package com.github.chesslab.chess;

import com.github.chesslab.chess.web.dto.AiMoveRequest;
import com.github.chesslab.chess.web.dto.LegalMove;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/**
 * 国际象棋的启发式兜底着法选择器。
 * <p>
 * 打分优先级：将杀 &gt; 升变 &gt; 吃子 &gt; 将军 &gt; 易位 &gt; 往中心走。
 * <p>
 * 将杀、将军、吃子都直接读 SAN 里的 #、+、x 标记，不自己判——那是前端规则引擎算好的结论，
 * 后端再算一遍就是第二份规则实现。同理这里不看「吃完会不会被吃回」，那要算攻击关系，
 * 等于把走法生成搬过来；吃子只按 MVV-LVA 排序（先吃大的，同样吃用小子吃），
 * 用小子吃大子本身就降低了被吃回亏本的概率。兜底只求不送、不卡，不求下得好。
 * <p>
 * bean 名要显式写：斗兽棋也有一个 FallbackPicker，默认 bean 名会撞。
 *
 * @author yaoyuquan
 */
@Component("chessFallbackPicker")
public class FallbackPicker {

    private static final int MATE_SCORE = 1_000_000;
    private static final int PROMOTION_BASE = 20_000;
    private static final int CAPTURE_BASE = 10_000;
    private static final int CHECK_SCORE = 300;
    private static final int CASTLE_SCORE = 200;
    private static final int KING_WALK_PENALTY = 100;
    private static final int CENTER_WEIGHT = 5;

    /**
     * 从合法着法中挑一条。请求保证 legalMoves 非空。
     */
    public LegalMove pick(AiMoveRequest request) {
        ChessBoard board = ChessBoard.parse(request.fen());
        List<LegalMove> moves = request.legalMoves();
        LegalMove best = moves.get(0);
        int bestScore = Integer.MIN_VALUE;
        for (LegalMove move : moves) {
            int score = score(board, move);
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
    private int score(ChessBoard board, LegalMove move) {
        String san = move.san();
        String uci = move.uci();
        if (san.contains("#")) {
            return MATE_SCORE;
        }

        int score = ThreadLocalRandom.current().nextInt(CENTER_WEIGHT);
        String from = uci.length() >= 2 ? uci.substring(0, 2) : "";
        String to = uci.length() >= 4 ? uci.substring(2, 4) : "";
        char mover = board.at(from);

        // UCI 第五位是升变子，e7e8q
        if (uci.length() >= 5) {
            score += PROMOTION_BASE + ChessBoard.valueOf(uci.charAt(4));
        }

        if (san.contains("x")) {
            char victim = board.at(to);
            // 落点是空格却标了吃子，只能是吃过路兵
            int victimValue = victim == 0 ? ChessBoard.valueOf('p') : ChessBoard.valueOf(victim);
            score += CAPTURE_BASE + victimValue * 10 - ChessBoard.valueOf(mover);
        }

        if (san.contains("+")) {
            score += CHECK_SCORE;
        }

        if (san.startsWith("O-O")) {
            score += CASTLE_SCORE;
        } else if (Character.toLowerCase(mover) == 'k') {
            // 没吃子、没易位的王步大多是在给自己拆掉掩护
            score -= KING_WALK_PENALTY;
        }

        int[] rc = ChessBoard.toRowCol(to);
        if (rc != null) {
            // 行、列都以 3.5 为中心，距离取两倍避开小数，最大 14
            int distance = Math.abs(2 * rc[0] - 7) + Math.abs(2 * rc[1] - 7);
            score += (14 - distance) * CENTER_WEIGHT;
        }
        return score;
    }
}

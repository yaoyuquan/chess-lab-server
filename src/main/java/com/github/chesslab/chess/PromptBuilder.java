package com.github.chesslab.chess;

import com.github.chesslab.chess.web.dto.AiMoveRequest;
import com.github.chesslab.chess.web.dto.LegalMove;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 构造发给大模型的国际象棋提示词。
 * <p>
 * 结构照搬斗兽棋那一份：系统提示词固定为人设、规则、思考节奏、输出约定，每一手完全相同，
 * 便于命中提示词缓存；用户提示词只携带当轮局面与编号后的候选着法。
 * <p>
 * 斗兽棋那份类注释里记下的教训这里同样成立，只是落点不同：
 * 模型对国际象棋规则本来就熟，不用像斗兽棋那样把水域、跳河逐格列出来；
 * 它真正会反复现推的是「这步合不合法」——被牵制的子能不能动、王走过去是不是自投将军。
 * 候选列表已经是前端算好的全部合法着法，所以规则里把这一点写死，省掉整类核对。
 * <p>
 * 同样不越的线：提示词只给事实和思考节奏，不给棋理（开局原则、子力交换准则之类），
 * 否则下出来的是我们的棋，不是模型的棋。
 * <p>
 * bean 名要显式写：斗兽棋也有一个 PromptBuilder，默认 bean 名会撞。
 *
 * @author yaoyuquan
 */
@Component("chessPromptBuilder")
public class PromptBuilder {

    /**
     * 规则要点。只写模型容易拿不准、或者会在上面白花思考的几条，不复述完整规则。
     * <p>
     * SAN 标记那一条是给候选列表用的：将军、将杀、吃子前端都已经算好并标在 SAN 上，
     * 让模型直接读标记，别再从坐标推一遍。
     */
    private static final String RULES = """
            规则要点：
            1. 按国际象棋标准规则。候选着法由规则引擎生成，已经是当前局面下全部合法着法：
               牵制、自投将军、易位条件、吃过路兵、升变都已经处理好。
               不在列表里的着法就是不合法，列表里的着法都合法，不需要再核对合法性。
            2. 候选着法用 SAN 记法书写，标记含义已由规则引擎算好，直接采信：
               x 表示吃子，+ 表示走完将军，# 表示走完将杀（直接获胜），=Q 这类表示升变成什么子，
               O-O 是短易位，O-O-O 是长易位。
            3. 和棋：逼和（无子可动且未被将军）、三次重复局面、连续 50 回合双方都没有吃子也没有动兵、
               双方子力都不足以将杀。
            """;

    /**
     * 棋手人设。只交代身份和任务，怎么下留给模型。
     */
    private static final String PERSONA = """
            你是一位国际象棋棋手，正在下一盘快棋，轮到你从给出的候选着法里选一手。""";

    /**
     * 局面平静时的思考字数上限，写进 {@link #THINKING_PACE}。
     */
    static final int QUIET_CHAR_BUDGET = 200;

    /**
     * 局面有战术接触时的思考字数上限，写进 {@link #THINKING_PACE}。
     * <p>
     * 这两个数直接沿用斗兽棋实测挑出来的值，还没拿国际象棋局面单独校过。
     * 国际象棋的候选数通常是斗兽棋的两三倍，嫌慢往下调，嫌下得臭往上调。
     */
    static final int CONTACT_CHAR_BUDGET = 600;

    /**
     * 思考节奏。
     * <p>
     * 沿用斗兽棋那一版的骨架：先判断局面平静还是接触，平静凭棋感直接走，
     * 接触沿一条主变推三步，不展开分支、不找完整杀法、选定不推翻。
     * 这套骨架对着的是推理模型「选出最好的一手」没有终止条件的问题，与棋种无关。
     * <p>
     * 「接触」的判定换成了国际象棋的说法。斗兽棋那边吃过一次亏：接触只按棋子相邻来判，
     * 逼近兽巢的子贴不着谁，于是守巢检查整段被跳过。国际象棋里对应的是王的安危，
     * 所以把「任何一方的王受到直接威胁」单列进接触条件，找应手时先看对方有没有将军、将杀。
     */
    private static final String THINKING_PACE = """
            这是快棋，每一手的思考时间有限。先看一眼局面有没有接触，再按对应的方式想。
            接触指：候选里有吃子或将军、我方有子正被对方攻击、或者任何一方的王受到直接威胁。
            - 还没有接触：凭棋感直接选一手，不推演变化，思考控制在 %d 字以内。
            - 已经接触：凭棋感圈出最有希望的两手，每一手都要沿一条主变推三步：
              我走这手 → 对方最强的应手 → 我的下一手。每一步只取最可能的那一着，不展开分支。
              找对方最强的应手时，先看对方有没有将军或将杀，
              再看落点有没有被对方的兵、马、象、车、后、王攻击，逐个过一遍。
              推完三步还看不清的，就按已经看到的判断，不再加深；不要去找完整的取胜路线。
              思考控制在 %d 字以内。
            如果看出怎么走都挡不住对方下一手将杀，或者怎么走都要丢子，这一手就是没有好解，
            不要继续找，直接从已经看过的着法里选一手。
            推演时用 SAN 简写记着法，不写整句。
            选定之后不再推翻重算；拿不准时取第一眼更看好的那手。
            规则以上面写的为准，不需要再核对或重新推导。""".formatted(QUIET_CHAR_BUDGET, CONTACT_CHAR_BUDGET);

    /**
     * 输出约定，与斗兽棋一致。
     */
    private static final String OUTPUT_CONTRACT = """
            你必须从给出的候选着法编号中选择恰好一个，并以 json 返回：
            {"index": <候选着法的编号>, "reason": "<一句中文理由，不超过 40 字>"}
            index 必须是候选列表中真实存在的编号，不得自创着法或返回列表之外的数字。
            只输出这一个 json 对象，不要附加任何解释，不要复述分析过程，也不要套代码块围栏。
            """;

    /**
     * 系统提示词：棋手人设 + 规则 + 思考节奏 + 输出约定。每手不变，可被提示词缓存复用。
     */
    public String buildSystemPrompt() {
        return PERSONA +
                "\n\n" + RULES +
                '\n' + THINKING_PACE +
                "\n\n" + OUTPUT_CONTRACT;
    }

    /**
     * 用户提示词：执哪方 + 棋谱 + 当轮局面 + 编号后的合法着法。
     */
    public String buildUserPrompt(AiMoveRequest request, String boardText) {
        String side = request.side();
        StringBuilder sb = new StringBuilder();
        sb.append("你执").append(ChessBoard.sideName(side))
                .append("，对手是").append(ChessBoard.sideName(ChessBoard.opposite(side))).append("。\n");

        String history = formatHistory(request.history());
        if (!history.isEmpty()) {
            sb.append("\n本局至今的着法：\n").append(history).append('\n');
        }

        sb.append("\nFEN：").append(request.fen().trim()).append('\n');
        sb.append("\n当前局面：\n").append(boardText).append('\n');
        sb.append("\n候选着法（只能从中选一个编号）：\n");
        for (LegalMove move : request.legalMoves()) {
            sb.append('[').append(move.i()).append("] ").append(move.san())
                    .append("（").append(move.uci()).append("）\n");
        }

        // 收尾同斗兽棋：不写「选出最好的一手」，指回快棋节奏，模型才有停下来的依据
        sb.append("\n按快棋的节奏想，走出你这一手。");
        return sb.toString();
    }

    /**
     * 把 SAN 序列排成「1. e4 e5 2. Nf3」的棋谱格式。假定从标准初始局面、白方先走开始记。
     */
    static String formatHistory(List<String> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int ply = 0; ply < history.size(); ply++) {
            if (ply % 2 == 0) {
                if (ply > 0) {
                    sb.append(' ');
                }
                sb.append(ply / 2 + 1).append(". ");
            } else {
                sb.append(' ');
            }
            sb.append(history.get(ply));
        }
        return sb.toString();
    }
}

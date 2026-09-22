package com.yaoyuquan.jungle.ai;

import com.yaoyuquan.jungle.llm.JevPrompt;
import com.yaoyuquan.jungle.web.dto.AiMoveRequest;
import com.yaoyuquan.jungle.web.dto.LegalMove;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 构造发给大模型的提示词。
 * <p>
 * 系统提示词固定为棋手人设加通用规则，每一手都完全相同，便于命中提示词缓存；
 * 用户提示词才携带当轮局面与合法着法编号。
 *
 * @author yaoyuquan
 */
@Component
public class PromptBuilder {

    private static final String RULES = """
            斗兽棋规则要点：
            1. 棋盘 7 列（A~G）× 9 行（1~9），红方底线在第 9 行，蓝方底线在第 1 行，蓝方先行。
            2. 兽力由大到小：象 > 狮 > 虎 > 豹 > 狼 > 狗 > 猫 > 鼠。高等级可吃同级及以下，唯独鼠能吃象，象不能吃鼠。
            3. 河流占据 B6~C4 与 E6~F4 两片水域。只有鼠能进入水中；狮与虎可沿直线跳过整条河，但水路上有任何棋子（包括鼠）时跳跃被阻断。
            4. 岸上的棋子吃不到水里的鼠，水里的鼠也吃不到岸上的棋子。
            5. 每方底线两侧各有陷阱。对方棋子踩进我方陷阱后失去全部威势，可被我方任意棋子吃掉。
            6. 任何棋子都不能走进自己的兽巢。有棋子走进对方兽巢即刻获胜；一方棋子被吃光或无子可动也判负。
            """;

    private static final String OUTPUT_CONTRACT = """
            你必须从给出的候选着法编号中选择恰好一个，并以 JSON 返回：
            {"index": <候选着法的编号>, "reason": "<一句中文理由，不超过 40 字>"}
            index 必须是候选列表中真实存在的编号，不得自创着法或返回列表之外的数字。
            只输出这一个 json 对象，不要附加任何解释，也不要套代码块围栏。
            """;

    /**
     * 棋手人设，写死在代码里，不走配置文件。
     */
    private static final String PERSONA = """
            你是一位斗兽棋棋手，请在给出的候选着法里选出当前局面下最好的一手。
            落子前先看清对方的威胁：不做亏损的换子，推进时留好退路，
            留意对方的鼠和能跳河的狮虎，同时盯住通往对方兽巢的那条线。""";

    /**
     * 系统提示词：棋手人设 + 规则。每手不变，可被提示词缓存复用。
     */
    public String buildSystemPrompt() {
        return PERSONA +
                "\n\n" + RULES +
                '\n' + OUTPUT_CONTRACT;
    }

    /**
     * 用户提示词：当轮局面 + 编号后的合法着法。
     */
    public String buildUserPrompt(AiMoveRequest request, String boardText) {
        String side = request.side();
        StringBuilder sb = new StringBuilder();
        sb.append("你执").append(JungleBoard.sideName(side))
                .append("，对手是").append(JungleBoard.sideName(JungleBoard.opposite(side))).append("。");
        sb.append("你要打进").append(JungleBoard.sideName(JungleBoard.opposite(side)))
                .append("的兽巢（").append(JungleBoard.toCoord(JungleBoard.targetDenRow(side), 3)).append("）。\n");
        if (request.moveNumber() != null) {
            sb.append("当前是第 ").append(request.moveNumber()).append(" 手。\n");
        }
        sb.append("\n当前局面：\n").append(boardText).append('\n');
        sb.append("\n候选着法（只能从中选一个编号）：\n");
        for (LegalMove move : request.legalMoves()) {
            sb.append('[').append(move.i()).append("] ").append(describe(move)).append('\n');
        }
        sb.append("\n请选出你认为最好的一手。");
        return sb.toString();
    }

    /**
     * 构造 Jev 需要的结构化输入。
     * <p>
     * 判断型模型没有系统/用户提示词之分：局面放 state，棋手人设与规则放 instructions，
     * 候选着法放 criteria。key 由 JevPrompt.optionKey 生成，方便把选中的 key 还原成候选编号。
     */
    public JevPrompt buildJevPrompt(AiMoveRequest request, String boardText) {
        String side = request.side();
        String opponent = JungleBoard.opposite(side);

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("我方阵营", JungleBoard.sideName(side));
        state.put("目标兽巢", JungleBoard.toCoord(JungleBoard.targetDenRow(side), 3));
        if (request.moveNumber() != null) {
            state.put("当前手数", request.moveNumber());
        }
        state.put("棋盘", boardText);

        Map<String, Object> instructions = new LinkedHashMap<>();
        instructions.put("任务", "你在下斗兽棋，执" + JungleBoard.sideName(side)
                + "，从候选着法中选出最好的一手。");
        instructions.put("棋手", PERSONA);
        instructions.put("规则", RULES);
        instructions.put("取胜条件", "让棋子走进" + JungleBoard.sideName(opponent)
                + "的兽巢，或吃光对方棋子，或让对方无子可动。");

        Map<String, String> criteria = new LinkedHashMap<>();
        for (LegalMove move : request.legalMoves()) {
            criteria.put(JevPrompt.optionKey(move.i()), describe(move));
        }
        return new JevPrompt(state, instructions, criteria);
    }

    /**
     * 着法描述。前端已经给了中文文本，缺失时用坐标兜底。
     */
    private String describe(LegalMove move) {
        if (move.text() != null && !move.text().isBlank()) {
            return move.text();
        }
        return JungleBoard.toCoord(move.from().get(0), move.from().get(1))
                + "→" + JungleBoard.toCoord(move.to().get(0), move.to().get(1));
    }
}

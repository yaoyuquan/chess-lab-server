package com.github.chess.jungle;

import com.github.chess.jungle.web.dto.AiMoveRequest;
import com.github.chess.jungle.web.dto.LegalMove;
import com.github.chess.llm.jev.JevPrompt;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 构造发给大模型的提示词。
 * <p>
 * 系统提示词固定为棋手人设加通用规则，每一手都完全相同，便于命中提示词缓存；
 * 用户提示词才携带当轮局面与合法着法编号。
 * <p>
 * 这里的文字是按「压住推理模型的思考长度」来写的，不是按人类读着顺口来写的。
 * 起因是一次实测：effort 配 low 的一手棋花了 887 秒，34186 个输出 token 里有 34152 个是思考，
 * 真正的答案只有 34 个。翻思考过程，token 主要烧在两件事上，下面两条常量分别对着它们：
 * {@link #RULES} 对着「反复重推规则」，{@link #DECISION_PROCEDURE} 对着「无边界地自由搜索」。
 *
 * @author yaoyuquan
 */
@Component
public class PromptBuilder {

    /**
     * 规则文本，写成可直接查表的判定式，而不是人类习惯的简写。
     * <p>
     * 实测里模型把大量思考花在把简写还原成事实上，而且还原错了好几次又回头改：
     * 「河流占据 B6~C4」它猜了三轮才确定是个矩形；「高等级可吃同级及以下」让它两次回头
     * 确认虎到底能不能吃鼠；水中的鼠能不能吃岸上的子，它推到一半才想起来，
     * 并因此推翻了前面整段结论。这些都是规则的确定性推论，让模型每一手现推一遍纯属浪费。
     * <p>
     * 所以这里把能枚举的全部枚举：水域列到格、跳河列出全部起落点对、陷阱与兽巢列出坐标，
     * 并把「没有别的例外」这种否定式结论直接写死，省得模型自己去确认一遍。
     * <p>
     * 这些坐标全都能从规则本身推出来，不是后端新增的规则知识，规则实现仍然只有前端一份。
     */
    private static final String RULES = """
            斗兽棋规则要点：
            1. 棋盘 7 列（A~G）× 9 行（1~9），红方底线在第 9 行，蓝方底线在第 1 行，蓝方先行。
            2. 兽力由大到小：象8 > 狮7 > 虎6 > 豹5 > 狼4 > 狗3 > 猫2 > 鼠1。
               高等级可吃同级及以下。唯一的例外是鼠与象：鼠能吃象，象不能吃鼠。
               除这一条外没有任何别的例外——狮、虎、豹、狼、狗、猫都能吃鼠。
            3. 水域共 12 格：B4 B5 B6 C4 C5 C6 E4 E5 E6 F4 F5 F6。只有鼠能进入水域。
            4. 鼠在水中时：吃不到岸上的棋子，也不会被岸上的棋子吃；从水里出来只能走到空格，
               不能在出水的同时吃子。
            5. 狮与虎可以跳过整片水域，直接落到对岸的陆地格，落点如下：
               横跨左河：A4↔D4、A5↔D5、A6↔D6
               横跨右河：D4↔G4、D5↔G5、D6↔G6
               纵跨左河：B3↔B7、C3↔C7
               纵跨右河：E3↔E7、F3↔F7
               跨越路径上只要有任何棋子（包括水里的鼠）就不能跳。除上列之外没有别的跳法。
            6. 陷阱：红方陷阱 C9、D8、E9，蓝方陷阱 C1、D2、E1。
               踩进对方陷阱的棋子失去全部威势，可被对方任意棋子吃掉（包括被鼠吃）；
               站在己方陷阱里不受任何影响。
            7. 兽巢：红巢 D9，蓝巢 D1。任何棋子都不能走进己方兽巢，走进对方兽巢即刻获胜。
               D9 的三个邻格是 C9、D8、E9，全部是红方陷阱；D1 的三个邻格 C1、D2、E1 同理。
               也就是说，进兽巢必定要先经过对方的一个陷阱。
            8. 一方棋子被吃光或无子可动也判负。
            """;

    /**
     * 棋手人设。只讲棋风，不讲怎么思考——对话型与判断型模型共用这一段。
     */
    private static final String PERSONA = """
            你是一位斗兽棋棋手，在给出的候选着法里选出当前局面下最好的一手。
            落子前先看清对方的威胁：不做亏损的换子，推进时留好退路，
            留意对方的鼠和能跳河的狮虎，同时盯住通往对方兽巢的那条线。""";

    /**
     * 决策顺序，只发给对话型模型。
     * <p>
     * 实测里模型烧掉最多 token 的不是规则，是没有停下来的理由：它把 24 条候选逐条展开，
     * 为开局设计了七八步的长远计划，然后反复自我推翻——思考过程里出现了
     * 「I keep going in circles」「analysis paralysis」「Time to decide」之后又继续想。
     * 根因是「选出最好的一手」这种最高级说法本身没有终止条件，穷举到死都不算违背指令。
     * <p>
     * 所以这里把目标换成一个有终止条件的过程：给定顺序、命中即返回、限定只看一步。
     * 「命中就不再看其余候选」和「不要推演三步以上」是这段话里最要紧的两句，
     * 它们是终止条件本身，改这段时别把它们改没了。
     * <p>
     * 这段不进 Jev 的 instructions：判断型模型不做链式推理，没有需要压的思考长度，
     * 把一套给推理模型的止损规矩塞给它，只会变成干扰判断的噪音。
     */
    private static final String DECISION_PROCEDURE = """
            按下面的顺序判断，一旦命中就立即返回该着法，不要再看其余候选：
            1. 有能走进对方兽巢的着法 → 直接选它，这一步就赢了。
            2. 有能白吃对方棋子的着法（吃完之后落点不在对方任何棋子的一步可达范围内）
               → 选其中吃到的子力最大的那个。
            3. 我方有价值较高的棋子正被对方一步吃掉 → 选能让它逃走的着法。
            4. 以上都没有 → 在落点不会被对方下一步白吃的着法里，
               选一个向对方兽巢推进、或占住中路 D 列的。

            思考时守住这几条：
            - 只往前看一步，只判断「这一步走完，对方下一步能不能吃掉它」，不要推演三步以上的计划。
            - 不要把候选逐条展开评估，按上面的顺序扫到第一个满足的就停。
            - 开局前几手没有战术可言，直接按第 4 条推进，不要做开局理论的推演。
            - 拿不准时就按顺序取第一个满足的，不要反复权衡、推翻重来。""";

    /**
     * 输出约定。
     * <p>
     * 末尾那句「不要复述分析过程」是给推理模型的：正文里再讲一遍思考，既拖慢又可能
     * 把 JSON 挤到 token 上限之外。
     */
    private static final String OUTPUT_CONTRACT = """
            你必须从给出的候选着法编号中选择恰好一个，并以 json 返回：
            {"index": <候选着法的编号>, "reason": "<一句中文理由，不超过 40 字>"}
            index 必须是候选列表中真实存在的编号，不得自创着法或返回列表之外的数字。
            只输出这一个 json 对象，不要附加任何解释，不要复述分析过程，也不要套代码块围栏。
            """;

    /**
     * 系统提示词：棋手人设 + 规则 + 决策顺序 + 输出约定。每手不变，可被提示词缓存复用。
     */
    public String buildSystemPrompt() {
        return PERSONA +
                "\n\n" + RULES +
                '\n' + DECISION_PROCEDURE +
                "\n\n" + OUTPUT_CONTRACT;
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

        // 收尾这句刻意不写「选出你认为最好的一手」：最高级说法没有终止条件，
        // 实测下来模型会一直找下去。指回决策顺序，它才有停下来的依据
        sb.append("\n按决策顺序扫一遍，选出第一个满足的着法。");
        return sb.toString();
    }

    /**
     * 构造 Jev 需要的结构化输入。
     * <p>
     * 判断型模型没有系统/用户提示词之分：局面放 state，棋手人设与规则放 instructions，
     * 候选着法放 criteria。key 由 JevPrompt.optionKey 生成，方便把选中的 key 还原成候选编号。
     * <p>
     * 这里只放 PERSONA，不放 DECISION_PROCEDURE，理由写在那个常量的注释里。
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

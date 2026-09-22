package com.yaoyuquan.jungle.llm;

import java.util.Map;

/**
 * Jev 这类判断型模型需要的结构化输入。
 * <p>
 * 它不生成文本，而是在给定的 criteria 里挑一个，所以拿到的不是提示词而是
 * 「局面（state）+ 判断要求（instructions）+ 候选项（criteria）」三件套。
 *
 * @param state        局面信息，模型据此作判断
 * @param instructions 判断要求，含棋手人设与规则
 * @param criteria     候选着法，key 由 {@link #optionKey(int)} 生成
 * @author yaoyuquan
 */
public record JevPrompt(
        Map<String, Object> state,
        Map<String, Object> instructions,
        Map<String, String> criteria) {

    /** 候选项 key 的前缀，后面接候选着法编号 */
    private static final String OPTION_PREFIX = "move_";

    /**
     * 候选着法编号转成候选项 key，如 12 → move_12。
     */
    public static String optionKey(int index) {
        return OPTION_PREFIX + index;
    }

    /**
     * 候选项 key 还原成候选着法编号，如 move_12 → 12。不合规的 key 返回 -1。
     */
    public static int indexOf(String key) {
        if (key == null || !key.startsWith(OPTION_PREFIX)) {
            return -1;
        }
        try {
            return Integer.parseInt(key.substring(OPTION_PREFIX.length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}

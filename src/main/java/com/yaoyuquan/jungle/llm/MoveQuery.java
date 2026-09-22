package com.yaoyuquan.jungle.llm;

import com.yaoyuquan.jungle.config.AiPlayer;

/**
 * 一次着法决策所需的全部信息。
 * <p>
 * 同一个局面对不同模型家族要摆成不同形状：对话型模型读 {@link ChatPrompt}，
 * 判断型模型读 {@link JevPrompt}。两份视图一起带上，各客户端取自己要的那份。
 *
 * @param player 棋手配置
 * @param chat   对话型模型的提示词
 * @param jev    判断型模型的结构化输入
 * @author yaoyuquan
 */
public record MoveQuery(AiPlayer player, ChatPrompt chat, JevPrompt jev) {
}

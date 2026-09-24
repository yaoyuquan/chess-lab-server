package com.github.chesslab.llm;

import com.github.chesslab.config.AiPlayer;

/**
 * 一次着法决策所需的全部信息。
 *
 * @param player 棋手配置
 * @param chat   对话型模型的提示词
 * @author yaoyuquan
 */
public record MoveQuery(AiPlayer player, ChatPrompt chat) {
}

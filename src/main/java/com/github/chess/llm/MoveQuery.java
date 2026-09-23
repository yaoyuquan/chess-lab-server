package com.github.chess.llm;

import com.github.chess.config.AiPlayer;

/**
 * 一次着法决策所需的全部信息。
 *
 * @param player 棋手配置
 * @param chat   对话型模型的提示词
 * @author yaoyuquan
 */
public record MoveQuery(AiPlayer player, ChatPrompt chat) {
}

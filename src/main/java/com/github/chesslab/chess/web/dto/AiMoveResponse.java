package com.github.chesslab.chess.web.dto;

/**
 * AI 的落子决定。
 * <p>
 * 与斗兽棋那份字段一模一样，但照「一个棋种一个顶层包」各放一份：
 * 现在还看不出两边的响应会不会分叉，先不为这三个字段抽公共类型。
 *
 * @param index    选中的着法编号，对应请求里 legalMoves 的 i
 * @param reason   选择理由
 * @param fallback 是否是启发式兜底的结果
 * @author yaoyuquan
 */
public record AiMoveResponse(int index, String reason, boolean fallback) {
}

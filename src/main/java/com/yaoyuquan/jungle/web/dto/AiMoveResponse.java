package com.yaoyuquan.jungle.web.dto;

/**
 * AI 的落子决定。
 *
 * @param index    选中的着法编号，对应请求里 legalMoves 的 i
 * @param reason   选择理由，展示在对局记录的悬浮提示里
 * @param fallback 是否是启发式兜底的结果（大模型不可用或连续返回非法编号）
 * @author yaoyuquan
 */
public record AiMoveResponse(int index, String reason, boolean fallback) {
}

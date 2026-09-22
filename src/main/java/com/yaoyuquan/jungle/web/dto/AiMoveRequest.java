package com.yaoyuquan.jungle.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * 请求 AI 走一步棋。
 *
 * @param playerId   棋手 id
 * @param side       AI 执哪一方，r 或 b
 * @param board      当前棋盘，9 行 × 7 列，空格为 null
 * @param moveNumber 当前是第几手，仅用于提示词里给模型一点节奏感
 * @param legalMoves 前端算好的全部合法着法，AI 只能从中选一条
 * @author yaoyuquan
 */
public record AiMoveRequest(
        @NotNull String playerId,
        @NotNull @Pattern(regexp = "[rb]") String side,
        @NotEmpty List<List<BoardCell>> board,
        Integer moveNumber,
        @NotEmpty @Valid List<LegalMove> legalMoves) {
}

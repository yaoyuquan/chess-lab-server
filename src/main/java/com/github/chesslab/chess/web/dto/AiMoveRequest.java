package com.github.chesslab.chess.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/**
 * 请求 AI 走一步国际象棋。
 * <p>
 * 局面用 FEN 而不是二维数组：易位权、吃过路兵目标格、五十步计数这些状态
 * 在 FEN 里本来就有，模型对这个格式也熟，不用另起一套字段。
 *
 * @param playerId   棋手 id
 * @param side       AI 执哪一方，w 白 / b 黑
 * @param fen        当前局面的完整 FEN
 * @param history    开局至今的 SAN 着法序列，可空，只用于提示词里给模型看来路和重复局面
 * @param legalMoves 前端算好的全部合法着法，AI 只能从中选一条
 * @author yaoyuquan
 */
public record AiMoveRequest(
        @NotNull String playerId,
        @NotNull @Pattern(regexp = "[wb]") String side,
        @NotBlank String fen,
        List<String> history,
        @NotEmpty @Valid List<LegalMove> legalMoves) {
}

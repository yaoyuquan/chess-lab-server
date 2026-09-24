package com.github.chesslab.chess.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 前端算好的一条合法着法。后端只在编号范围内做选择，不自行推导着法。
 * <p>
 * 两种记法都要：uci 是机器坐标，兜底评分靠它定位起落格；
 * san 是人类棋谱，带着吃子、将军、将杀、升变这些结论，原样进提示词，
 * 模型不必自己再从坐标推一遍这步棋意味着什么。
 *
 * @param i   着法编号，响应的 index 就是这个值
 * @param uci UCI 记法，如 e2e4、e7e8q、e1g1
 * @param san SAN 记法，如 e4、exd8=Q+、O-O、Qxf7#
 * @author yaoyuquan
 */
public record LegalMove(
        @NotNull Integer i,
        @NotBlank String uci,
        @NotBlank String san) {
}

package com.yaoyuquan.jungle.web.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 前端算好的一条合法着法。后端只在编号范围内做选择，不自行推导着法。
 *
 * @param i    着法编号，从 0 开始连续递增
 * @param from 起点坐标 [行, 列]
 * @param to   终点坐标 [行, 列]
 * @param text 中文描述，如「豹 C7→D7 吃狼」
 * @author yaoyuquan
 */
public record LegalMove(
        @NotNull Integer i,
        @NotEmpty List<Integer> from,
        @NotEmpty List<Integer> to,
        String text) {
}

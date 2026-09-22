package com.yaoyuquan.jungle.web.dto;

/**
 * 棋盘上一个格子里的棋子。空格在 JSON 里表现为 null，不会反序列化成本类型。
 *
 * @param rank 兽力等级，1 鼠 … 8 象
 * @param side 阵营，r 红 / b 蓝
 * @author yaoyuquan
 */
public record BoardCell(Integer rank, String side) {
}

package com.yaoyuquan.jungle.llm;

/**
 * 大模型给出的着法选择。
 *
 * @param index  候选着法编号
 * @param reason 选择理由
 * @author yaoyuquan
 */
public record MoveChoice(int index, String reason) {
}

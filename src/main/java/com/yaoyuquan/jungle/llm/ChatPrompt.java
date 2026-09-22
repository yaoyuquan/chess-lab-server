package com.yaoyuquan.jungle.llm;

/**
 * 对话型模型（Claude、OpenAI 兼容接口）需要的提示词。
 *
 * @param system 系统提示词，含棋手人设、规则与输出约定
 * @param user   用户提示词，含当轮局面与候选着法
 * @author yaoyuquan
 */
public record ChatPrompt(String system, String user) {
}

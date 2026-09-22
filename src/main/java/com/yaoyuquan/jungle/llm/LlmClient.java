package com.yaoyuquan.jungle.llm;

/**
 * 大模型调用的统一入口。
 * <p>
 * 目前有三个实现：Anthropic 官方 SDK、OpenAI 兼容接口、TypeSafe 的 Jev。
 * 前两者是对话型模型，读 {@link ChatPrompt}；Jev 是判断型模型，读 {@link JevPrompt}。
 *
 * @author yaoyuquan
 */
public interface LlmClient {

    /**
     * 让模型在候选着法中选一个。
     *
     * @param query 本轮决策所需的全部信息
     * @return 模型的选择
     * @throws LlmCallException 调用失败或返回内容无法解析
     */
    MoveChoice choose(MoveQuery query);

    /**
     * 当前是否具备调用条件。缺少密钥时返回 false，调用方会直接走兜底。
     */
    boolean isAvailable();
}

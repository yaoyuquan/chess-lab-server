package com.github.chesslab.llm;

/**
 * 大模型调用的统一入口。
 * <p>
 * 目前有两个实现：Anthropic 官方 SDK 与 OpenAI 兼容接口，都是对话型模型，读 {@link ChatPrompt}。
 * <p>
 * 曾经还接过 TypeSafe 的 Jev（判断型模型，不生成文本、只在候选项里给概率）。实测它每手 1~2 秒，
 * 但只看这一步能吃什么，不看对方下一手，送子、不守巢，棋力太弱，已移除。
 * 判断型模型要配合代码做推演才能下棋，而这个服务不实现规则，给不了它这一半。
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

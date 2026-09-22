package com.yaoyuquan.jungle.llm;

/**
 * 大模型调用失败。包括网络异常、鉴权失败、返回内容无法解析等情况。
 *
 * @author yaoyuquan
 */
public class LlmCallException extends RuntimeException {

    public LlmCallException(String message) {
        super(message);
    }

    public LlmCallException(String message, Throwable cause) {
        super(message, cause);
    }
}

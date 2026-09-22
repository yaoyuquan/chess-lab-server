package com.yaoyuquan.jungle.llm;

/**
 * 一次 JSON HTTP 调用的结果。
 *
 * @param status HTTP 状态码
 * @param body   响应体原文
 * @author yaoyuquan
 */
public record JsonHttpResponse(int status, String body) {

    /**
     * 是否是错误状态。
     */
    public boolean isError() {
        return status < 200 || status >= 300;
    }
}

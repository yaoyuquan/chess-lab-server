package com.yaoyuquan.jungle.web.dto;

/**
 * 统一错误响应体。
 *
 * @param code    错误码
 * @param message 面向调用方的错误描述
 * @author yaoyuquan
 */
public record ApiError(String code, String message) {
}

package com.yaoyuquan.jungle.config;

/**
 * 棋手引用了一个不存在的连接名。
 *
 * @author yaoyuquan
 */
public class UnknownProviderException extends RuntimeException {

    public UnknownProviderException(String playerId, String providerName) {
        super("棋手 " + playerId + " 引用了未定义的连接：" + providerName);
    }
}

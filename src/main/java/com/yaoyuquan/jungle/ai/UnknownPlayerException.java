package com.yaoyuquan.jungle.ai;

/**
 * 请求里指定的棋手 id 不存在。
 *
 * @author yaoyuquan
 */
public class UnknownPlayerException extends RuntimeException {

    public UnknownPlayerException(String playerId) {
        super("未知的棋手：" + playerId);
    }
}

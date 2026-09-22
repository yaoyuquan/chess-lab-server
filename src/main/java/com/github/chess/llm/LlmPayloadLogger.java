package com.github.chess.llm;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 把发给模型的请求与模型的原始响应打到日志。
 * <p>
 * 单独拎出来是因为三个客户端都要用，而且排查问题时最需要的就是「发到哪、带了什么头、发了什么、回了什么」。
 * 这是个实验项目，报文一律打印，不设开关：出问题时能直接看到原始报文比日志干净重要得多。
 * 请求头里的密钥统一脱敏，只留头尾几位：够核对是哪一把 key，又不会把完整密钥落进日志文件。
 *
 * @author yaoyuquan
 */
public final class LlmPayloadLogger {

    private static final Logger log = LoggerFactory.getLogger(LlmPayloadLogger.class);

    /** 值必须脱敏的请求头，按小写比对 */
    private static final Set<String> SECRET_HEADERS = Set.of(
            "authorization", "proxy-authorization", "x-api-key", "api-key", "x-goog-api-key");

    /** 脱敏后保留的开头字符数 */
    private static final int KEEP_PREFIX = 6;

    /** 脱敏后保留的结尾字符数 */
    private static final int KEEP_SUFFIX = 4;

    private final String providerName;

    public LlmPayloadLogger(String providerName) {
        this.providerName = providerName;
    }

    /**
     * 打印请求体。拿不到请求头时用这个重载。
     *
     * @param endpoint 实际请求的地址
     * @param payload  请求体
     */
    public void logRequest(String endpoint, String payload) {
        logRequest(endpoint, null, payload);
    }

    /**
     * 打印请求地址、请求头与请求体。
     *
     * @param endpoint 实际请求的地址
     * @param headers  实际发出的请求头，可以为 null
     * @param payload  请求体
     */
    public void logRequest(String endpoint, Map<String, String> headers, String payload) {
        log.info("[{}] → 请求 {}{}\n{}", providerName, endpoint, formatHeaders(headers), payload);
    }

    /**
     * 只打印请求行与请求头，给官方 SDK 的拦截器用。
     * <p>
     * SDK 自己封装了 HTTP，真实地址与最终请求头只有到了拦截器这一层才看得到；
     * 重试也会走到这里，所以一次调用可能打出多条。
     *
     * @param method  HTTP 方法
     * @param url     完整地址
     * @param headers 实际发出的请求头
     */
    public void logHttpRequest(String method, String url, Map<String, String> headers) {
        log.info("[{}] → {} {}{}", providerName, method, url, formatHeaders(headers));
    }

    /**
     * 打印响应。
     *
     * @param status  HTTP 状态码，SDK 路径拿不到时传 -1
     * @param payload 响应体
     */
    public void logResponse(int status, String payload) {
        if (status < 0) {
            log.info("[{}] ← 响应\n{}", providerName, payload);
        } else {
            log.info("[{}] ← 响应 HTTP {}\n{}", providerName, status, payload);
        }
    }

    /**
     * 打印单次调用的耗时与 token 用量。
     *
     * @param endpoint      调用的接口
     * @param elapsedMillis 墙上耗时，毫秒
     * @param usage         token 用量摘要，拿不到时传一句说明
     */
    public void logTiming(String endpoint, long elapsedMillis, String usage) {
        log.info("[{}] ⏱ {} 耗时 {} ms，{}", providerName, endpoint, elapsedMillis, usage);
    }

    /**
     * 打印调用失败。
     */
    public void logFailure(String message, Throwable cause) {
        log.warn("[{}] ✗ 调用失败：{}", providerName, message, cause);
    }

    /**
     * 请求头排成每行一条，密钥类的值就地脱敏。没有请求头时返回空串。
     */
    private static String formatHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        headers.forEach((name, value) -> text.append("\n  ").append(name).append(": ").append(mask(name, value)));
        return text.toString();
    }

    /**
     * 密钥类请求头只留头尾，其余原样返回。
     */
    private static String mask(String name, String value) {
        if (value == null) {
            return "<null>";
        }
        if (!SECRET_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
            return value;
        }
        // Bearer 这类认证方案前缀不是秘密，留着，只打码后面的密钥本体
        int space = value.indexOf(' ');
        if (space > 0 && space < value.length() - 1) {
            return value.substring(0, space + 1) + maskSecret(value.substring(space + 1));
        }
        return maskSecret(value);
    }

    /**
     * 太短的值整条打掉，否则留头尾。
     */
    private static String maskSecret(String secret) {
        if (secret.length() <= KEEP_PREFIX + KEEP_SUFFIX) {
            return "***";
        }
        return secret.substring(0, KEEP_PREFIX) + "***" + secret.substring(secret.length() - KEEP_SUFFIX);
    }
}

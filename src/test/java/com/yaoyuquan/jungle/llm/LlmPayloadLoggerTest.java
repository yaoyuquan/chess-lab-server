package com.yaoyuquan.jungle.llm;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * 请求日志的格式与脱敏。
 *
 * @author yaoyuquan
 */
class LlmPayloadLoggerTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(LlmPayloadLogger.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    @DisplayName("请求日志带上完整地址与请求头")
    void logsUrlAndHeaders() {
        new LlmPayloadLogger("gpt").logRequest("https://api.openai.com/v1/chat/completions", headers(), "{}");

        assertThat(message()).contains("https://api.openai.com/v1/chat/completions")
                .contains("Content-Type: application/json");
    }

    @Test
    @DisplayName("密钥类请求头只留头尾")
    void masksSecretHeaders() {
        new LlmPayloadLogger("gpt").logRequest("https://api.openai.com/v1/chat/completions", headers(), "{}");

        assertThat(message()).contains("Authorization: Bearer sk-abc***7890")
                .doesNotContain("sk-abcdefghijklmn1234567890");
    }

    @Test
    @DisplayName("SDK 路径只打请求行与请求头")
    void logsHttpRequestLine() {
        new LlmPayloadLogger("claude")
                .logHttpRequest("POST", "https://api.anthropic.com/v1/messages",
                        Map.of("x-api-key", "sk-ant-abcdefghijklmn1234567890"));

        assertThat(message()).contains("POST https://api.anthropic.com/v1/messages")
                .contains("x-api-key: sk-ant***7890");
    }

    private static Map<String, String> headers() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer sk-abcdefghijklmn1234567890");
        return headers;
    }

    private String message() {
        return appender.list.getFirst().getFormattedMessage();
    }
}

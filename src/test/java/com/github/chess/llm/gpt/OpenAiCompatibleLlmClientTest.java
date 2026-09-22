package com.github.chess.llm.gpt;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.chess.config.AiPlayer;
import com.github.chess.config.ProviderConfig;
import com.github.chess.llm.ChatPrompt;
import com.github.chess.llm.LlmPayloadLogger;
import com.github.chess.llm.MoveChoiceSchema;
import com.github.chess.llm.MoveQuery;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import static org.assertj.core.api.Assertions.assertThat;
import tools.jackson.databind.ObjectMapper;

/**
 * OpenAI 兼容客户端的请求体构造与调用日志测试。
 *
 * @author yaoyuquan
 */
class OpenAiCompatibleLlmClientTest {

    private static final AiPlayer PLAYER = new AiPlayer(
            "shentong", "深瞳", "SHENTONG",
            "gpt", "gpt-4o", "medium");

    /**
     * 造一个客户端。带 key 才会真正建出 RestClient，但这里只调 buildBody 不发请求。
     */
    private static OpenAiCompatibleLlmClient client() {
        ProviderConfig config = new ProviderConfig("openai", "https://example.test/v1", "test-key", null);
        return new OpenAiCompatibleLlmClient(config, new ObjectMapper(), new LlmPayloadLogger("test"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> responseFormat(Map<String, Object> body) {
        return (Map<String, Object>) body.get("response_format");
    }

    @Test
    @DisplayName("response_format 固定发完整 schema 与 strict")
    void buildsJsonSchemaFormat() {
        Map<String, Object> format = responseFormat(client().buildBody(PLAYER, "sys", "user"));

        assertThat(format).containsEntry("type", "json_schema");
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) format.get("json_schema");
        assertThat(schema).containsEntry("name", MoveChoiceSchema.NAME);
        assertThat(schema).containsEntry("strict", true);
        assertThat(schema).containsKey("schema");
    }

    @Test
    @DisplayName("model 取自棋手配置，temperature 是这条路自己的常量")
    void carriesPlayerSettings() {
        Map<String, Object> body = client().buildBody(PLAYER, "sys", "user");

        assertThat(body).containsEntry("model", "gpt-4o");
        assertThat(body).containsEntry("temperature", 0.6);
    }

    @Test
    @DisplayName("棋手的 effort 原样发成 reasoning_effort")
    void sendsReasoningEffort() {
        assertThat(client().buildBody(PLAYER, "sys", "user"))
                .containsEntry("reasoning_effort", "medium");
    }

    @Test
    @DisplayName("effort 的大小写与首尾空白都容忍")
    void normalizesEffort() {
        AiPlayer loud = new AiPlayer("x", "X", "X", "gpt", "gpt-4o", "  HIGH  ");

        assertThat(client().buildBody(loud, "sys", "user")).containsEntry("reasoning_effort", "high");
    }

    @Test
    @DisplayName("棋手没配 effort 就整个字段不带，交给服务端默认值")
    void omitsReasoningEffortWhenBlank() {
        AiPlayer noEffort = new AiPlayer("x", "X", "X", "gpt", "gpt-4o", "  ");

        assertThat(client().buildBody(noEffort, "sys", "user")).doesNotContainKey("reasoning_effort");
    }

    @Test
    @DisplayName("调用完记下耗时，并把 reasoning token 单独摘出来")
    void logsTimingWithReasoningTokens() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String content = mapper.writeValueAsString(Map.of("index", 2, "reason", "吃子"));
        String responseBody = mapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of("message", Map.of("role", "assistant", "content", content))),
                "usage", Map.of(
                        "prompt_tokens", 1500,
                        "completion_tokens", 830,
                        "completion_tokens_details", Map.of("reasoning_tokens", 790))));

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();

        Logger logger = (Logger) LoggerFactory.getLogger(LlmPayloadLogger.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        try {
            ProviderConfig config = new ProviderConfig(
                    "openai", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "test-key", null);
            new OpenAiCompatibleLlmClient(config, mapper, new LlmPayloadLogger("gpt")).choose(
                    new MoveQuery(PLAYER, new ChatPrompt("sys", "user"), null));

            // reasoning 这个数是这条路排查慢的唯一凭据：配着 low 却回来上千 reasoning token，
            // 就说明对端没认 reasoning_effort。谁把它从日志里删掉，这条会失败
            String timing = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(text -> text.contains("耗时"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("日志里没有耗时那一行"));
            assertThat(timing).contains("in=1500").contains("out=830").contains("reasoning=790");
        } finally {
            logger.detachAppender(appender);
            server.stop(0);
        }
    }
}

package com.github.chess.llm.claude;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.chess.config.AiPlayer;
import com.github.chess.config.ProviderConfig;
import com.github.chess.llm.ChatPrompt;
import com.github.chess.llm.LlmPayloadLogger;
import com.github.chess.llm.MoveChoice;
import com.github.chess.llm.MoveChoiceSchema;
import com.github.chess.llm.MoveQuery;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import static org.assertj.core.api.Assertions.assertThat;
import tools.jackson.databind.ObjectMapper;

/**
 * Anthropic 客户端的报文日志测试。
 * <p>
 * 不打真实接口：在 loopback 上起一个假服务回一条固定报文，因此可以把
 * 「日志里打的」和「服务端实际收到的」对起来比。这正是这组断言的意义——
 * 拦截器打的必须是真正收发的字节，而不是把参数对象重新序列化出来的近似结果。
 *
 * @author yaoyuquan
 */
class AnthropicLlmClientTest {

    private static final AiPlayer PLAYER = new AiPlayer(
            "qingyun", "青云", "QINGYUN", "claude", "claude-test", "low");

    /** 假服务收到的请求体，用来和日志里打的那份比对 */
    private final List<String> receivedBodies = new ArrayList<>();

    /** 假服务收到的请求头名字，用来核对日志有没有漏掉哪一条 */
    private final List<String> receivedHeaderNames = new ArrayList<>();

    private HttpServer server;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;
    private ObjectMapper mapper;
    private AnthropicLlmClient client;

    @BeforeEach
    void setUp() throws IOException {
        mapper = new ObjectMapper();
        // 正文是一段 JSON 字符串，这里用 mapper 套两层，省得在源码里写满转义
        String content = mapper.writeValueAsString(Map.of("index", 2, "reason", "吃子"));
        String responseBody = mapper.writeValueAsString(Map.of(
                "id", "msg_test",
                "type", "message",
                "role", "assistant",
                "model", "claude-test",
                "content", List.of(Map.of("type", "text", "text", content)),
                "stop_reason", "end_turn",
                "usage", Map.of("input_tokens", 11, "output_tokens", 7)));

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            receivedHeaderNames.addAll(exchange.getRequestHeaders().keySet());
            receivedBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();

        logger = (Logger) LoggerFactory.getLogger(LlmPayloadLogger.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);

        client = clientWith(false);
    }

    /**
     * 造一个指向假服务的客户端，只有思考开关不同。
     */
    private AnthropicLlmClient clientWith(Boolean thinking) {
        ProviderConfig config = new ProviderConfig(
                "anthropic", "http://127.0.0.1:" + server.getAddress().getPort(),
                "sk-ant-test-1234567890", thinking);
        return new AnthropicLlmClient(config, mapper, new LlmPayloadLogger("claude"));
    }

    /**
     * 假服务收到的那份请求体里的 thinking 节点。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> sentThinking() {
        Map<String, Object> body = new ObjectMapper().readValue(receivedBodies.getFirst(), Map.class);
        return (Map<String, Object>) body.get("thinking");
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        server.stop(0);
    }

    private static MoveQuery query() {
        return new MoveQuery(PLAYER, new ChatPrompt("你是青云", "该你走了"), null);
    }

    /**
     * 日志里第一条含给定关键字的消息。
     */
    private String logLine(String keyword) {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(text -> text.contains(keyword))
                .findFirst()
                .orElseThrow(() -> new AssertionError("日志里没有含「" + keyword + "」的消息"));
    }

    @Test
    @DisplayName("请求日志里的报文与服务端实际收到的一字不差")
    void logsExactRequestBytes() {
        client.choose(query());

        assertThat(receivedBodies).hasSize(1);
        assertThat(logLine("请求"))
                .contains("/v1/messages")
                .contains(receivedBodies.getFirst());
    }

    @Test
    @DisplayName("SDK 自己加的请求头照样打出来，密钥只留头尾")
    void logsSdkHeadersMasked() {
        client.choose(query());

        assertThat(logLine("请求"))
                .contains("X-Api-Key: sk-ant***7890")
                .contains("X-Stainless-Retry-Count: 0")
                .doesNotContain("sk-ant-test-1234567890");
    }

    @Test
    @DisplayName("拦截器拿到的是 SDK 层的头，anthropic-version 这类由更底层补上")
    void headersBelowInterceptorAreNotLogged() {
        client.choose(query());

        // 这条不是在夸这个行为，而是把它钉住：日志里的请求头比服务端实际收到的少几条，
        // 排查「某个头到底发没发」时别只看日志就下结论。SDK 哪天把这些上移，这里会失败
        assertThat(receivedHeaderNames).contains("Anthropic-version", "Content-type");
        assertThat(logLine("请求")).doesNotContain("Anthropic-version");
    }

    @Test
    @DisplayName("响应日志带上真实状态码与原始报文")
    void logsRawResponse() {
        client.choose(query());

        assertThat(logLine("响应")).contains("HTTP 200").contains("msg_test");
    }

    @Test
    @DisplayName("发出去的 schema 与 asMap() 那份逐字段相同")
    void sendsSameSchemaAsOpenAiPath() {
        client.choose(query());

        // Anthropic 那份 schema 是从 asMap() 派生的，这里从实际报文里挖回来比一遍：
        // 将来谁把两条路的 schema 又拆成两份写，这条会失败
        Map<String, Object> body = new ObjectMapper().readValue(receivedBodies.getFirst(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> outputConfig = (Map<String, Object>) body.get("output_config");
        @SuppressWarnings("unchecked")
        Map<String, Object> format = (Map<String, Object>) outputConfig.get("format");

        assertThat(format).containsEntry("type", "json_schema");
        assertThat(format.get("schema")).isEqualTo(MoveChoiceSchema.asMap());
    }

    @Test
    @DisplayName("连接没配 thinking 时发出去的是 disabled")
    void thinkingDefaultsToDisabled() {
        clientWith(null).choose(query());

        assertThat(sentThinking()).containsEntry("type", "disabled");
    }

    @Test
    @DisplayName("连接配了 thinking: true 时发出去的是 enabled 并带上思考预算")
    void thinkingEnabledCarriesBudget() {
        clientWith(true).choose(query());

        // budget_tokens 是接口的必填项，光把 type 切成 enabled 会被服务端拒掉，
        // 所以这里连着预算一起钉住
        assertThat(sentThinking())
                .containsEntry("type", "enabled")
                .containsKey("budget_tokens");
    }

    @Test
    @DisplayName("读过报文的响应仍能被 SDK 正常解析")
    void stillParsesAfterLogging() {
        MoveChoice choice = client.choose(query());

        assertThat(choice.index()).isEqualTo(2);
        assertThat(choice.reason()).isEqualTo("吃子");
    }
}

package com.github.chess.llm;

import com.github.chess.config.AiPlayer;
import com.github.chess.config.AiProperties;
import com.github.chess.config.ProviderConfig;
import com.github.chess.config.UnknownProviderException;
import com.github.chess.llm.claude.AnthropicLlmClient;
import com.github.chess.llm.gpt.OpenAiCompatibleLlmClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import tools.jackson.databind.ObjectMapper;

/**
 * 具名连接池的单元测试：验证棋手确实能分属不同服务商。
 *
 * @author yaoyuquan
 */
class LlmClientRegistryTest {

    private static AiPlayer player(String id, String provider, String model) {
        return new AiPlayer(id, id, id, provider, model, "medium", 0.5);
    }

    /**
     * 造一个含 claude 与 gpt 两条连接的配置。
     */
    private static AiProperties properties(List<AiPlayer> players) {
        Map<String, ProviderConfig> providers = new LinkedHashMap<>();
        providers.put("claude", new ProviderConfig("anthropic", "", "claude-key"));
        providers.put("gpt", new ProviderConfig("openai", "https://example.test/v1", "gpt-key"));
        return new AiProperties(providers, players);
    }

    private static LlmClientRegistry registry(AiProperties properties) {
        return new LlmClientRegistry(properties, new ObjectMapper());
    }

    @Test
    @DisplayName("两个棋手引用不同连接时拿到不同类型的客户端")
    void resolvesDifferentProvidersPerPlayer() {
        AiPlayer claudePlayer = player("xuanji", "claude", "claude-opus-5");
        AiPlayer gptPlayer = player("qingyun", "gpt", "gpt-4o");
        LlmClientRegistry registry = registry(properties(List.of(claudePlayer, gptPlayer)));

        assertThat(registry.clientFor(claudePlayer)).isInstanceOf(AnthropicLlmClient.class);
        assertThat(registry.clientFor(gptPlayer)).isInstanceOf(OpenAiCompatibleLlmClient.class);
    }

    @Test
    @DisplayName("引用同一条连接的棋手共享同一个客户端实例")
    void sharesClientAcrossPlayersOnSameProvider() {
        AiPlayer a = player("qingyun", "claude", "claude-opus-5");
        AiPlayer b = player("xuanji", "claude", "claude-opus-5");
        LlmClientRegistry registry = registry(properties(List.of(a, b)));

        assertThat(registry.clientFor(a)).isSameAs(registry.clientFor(b));
    }

    @Test
    @DisplayName("棋手没写 provider 时启动就失败，不会静默落到某条连接")
    void rejectsPlayerWithoutProvider() {
        AiPlayer bare = player("bare", null, "gpt-4o");

        assertThatThrownBy(() -> registry(properties(List.of(bare))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bare")
                .hasMessageContaining("provider");
    }

    @Test
    @DisplayName("provider 写成空白串同样不让启动")
    void rejectsBlankProvider() {
        AiPlayer blank = player("blank", "   ", "gpt-4o");

        assertThatThrownBy(() -> registry(properties(List.of(blank))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("blank");
    }

    @Test
    @DisplayName("引用未定义的连接名时抛出异常")
    void rejectsUnknownProvider() {
        AiPlayer typo = player("typo", "clude", "claude-opus-5");
        LlmClientRegistry registry = registry(properties(List.of(typo)));

        assertThatThrownBy(() -> registry.clientFor(typo))
                .isInstanceOf(UnknownProviderException.class)
                .hasMessageContaining("clude");
    }

    @Test
    @DisplayName("缺少密钥的连接建得出客户端但标记为不可用")
    void marksKeylessProviderUnavailable() {
        Map<String, ProviderConfig> providers = new LinkedHashMap<>();
        providers.put("claude", new ProviderConfig("anthropic", "", ""));
        AiPlayer p = player("x", "claude", "claude-opus-5");
        AiProperties properties = new AiProperties(providers, List.of(p));
        LlmClientRegistry registry = registry(properties);

        assertThat(registry.clientFor(p).isAvailable()).isFalse();
        assertThat(registry.hasAnyAvailable()).isFalse();
    }

    @Test
    @DisplayName("只要有一条连接可用就算整体可用")
    void reportsAvailabilityAcrossPool() {
        AiPlayer p = player("x", "claude", "claude-opus-5");
        assertThat(registry(properties(List.of(p))).hasAnyAvailable()).isTrue();
    }

    /**
     * 造一个只含单条连接的配置。
     */
    private static AiProperties single(String name, ProviderConfig config, AiPlayer player) {
        Map<String, ProviderConfig> providers = new LinkedHashMap<>();
        providers.put(name, config);
        return new AiProperties(providers, List.of(player));
    }

    @Test
    @DisplayName("同一条连接换个 type 就换一套接口格式")
    void switchesWireFormatByType() {
        AiPlayer p = player("zisu", "relay", "some-model");
        ProviderConfig openAi = new ProviderConfig("openai", "https://relay.example.test/v1", "relay-key");
        ProviderConfig anthropic = new ProviderConfig("anthropic", "https://relay.example.test", "relay-key");

        assertThat(registry(single("relay", openAi, p)).clientFor(p))
                .isInstanceOf(OpenAiCompatibleLlmClient.class);
        assertThat(registry(single("relay", anthropic, p)).clientFor(p))
                .isInstanceOf(AnthropicLlmClient.class);
    }

    @Test
    @DisplayName("type 留空按 anthropic 处理")
    void blankTypeMeansAnthropic() {
        AiPlayer p = player("zisu", "relay", "claude-opus-5");
        ProviderConfig blank = new ProviderConfig(null, "https://relay.example.test", "relay-key");

        assertThat(registry(single("relay", blank, p)).clientFor(p))
                .isInstanceOf(AnthropicLlmClient.class);
    }

    @Test
    @DisplayName("type 拼错时启动就失败，不会默默退回某种格式")
    void rejectsUnknownType() {
        AiPlayer p = player("zisu", "relay", "some-model");
        ProviderConfig typo = new ProviderConfig("openai-compatible", "https://relay.example.test/v1", "relay-key");

        assertThatThrownBy(() -> registry(single("relay", typo, p)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("openai-compatible")
                .hasMessageContaining("relay");
    }
}

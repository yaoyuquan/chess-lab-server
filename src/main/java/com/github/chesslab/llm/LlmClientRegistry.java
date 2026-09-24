package com.github.chesslab.llm;

import com.github.chesslab.config.AiPlayer;
import com.github.chesslab.config.AiProperties;
import com.github.chesslab.config.ProviderConfig;
import com.github.chesslab.config.UnknownProviderException;
import com.github.chesslab.llm.claude.AnthropicLlmClient;
import com.github.chesslab.llm.gpt.OpenAiCompatibleLlmClient;
import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * 按连接名持有大模型客户端。
 * <p>
 * 每条具名连接只建一个客户端实例，被多个棋手共享；
 * 棋手之间因此可以分属不同服务商，而同一服务商下的棋手又不会重复建连接。
 *
 * @author yaoyuquan
 */
@Component
public class LlmClientRegistry {

    private static final Logger log = LoggerFactory.getLogger(LlmClientRegistry.class);

    private final AiProperties properties;
    private final Map<String, LlmClient> clients = new LinkedHashMap<>();

    public LlmClientRegistry(AiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        requireProviderOnEveryPlayer(properties);
        properties.providersOrEmpty().forEach((name, config) ->
                clients.put(name, create(name, config, objectMapper)));
    }

    /**
     * 棋手的 provider 是必填项，漏配就不让服务起来。
     * <p>
     * 没有「默认连接」这回事：一个漏配 provider 的棋手，静默落到某条连接上只会让人对着
     * 一份看不出问题的配置查半天，不如启动就报出来。
     *
     * @throws IllegalStateException 有棋手没写 provider
     */
    private static void requireProviderOnEveryPlayer(AiProperties properties) {
        properties.playersOrEmpty().stream()
                .filter(player -> !StringUtils.hasText(player.provider()))
                .findFirst()
                .ifPresent(player -> {
                    throw new IllegalStateException("棋手 " + player.id() + " 没有配置 provider，"
                            + "它是必填项，可选值：" + String.join("、", properties.providersOrEmpty().keySet()));
                });
    }

    /**
     * 启动时把连接状况打出来，方便一眼看出哪条连接缺密钥。
     */
    @PostConstruct
    void logStatus() {
        if (clients.isEmpty()) {
            log.warn("未配置任何大模型连接，AI 着法将全部走启发式兜底");
            return;
        }
        clients.forEach((name, client) -> log.info("大模型连接 {}（{}）：{}",
                name,
                properties.provider(name).map(ProviderConfig::normalizedType).orElse("?"),
                client.isAvailable() ? "已就绪" : "缺少密钥，该连接下的棋手走兜底"));

        properties.playersOrEmpty().forEach(player -> properties.resolveProviderName(player)
                .filter(name -> !clients.containsKey(name))
                .ifPresent(name -> log.warn("棋手 {} 引用了未定义的连接 {}，该棋手将走兜底", player.id(), name)));

        warnKeyWithoutBaseUrl();
    }

    /**
     * 配了密钥却没配地址的 OpenAI 兼容连接会打到官方地址去。
     * <p>
     * 自定义连接最容易漏掉 base-url，而漏掉的后果是把第三方密钥发给了 api.openai.com，
     * 所以这里明确提醒一句。Anthropic 连接留空地址是官方地址，属于正常用法，不提醒。
     */
    private void warnKeyWithoutBaseUrl() {
        properties.providersOrEmpty().forEach((name, config) -> {
            if (config.isOpenAi() && config.hasApiKey() && !config.hasBaseUrl()) {
                log.warn("大模型连接 {} 配了密钥但没配 base-url，请求会发往 OpenAI 官方地址", name);
            }
        });
    }

    /**
     * 按连接的接口格式建对应的客户端。所有连接都不超时，一直等到模型返回为止。
     * <p>
     * type 拼错必须当场报错：几种格式的报文完全不同，若默默退回 anthropic，
     * 表现出来只是一个莫名其妙的 404 或鉴权失败，排查成本远高于启动失败。
     *
     * @throws IllegalStateException type 不是支持的接口格式
     */
    private static LlmClient create(String name, ProviderConfig config, ObjectMapper objectMapper) {
        if (config != null && !config.isSupportedType()) {
            throw new IllegalStateException("大模型连接 " + name + " 的 type=" + config.type()
                    + " 无法识别，可选值：" + String.join("、", ProviderConfig.SUPPORTED_TYPES));
        }
        LlmPayloadLogger payloadLogger = new LlmPayloadLogger(name);
        if (config.isOpenAi()) {
            return new OpenAiCompatibleLlmClient(config, objectMapper, payloadLogger);
        }
        return new AnthropicLlmClient(config, objectMapper, payloadLogger);
    }

    /**
     * 取某位棋手该用的客户端。
     *
     * @throws UnknownProviderException 棋手引用了未定义的连接名
     */
    public LlmClient clientFor(AiPlayer player) {
        String name = properties.resolveProviderName(player)
                .orElseThrow(() -> new UnknownProviderException(player.id(), "(未配置任何连接)"));
        LlmClient client = clients.get(name);
        if (client == null) {
            throw new UnknownProviderException(player.id(), name);
        }
        return client;
    }

    /**
     * 是否存在至少一条可用连接。
     */
    public boolean hasAnyAvailable() {
        return clients.values().stream().anyMatch(LlmClient::isAvailable);
    }
}

package com.github.chesslab.config;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * AI 相关的全部配置，绑定 application.yml 中的 chess.ai 节点。
 * <p>
 * providers 是一个具名连接池，棋手通过 provider 字段引用其中一条，
 * 因此不同棋手可以分属不同服务商。
 *
 * @param providers 具名连接池，key 是连接名
 * @param players   棋手清单
 * @author yaoyuquan
 */
@ConfigurationProperties(prefix = "chess.ai")
public record AiProperties(
        Map<String, ProviderConfig> providers,
        List<AiPlayer> players) {

    /**
     * 按 id 查找棋手。
     */
    public Optional<AiPlayer> findPlayer(String playerId) {
        if (players == null || playerId == null) {
            return Optional.empty();
        }
        return players.stream().filter(p -> playerId.equals(p.id())).findFirst();
    }

    /**
     * 棋手引用的连接名。provider 是必填项，空值在启动时就被挡住了，这里返回空只是为了调用方少写一层判空。
     */
    public Optional<String> resolveProviderName(AiPlayer player) {
        if (player != null && StringUtils.hasText(player.provider())) {
            return Optional.of(player.provider());
        }
        return Optional.empty();
    }

    /**
     * 按名字取连接配置。
     */
    public Optional<ProviderConfig> provider(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(providersOrEmpty().get(name));
    }

    public Map<String, ProviderConfig> providersOrEmpty() {
        return providers == null ? Map.of() : providers;
    }

    public List<AiPlayer> playersOrEmpty() {
        return players == null ? List.of() : players;
    }
}

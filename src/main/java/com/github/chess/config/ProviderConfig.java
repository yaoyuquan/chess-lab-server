package com.github.chess.config;

import java.util.List;
import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * 一个具名的大模型服务连接配置。
 * <p>
 * 多个棋手可以引用同一个连接，也可以各自引用不同的连接，
 * 于是「Claude 对 GPT」或者「同一模型走两条不同中转」都能配出来。
 *
 * thinking 是 anthropic 独有的字段，放在连接级而不是 AiPlayer 上：
 * 另外两家根本没有这个概念，摆到棋手配置里只会让 gpt / jev 的棋手都杵着一个对自己无效的开关。
 * 想让同一个模型开着思考和关着思考各下一盘，就配两条指向同一地址、只有这个开关不同的连接。
 *
 * @param type     接口格式，anthropic、openai 或 jev。同一个自定义地址换个格式即可对接另一套协议，留空按 anthropic 处理
 * @param baseUrl  服务地址，留空表示使用该类型的默认地址
 * @param apiKey   访问密钥，留空时该连接下的棋手会直接走启发式兜底
 * @param thinking 是否开启扩展思考，只对 anthropic 生效，留空按关闭处理。另外两家配了会被忽略
 * @author yaoyuquan
 */
public record ProviderConfig(String type, String baseUrl, String apiKey, Boolean thinking) {

    public static final String TYPE_ANTHROPIC = "anthropic";
    public static final String TYPE_OPENAI = "openai";
    public static final String TYPE_JEV = "jev";

    /** 支持的全部服务类型，用于在启动时挡住拼错的 type */
    public static final List<String> SUPPORTED_TYPES = List.of(TYPE_ANTHROPIC, TYPE_OPENAI, TYPE_JEV);

    /**
     * 规范化后的服务类型，未配置时按 anthropic 处理。
     */
    public String normalizedType() {
        if (!StringUtils.hasText(type)) {
            return TYPE_ANTHROPIC;
        }
        return type.toLowerCase(Locale.ROOT);
    }

    /**
     * 是否是 Anthropic 接口。
     */
    public boolean isAnthropic() {
        return TYPE_ANTHROPIC.equals(normalizedType());
    }

    /**
     * 是否是 OpenAI 兼容接口。
     */
    public boolean isOpenAi() {
        return TYPE_OPENAI.equals(normalizedType());
    }

    /**
     * 是否是 TypeSafe 的 Jev 判断型模型。
     */
    public boolean isJev() {
        return TYPE_JEV.equals(normalizedType());
    }

    /**
     * type 是否是能认的值。留空算认得（按 anthropic 处理），拼错则不认。
     */
    public boolean isSupportedType() {
        return SUPPORTED_TYPES.contains(normalizedType());
    }

    /**
     * 是否开启扩展思考。
     * <p>
     * 留空按关闭处理：这条链路只是从二十来个候选编号里挑一个，长链推理换不来多少棋力，
     * 却让每一手多等十几秒，所以默认不开，要开得在配置里明写。
     */
    public boolean thinkingEnabled() {
        return Boolean.TRUE.equals(thinking);
    }

    /**
     * 是否配置了可用的密钥。
     */
    public boolean hasApiKey() {
        return StringUtils.hasText(apiKey);
    }

    /**
     * 是否配置了自定义服务地址。
     */
    public boolean hasBaseUrl() {
        return StringUtils.hasText(baseUrl);
    }
}

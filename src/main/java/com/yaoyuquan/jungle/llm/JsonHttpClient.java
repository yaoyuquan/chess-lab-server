package com.yaoyuquan.jungle.llm;

import com.yaoyuquan.jungle.config.ProviderConfig;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 发 JSON、收原始文本的小客户端，供 OpenAI 兼容与 Jev 两条路径共用。
 * <p>
 * 两个约定很关键：
 * 一是响应按原始字节读回，不交给消息转换器，保证任何状态码下都能拿到报文并打进日志；
 * 二是不用 JdkClientHttpRequestFactory —— 它读响应体走异步订阅，
 * 订阅被取消时抛 "subscription cancelled / closed"，把底层的连接问题伪装成解析失败。
 *
 * @author yaoyuquan
 */
public class JsonHttpClient {

    /**
     * 建连的上限。
     * <p>
     * 读响应不限时，因为那是在等模型思考；建连不限则毫无意义：
     * 地址写错或对端不可达时，请求会一直挂着，连报错都等不到。
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final String baseUrl;
    private final Map<String, String> headers;

    public JsonHttpClient(ProviderConfig config, String defaultBaseUrl) {
        this.baseUrl = config != null && config.hasBaseUrl() ? config.baseUrl() : defaultBaseUrl;
        this.headers = buildHeaders(config);
        this.restClient = build(config, this.baseUrl, this.headers);
    }

    /**
     * 每个请求都会带上的请求头。集中在这里定义，是为了发出去的和打进日志的是同一份。
     */
    private static Map<String, String> buildHeaders(ProviderConfig config) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (config != null && config.hasApiKey()) {
            headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey());
        }
        return Collections.unmodifiableMap(headers);
    }

    private static RestClient build(ProviderConfig config, String baseUrl,
                                    Map<String, String> headers) {
        if (config == null || !config.hasApiKey()) {
            return null;
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // 底层是 URLConnection，读超时 0 毫秒就是一直等，等到模型把话说完为止
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(Duration.ZERO);
        RestClient.Builder builder = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl);
        headers.forEach((name, value) -> builder.defaultHeader(name, value));
        return builder.build();
    }

    /**
     * 是否具备调用条件。缺少密钥时返回 false。
     */
    public boolean isAvailable() {
        return restClient != null;
    }

    /**
     * 完整的请求地址，只用于日志。
     */
    public String endpoint(String path) {
        return baseUrl + path;
    }

    /**
     * 实际发出的请求头，只用于日志。密钥原样返回，由日志那一层负责脱敏。
     */
    public Map<String, String> headers() {
        return headers;
    }

    /**
     * 发一个 JSON 请求。网络层失败抛 {@link LlmCallException}，HTTP 错误状态原样返回供调用方处理。
     */
    public JsonHttpResponse post(String path, String json) {
        if (restClient == null) {
            throw new LlmCallException("未配置 API Key");
        }
        try {
            ResponseEntity<byte[]> response = restClient.post()
                    .uri(path)
                    .body(json)
                    .retrieve()
                    // 不让 RestClient 在 4xx/5xx 上自己抛异常，错误响应体同样要能打进日志
                    .onStatus(status -> true, (req, res) -> {
                    })
                    .toEntity(byte[].class);
            byte[] bytes = response.getBody();
            return new JsonHttpResponse(response.getStatusCode().value(),
                    bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8));
        } catch (RestClientException e) {
            throw new LlmCallException("请求未能完成：" + e.getMessage(), e);
        }
    }
}

package com.yaoyuquan.jungle.llm;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 把模型返回的文本解析成着法选择。
 * <p>
 * 结构化输出通常已经是干净的 JSON，但模型偶尔会裹一层 Markdown 代码块或前后加一句话，
 * 这里统一做一次宽松截取，减少无谓的重试。
 *
 * @author yaoyuquan
 */
public final class JsonMoveChoiceParser {

    private JsonMoveChoiceParser() {
    }

    /**
     * 解析文本。无法得到合法的 index 字段时抛出 LlmCallException 触发重试。
     */
    public static MoveChoice parse(ObjectMapper objectMapper, String raw) {
        String json = extractJsonObject(raw);
        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode indexNode = node.get("index");
            if (indexNode == null || !indexNode.canConvertToInt()) {
                throw new LlmCallException("模型返回缺少合法的 index 字段：" + raw);
            }
            JsonNode reasonNode = node.get("reason");
            String reason = reasonNode == null ? "" : reasonNode.asString("");
            return new MoveChoice(indexNode.asInt(), reason);
        } catch (LlmCallException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmCallException("模型返回无法解析为 JSON：" + raw, e);
        }
    }

    /**
     * 截取文本中的第一个 JSON 对象，去掉可能存在的 Markdown 围栏与说明文字。
     */
    private static String extractJsonObject(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.strip();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }
}

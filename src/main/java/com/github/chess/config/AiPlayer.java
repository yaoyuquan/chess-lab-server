package com.github.chess.config;

/**
 * 一个 AI 棋手的配置。
 * <p>
 * 对局里最多同时出现两个 AI（观战模式的红蓝双方），所以这里就是两个席位。
 * 两个席位不分强弱，也不代表不同棋风，区别只在于各自指向哪个模型。
 *
 * @param id           棋手标识，前端下拉框的 value
 * @param name         中文名，如「青云」
 * @param code         英文代号，如「QINGYUN」
 * @param provider     引用的连接名，对应 chess.ai.providers 下的 key。必填，漏配时服务起不来
 * @param model        实际调用的模型 id
 * @param effort       思考强度。Anthropic 取 low / medium / high / xhigh / max；
 *                     OpenAI 兼容接口原样发成 reasoning_effort，留空则整个字段不带
 * @author yaoyuquan
 */
public record AiPlayer(
        String id,
        String name,
        String code,
        String provider,
        String model,
        String effort) {
}

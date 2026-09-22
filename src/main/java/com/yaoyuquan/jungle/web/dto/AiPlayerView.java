package com.yaoyuquan.jungle.web.dto;

import com.yaoyuquan.jungle.config.AiPlayer;

/**
 * 棋手清单里对外暴露的字段。提示词与模型 id 属于后台细节，不下发给前端。
 *
 * @param id    棋手标识
 * @param name  中文名
 * @param code  英文代号
 * @author yaoyuquan
 */
public record AiPlayerView(String id, String name, String code) {

    /**
     * 从配置对象裁剪出对外视图。
     */
    public static AiPlayerView from(AiPlayer player) {
        return new AiPlayerView(player.id(), player.name(), player.code());
    }
}

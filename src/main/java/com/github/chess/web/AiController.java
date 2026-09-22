package com.github.chess.web;

import com.github.chess.config.AiProperties;
import com.github.chess.web.dto.AiPlayerView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 与棋种无关的 AI 接口。
 * <p>
 * 棋手清单只描述「这两个席位各是谁」，跟下的是斗兽棋还是别的棋无关，
 * 所以留在通用层，不挂到某一种棋的路径下。具体某种棋的着法决策各自有自己的控制器。
 *
 * @author yaoyuquan
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiProperties properties;

    public AiController(AiProperties properties) {
        this.properties = properties;
    }

    /**
     * 棋手清单。前端首页的模型下拉框读这个接口。
     */
    @GetMapping("/models")
    public List<AiPlayerView> models() {
        return properties.playersOrEmpty().stream().map(AiPlayerView::from).toList();
    }
}

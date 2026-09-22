package com.yaoyuquan.jungle.web;

import com.yaoyuquan.jungle.ai.AiMoveService;
import com.yaoyuquan.jungle.config.AiProperties;
import com.yaoyuquan.jungle.web.dto.AiMoveRequest;
import com.yaoyuquan.jungle.web.dto.AiMoveResponse;
import com.yaoyuquan.jungle.web.dto.AiPlayerView;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 相关接口。
 *
 * @author yaoyuquan
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiProperties properties;
    private final AiMoveService aiMoveService;

    public AiController(AiProperties properties, AiMoveService aiMoveService) {
        this.properties = properties;
        this.aiMoveService = aiMoveService;
    }

    /**
     * 棋手清单。前端首页的模型下拉框读这个接口。
     */
    @GetMapping("/models")
    public List<AiPlayerView> models() {
        return properties.playersOrEmpty().stream().map(AiPlayerView::from).toList();
    }

    /**
     * 让 AI 在前端给出的候选着法里选一条。
     */
    @PostMapping("/move")
    public AiMoveResponse move(@Valid @RequestBody AiMoveRequest request) {
        return aiMoveService.decide(request);
    }
}

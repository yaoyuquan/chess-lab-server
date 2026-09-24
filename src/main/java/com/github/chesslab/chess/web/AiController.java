package com.github.chesslab.chess.web;

import com.github.chesslab.chess.AiMoveService;
import com.github.chesslab.chess.web.dto.AiMoveRequest;
import com.github.chesslab.chess.web.dto.AiMoveResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 国际象棋的 AI 接口。
 * <p>
 * bean 名要显式写死：斗兽棋也有一个 AiController，默认 bean 名取类的短名，
 * 两个会在启动时撞成 ConflictingBeanDefinitionException。
 *
 * @author yaoyuquan
 */
@RestController("chessAiController")
@RequestMapping("/api/chess/ai")
public class AiController {

    private final AiMoveService aiMoveService;

    public AiController(AiMoveService aiMoveService) {
        this.aiMoveService = aiMoveService;
    }

    /**
     * 让 AI 在前端给出的候选着法里选一条。
     */
    @PostMapping("/move")
    public AiMoveResponse move(@Valid @RequestBody AiMoveRequest request) {
        return aiMoveService.decide(request);
    }
}

package com.github.chesslab.jungle.web;

import com.github.chesslab.jungle.AiMoveService;
import com.github.chesslab.jungle.web.dto.AiMoveRequest;
import com.github.chesslab.jungle.web.dto.AiMoveResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 斗兽棋的 AI 接口。
 * <p>
 * 路径里带棋种，加围棋、国际象棋时各自再挂一条，互不干扰。
 * <p>
 * bean 名要显式写死：每种棋都会有一个同名的控制器，
 * 默认 bean 名取的是类的短名，两个 AiController 会在启动时撞成
 * ConflictingBeanDefinitionException。别删这个名字，新加棋种时照这个写。
 *
 * @author yaoyuquan
 */
@RestController("jungleAiController")
@RequestMapping("/api/jungle/ai")
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

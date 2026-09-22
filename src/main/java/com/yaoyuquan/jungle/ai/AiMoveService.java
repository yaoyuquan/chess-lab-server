package com.yaoyuquan.jungle.ai;

import com.yaoyuquan.jungle.config.AiPlayer;
import com.yaoyuquan.jungle.config.AiProperties;
import com.yaoyuquan.jungle.config.UnknownProviderException;
import com.yaoyuquan.jungle.llm.ChatPrompt;
import com.yaoyuquan.jungle.llm.LlmCallException;
import com.yaoyuquan.jungle.llm.LlmClient;
import com.yaoyuquan.jungle.llm.LlmClientRegistry;
import com.yaoyuquan.jungle.llm.MoveChoice;
import com.yaoyuquan.jungle.llm.MoveQuery;
import com.yaoyuquan.jungle.web.dto.AiMoveRequest;
import com.yaoyuquan.jungle.web.dto.AiMoveResponse;
import com.yaoyuquan.jungle.web.dto.LegalMove;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * AI 着法决策服务。
 * <p>
 * 流程：按棋手取到它所属连接的客户端 → 调大模型 → 校验返回的编号确实在候选里
 * → 越界或调用失败就落到启发式兜底。
 * 只问一次，不重试：一次重试就是再等模型把整轮推理跑完，等来的还多半是同样的答案，
 * 不如直接兜底。无论如何都会返回一条合法着法，对局不会因为模型抽风而卡死。
 *
 * @author yaoyuquan
 */
@Service
public class AiMoveService {

    private static final Logger log = LoggerFactory.getLogger(AiMoveService.class);
    private static final String FALLBACK_REASON = "AI 未给出有效着法，已按子力价值兜底。";
    private static final String NO_KEY_REASON = "未配置模型密钥，已按子力价值兜底。";
    private static final String BAD_PROVIDER_REASON = "棋手所属的模型连接不可用，已按子力价值兜底。";

    private final AiProperties properties;
    private final PromptBuilder promptBuilder;
    private final BoardRenderer boardRenderer;
    private final FallbackPicker fallbackPicker;
    private final LlmClientRegistry clientRegistry;

    public AiMoveService(AiProperties properties,
                         PromptBuilder promptBuilder,
                         BoardRenderer boardRenderer,
                         FallbackPicker fallbackPicker,
                         LlmClientRegistry clientRegistry) {
        this.properties = properties;
        this.promptBuilder = promptBuilder;
        this.boardRenderer = boardRenderer;
        this.fallbackPicker = fallbackPicker;
        this.clientRegistry = clientRegistry;
    }

    /**
     * 为当前局面选出一步棋。
     */
    public AiMoveResponse decide(AiMoveRequest request) {
        AiPlayer player = properties.findPlayer(request.playerId())
                .orElseThrow(() -> new UnknownPlayerException(request.playerId()));

        LlmClient llmClient;
        try {
            llmClient = clientRegistry.clientFor(player);
        } catch (UnknownProviderException e) {
            // 配置写错不该让对局中断，记一条日志后走兜底
            log.warn("{}", e.getMessage());
            return fallback(request, BAD_PROVIDER_REASON);
        }

        if (!llmClient.isAvailable()) {
            return fallback(request, NO_KEY_REASON);
        }

        Set<Integer> validIndexes = request.legalMoves().stream()
                .map(LegalMove::i)
                .collect(Collectors.toSet());
        // 同一个局面摆成两份视图：对话型模型读提示词，判断型模型读结构化的候选项
        String boardText = boardRenderer.render(request.board());
        MoveQuery query = new MoveQuery(
                player,
                new ChatPrompt(promptBuilder.buildSystemPrompt(),
                        promptBuilder.buildUserPrompt(request, boardText)),
                promptBuilder.buildJevPrompt(request, boardText));

        try {
            MoveChoice choice = llmClient.choose(query);
            if (validIndexes.contains(choice.index())) {
                return new AiMoveResponse(choice.index(), choice.reason(), false);
            }
            log.warn("棋手 {} 返回越界编号 {}，候选共 {} 条", player.id(), choice.index(), validIndexes.size());
        } catch (LlmCallException e) {
            log.warn("棋手 {} 调用失败：{}", player.id(), e.getMessage());
        }
        return fallback(request, FALLBACK_REASON);
    }

    /**
     * 走启发式兜底。
     */
    private AiMoveResponse fallback(AiMoveRequest request, String reason) {
        LegalMove move = fallbackPicker.pick(request);
        return new AiMoveResponse(move.i(), reason, true);
    }
}

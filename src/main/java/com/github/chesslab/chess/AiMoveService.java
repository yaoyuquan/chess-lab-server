package com.github.chesslab.chess;

import com.github.chesslab.chess.web.dto.AiMoveRequest;
import com.github.chesslab.chess.web.dto.AiMoveResponse;
import com.github.chesslab.chess.web.dto.LegalMove;
import com.github.chesslab.config.AiPlayer;
import com.github.chesslab.config.AiProperties;
import com.github.chesslab.config.UnknownPlayerException;
import com.github.chesslab.config.UnknownProviderException;
import com.github.chesslab.llm.ChatPrompt;
import com.github.chesslab.llm.LlmCallException;
import com.github.chesslab.llm.LlmClient;
import com.github.chesslab.llm.LlmClientRegistry;
import com.github.chesslab.llm.MoveChoice;
import com.github.chesslab.llm.MoveQuery;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 国际象棋的 AI 着法决策服务。
 * <p>
 * 流程与斗兽棋一致：按棋手取到它所属连接的客户端 → 调大模型 → 校验返回的编号确实在候选里
 * → 越界或调用失败就落到启发式兜底。只问一次，不重试。
 * 两边的流程眼下几乎逐行相同，但约定是等第二条链路写完再看抽什么，
 * 所以先各写一份，别急着合并。
 * <p>
 * bean 名要显式写：斗兽棋也有一个 AiMoveService，默认 bean 名会撞。
 *
 * @author yaoyuquan
 */
@Service("chessAiMoveService")
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
        String boardText = boardRenderer.render(ChessBoard.parse(request.fen()));
        MoveQuery query = new MoveQuery(
                player,
                new ChatPrompt(promptBuilder.buildSystemPrompt(),
                        promptBuilder.buildUserPrompt(request, boardText)));

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

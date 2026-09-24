package com.github.chesslab.chess;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 把国际象棋局面渲染成大模型易读的文本。
 * <p>
 * 输出三部分：带坐标的网格图、双方子力清单、FEN 里那几个不在棋盘上的状态。
 * FEN 原串也会进提示词，但模型从 FEN 数格子很容易数错一列——网格图给空间感，
 * 坐标清单让它不必自己数，状态行把易位权这类缩写翻成一句话。
 * <p>
 * 网格固定白方在下，不随 AI 执哪方翻转：棋谱、棋书里的图都是这个朝向，模型见得最多。
 * <p>
 * bean 名要显式写：斗兽棋也有一个 BoardRenderer，默认 bean 名会撞。
 *
 * @author yaoyuquan
 */
@Component("chessBoardRenderer")
public class BoardRenderer {

    /**
     * 渲染整个局面。
     */
    public String render(ChessBoard board) {
        return renderGrid(board) +
                "（大写为白方，小写为黑方，. 为空格）\n\n" +
                renderPieceList(board, "w") +
                '\n' +
                renderPieceList(board, "b") +
                "\n\n" +
                renderState(board);
    }

    /**
     * 网格图，每格放 FEN 字母。
     */
    private String renderGrid(ChessBoard board) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < ChessBoard.SIZE; r++) {
            sb.append(ChessBoard.SIZE - r).append(' ');
            for (int c = 0; c < ChessBoard.SIZE; c++) {
                char piece = board.at(r, c);
                sb.append(' ').append(piece == 0 ? '.' : piece);
            }
            sb.append('\n');
        }
        sb.append("   a b c d e f g h\n");
        return sb.toString();
    }

    /**
     * 一方的子力清单，按价值从大到小，末尾附不含王的子力合计。
     */
    private String renderPieceList(ChessBoard board, String side) {
        record Entry(char piece, String square) {
        }
        List<Entry> entries = new ArrayList<>();
        int material = 0;
        for (int r = 0; r < ChessBoard.SIZE; r++) {
            for (int c = 0; c < ChessBoard.SIZE; c++) {
                char piece = board.at(r, c);
                if (!side.equals(ChessBoard.sideOf(piece))) {
                    continue;
                }
                entries.add(new Entry(piece, ChessBoard.toSquare(r, c)));
                material += ChessBoard.valueOf(piece);
            }
        }
        // 王价值记 0，但清单里要排第一个，模型找王的位置最频繁
        entries.sort((a, b) -> Integer.compare(sortKey(b.piece()), sortKey(a.piece())));
        StringBuilder sb = new StringBuilder();
        sb.append(ChessBoard.sideName(side)).append("子力：");
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                sb.append("、");
            }
            Entry e = entries.get(i);
            sb.append(ChessBoard.nameOf(e.piece())).append(' ').append(e.square());
        }
        sb.append("（子力合计 ").append(material).append("）");
        return sb.toString();
    }

    private static int sortKey(char piece) {
        return Character.toLowerCase(piece) == 'k' ? Integer.MAX_VALUE : ChessBoard.valueOf(piece);
    }

    /**
     * 棋盘之外的状态：轮到谁、易位权、过路兵格、五十步计数。
     */
    private String renderState(ChessBoard board) {
        StringBuilder sb = new StringBuilder();
        sb.append("轮到").append(ChessBoard.sideName(board.activeColor())).append("走。\n");
        sb.append("易位权：").append(describeCastling(board.castling())).append('\n');
        if (!"-".equals(board.enPassant())) {
            sb.append("吃过路兵：本手可以吃过路兵落到 ").append(board.enPassant()).append('\n');
        }
        if (board.halfmoveClock() != null) {
            sb.append("五十步计数：已连续 ").append(board.halfmoveClock())
                    .append(" 个半回合没有吃子或动兵（到 100 判和）\n");
        }
        return sb.toString();
    }

    private String describeCastling(String castling) {
        if (castling == null || "-".equals(castling)) {
            return "双方都已不能易位";
        }
        List<String> rights = new ArrayList<>();
        if (castling.indexOf('K') >= 0) {
            rights.add("白方短易位");
        }
        if (castling.indexOf('Q') >= 0) {
            rights.add("白方长易位");
        }
        if (castling.indexOf('k') >= 0) {
            rights.add("黑方短易位");
        }
        if (castling.indexOf('q') >= 0) {
            rights.add("黑方长易位");
        }
        return rights.isEmpty() ? "双方都已不能易位" : String.join("、", rights) + "（仅指权利仍在，本手能否易位以候选着法为准）";
    }
}

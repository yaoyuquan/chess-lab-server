package com.github.chesslab.chess;

import java.util.Map;

/**
 * 从 FEN 读出来的国际象棋局面，只读。
 * <p>
 * 坐标系：grid[行][列]，行 0 是第 8 横线（黑方底线），列 0 是 a 线，与 FEN 的书写顺序一致。
 * <p>
 * 这里只做「把 FEN 拆开」这一件事，不生成着法、不判将军、不判胜负，规则引擎在前端。
 * 棋子价值只服务于兜底启发式。FEN 写坏了也不抛异常：看不懂的字符跳过，越界的格子当空格，
 * 渲染和兜底会跟着失真，但对局不会因此卡住——和斗兽棋「越界当空格读」是同一个取舍。
 *
 * @author yaoyuquan
 */
public final class ChessBoard {

    public static final int SIZE = 8;

    /** 棋子中文名，键是小写 FEN 字母 */
    public static final Map<Character, String> NAMES = Map.of(
            'k', "王", 'q', "后", 'r', "车", 'b', "象", 'n', "马", 'p', "兵");

    /**
     * 子力价值，取最常见的百分制。王记 0：它不会被吃，放进吃子评分只会是噪音。
     */
    public static final Map<Character, Integer> VALUES = Map.of(
            'k', 0, 'q', 900, 'r', 500, 'b', 330, 'n', 320, 'p', 100);

    private final char[][] grid;
    private final String activeColor;
    private final String castling;
    private final String enPassant;
    private final String halfmoveClock;
    private final String fullmoveNumber;

    private ChessBoard(char[][] grid, String activeColor, String castling,
                       String enPassant, String halfmoveClock, String fullmoveNumber) {
        this.grid = grid;
        this.activeColor = activeColor;
        this.castling = castling;
        this.enPassant = enPassant;
        this.halfmoveClock = halfmoveClock;
        this.fullmoveNumber = fullmoveNumber;
    }

    /**
     * 解析 FEN。缺失的字段按 FEN 的默认含义补齐：白方走、无易位权、无过路兵格。
     */
    public static ChessBoard parse(String fen) {
        String[] fields = fen == null ? new String[0] : fen.trim().split("\\s+");
        char[][] grid = new char[SIZE][SIZE];
        String placement = fields.length > 0 ? fields[0] : "";
        String[] ranks = placement.split("/");
        for (int r = 0; r < SIZE && r < ranks.length; r++) {
            int c = 0;
            for (char ch : ranks[r].toCharArray()) {
                if (Character.isDigit(ch)) {
                    c += ch - '0';
                } else if (NAMES.containsKey(Character.toLowerCase(ch))) {
                    if (c < SIZE) {
                        grid[r][c] = ch;
                    }
                    c++;
                }
            }
        }
        return new ChessBoard(grid,
                field(fields, 1, "w"),
                field(fields, 2, "-"),
                field(fields, 3, "-"),
                field(fields, 4, null),
                field(fields, 5, null));
    }

    private static String field(String[] fields, int index, String fallback) {
        return fields.length > index ? fields[index] : fallback;
    }

    /**
     * 取 (r,c) 上的棋子 FEN 字母，越界或空格返回 0。
     */
    public char at(int r, int c) {
        if (r < 0 || r >= SIZE || c < 0 || c >= SIZE) {
            return 0;
        }
        return grid[r][c];
    }

    /**
     * 按代数坐标取棋子，如 at("e4")。坐标写坏时返回 0。
     */
    public char at(String square) {
        int[] rc = toRowCol(square);
        return rc == null ? 0 : at(rc[0], rc[1]);
    }

    public String activeColor() {
        return activeColor;
    }

    public String castling() {
        return castling;
    }

    public String enPassant() {
        return enPassant;
    }

    /**
     * 半回合计数，FEN 里缺失时为 null。
     */
    public String halfmoveClock() {
        return halfmoveClock;
    }

    /**
     * 全回合数，FEN 里缺失时为 null。
     */
    public String fullmoveNumber() {
        return fullmoveNumber;
    }

    /**
     * 代数坐标转 [行, 列]，如 e4 → [4, 4]。写坏时返回 null。
     */
    public static int[] toRowCol(String square) {
        if (square == null || square.length() < 2) {
            return null;
        }
        int c = square.charAt(0) - 'a';
        int rank = square.charAt(1) - '0';
        if (c < 0 || c >= SIZE || rank < 1 || rank > SIZE) {
            return null;
        }
        return new int[]{SIZE - rank, c};
    }

    /**
     * [行, 列] 转代数坐标，如 (7, 4) → e1。
     */
    public static String toSquare(int r, int c) {
        return String.valueOf((char) ('a' + c)) + (SIZE - r);
    }

    /**
     * 棋子属于哪一方：大写白、小写黑，空格返回 null。
     */
    public static String sideOf(char piece) {
        if (piece == 0) {
            return null;
        }
        return Character.isUpperCase(piece) ? "w" : "b";
    }

    /**
     * 对方阵营。
     */
    public static String opposite(String side) {
        return "w".equals(side) ? "b" : "w";
    }

    /**
     * 阵营中文名。
     */
    public static String sideName(String side) {
        return "w".equals(side) ? "白方" : "黑方";
    }

    /**
     * 棋子中文名，不认识的字母回退为问号。
     */
    public static String nameOf(char piece) {
        return NAMES.getOrDefault(Character.toLowerCase(piece), "?");
    }

    /**
     * 子力价值，空格或不认识的字母记 0。
     */
    public static int valueOf(char piece) {
        return VALUES.getOrDefault(Character.toLowerCase(piece), 0);
    }
}

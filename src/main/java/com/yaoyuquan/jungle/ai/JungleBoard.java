package com.yaoyuquan.jungle.ai;

import com.yaoyuquan.jungle.web.dto.BoardCell;
import java.util.List;
import java.util.Map;

/**
 * 斗兽棋棋盘的只读工具方法。
 * <p>
 * 坐标系与前端保持一致：board[行][列]，行 0 是红方底线（红巢在 (0,3)），行 8 是蓝方底线（蓝巢在 (8,3)）。
 * 后端不生成着法、不判胜负，这里只提供渲染棋盘与评估兜底着法所需的最小信息。
 *
 * @author yaoyuquan
 */
public final class JungleBoard {

    public static final int ROWS = 9;
    public static final int COLS = 7;

    /** 列号字母，A 在最左 */
    public static final char[] COL_LABELS = {'A', 'B', 'C', 'D', 'E', 'F', 'G'};

    /** 兽名 */
    public static final Map<Integer, String> NAMES = Map.of(
            1, "鼠", 2, "猫", 3, "狼", 4, "狗", 5, "豹", 6, "虎", 7, "狮", 8, "象");

    /** 子力价值，与前端设计稿保持一致，用于兜底着法评估 */
    public static final Map<Integer, Integer> VALUES = Map.of(
            1, 95, 2, 55, 3, 75, 4, 95, 5, 135, 6, 185, 7, 205, 8, 235);

    private JungleBoard() {
    }

    /**
     * 取 (r,c) 上的棋子，越界或空格返回 null。
     */
    public static BoardCell at(List<List<BoardCell>> board, int r, int c) {
        if (r < 0 || r >= board.size()) {
            return null;
        }
        List<BoardCell> row = board.get(r);
        if (row == null || c < 0 || c >= row.size()) {
            return null;
        }
        return row.get(c);
    }

    /**
     * 水域：3~5 行的 1、2、4、5 列。
     */
    public static boolean isWater(int r, int c) {
        return r >= 3 && r <= 5 && (c == 1 || c == 2 || c == 4 || c == 5);
    }

    /**
     * 若 (r,c) 是兽巢，返回所属阵营，否则返回 null。
     */
    public static String denOf(int r, int c) {
        if (c != 3) {
            return null;
        }
        if (r == 0) {
            return "r";
        }
        if (r == 8) {
            return "b";
        }
        return null;
    }

    /**
     * 若 (r,c) 是陷阱，返回所属阵营，否则返回 null。
     */
    public static String trapOf(int r, int c) {
        if ((r == 0 && (c == 2 || c == 4)) || (r == 1 && c == 3)) {
            return "r";
        }
        if ((r == 8 && (c == 2 || c == 4)) || (r == 7 && c == 3)) {
            return "b";
        }
        return null;
    }

    /**
     * 对方阵营。
     */
    public static String opposite(String side) {
        return "r".equals(side) ? "b" : "r";
    }

    /**
     * 阵营中文名。
     */
    public static String sideName(String side) {
        return "r".equals(side) ? "红方" : "蓝方";
    }

    /**
     * 兽名，等级非法时回退为问号。
     */
    public static String nameOf(Integer rank) {
        return rank == null ? "?" : NAMES.getOrDefault(rank, "?");
    }

    /**
     * 子力价值，等级非法时记 0。
     */
    public static int valueOf(Integer rank) {
        return rank == null ? 0 : VALUES.getOrDefault(rank, 0);
    }

    /**
     * 棋盘坐标转棋谱坐标：列用 A~G，行自下而上记 1~9，故 (0,3) 记作 D9。
     */
    public static String toCoord(int r, int c) {
        if (c < 0 || c >= COL_LABELS.length) {
            return "??";
        }
        return COL_LABELS[c] + String.valueOf(9 - r);
    }

    /**
     * 目标兽巢所在行：红方要打进蓝巢（第 8 行），蓝方要打进红巢（第 0 行）。
     */
    public static int targetDenRow(String side) {
        return "r".equals(side) ? 8 : 0;
    }
}

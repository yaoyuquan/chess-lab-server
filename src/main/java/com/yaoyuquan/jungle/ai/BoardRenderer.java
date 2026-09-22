package com.yaoyuquan.jungle.ai;

import com.yaoyuquan.jungle.web.dto.BoardCell;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 把棋盘渲染成大模型易读的文本。
 * <p>
 * 输出分两部分：一张带行列号的网格图，以及双方子力的坐标清单。
 * 网格图给模型空间感，坐标清单避免它在网格里数错格子。
 *
 * @author yaoyuquan
 */
@Component
public class BoardRenderer {

    /**
     * 渲染整张棋盘。
     */
    public String render(List<List<BoardCell>> board) {
        StringBuilder sb = new StringBuilder();
        sb.append(renderGrid(board));
        sb.append('\n');
        sb.append(renderPieceList(board, "r"));
        sb.append('\n');
        sb.append(renderPieceList(board, "b"));
        return sb.toString();
    }

    /**
     * 渲染网格图。每格四个字符宽：有子写「红狮」，空格写地形标记。
     */
    private String renderGrid(List<List<BoardCell>> board) {
        StringBuilder sb = new StringBuilder();
        sb.append("   ");
        for (char label : JungleBoard.COL_LABELS) {
            sb.append(' ').append(label).append("   ");
        }
        sb.append('\n');
        for (int r = 0; r < JungleBoard.ROWS; r++) {
            sb.append(9 - r).append("  ");
            for (int c = 0; c < JungleBoard.COLS; c++) {
                sb.append(cellText(board, r, c)).append(' ');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * 单格文本。棋子优先于地形显示。
     */
    private String cellText(List<List<BoardCell>> board, int r, int c) {
        BoardCell cell = JungleBoard.at(board, r, c);
        if (cell != null) {
            String sideMark = "r".equals(cell.side()) ? "红" : "蓝";
            return sideMark + JungleBoard.nameOf(cell.rank());
        }
        String den = JungleBoard.denOf(r, c);
        if (den != null) {
            return JungleBoard.sideName(den).charAt(0) + "巢";
        }
        String trap = JungleBoard.trapOf(r, c);
        if (trap != null) {
            return JungleBoard.sideName(trap).charAt(0) + "陷";
        }
        if (JungleBoard.isWater(r, c)) {
            return "～水";
        }
        return "・空";
    }

    /**
     * 渲染一方的子力清单，按兽力从大到小排列。
     */
    private String renderPieceList(List<List<BoardCell>> board, String side) {
        record Entry(int rank, String coord, String note) {
        }
        List<Entry> entries = new ArrayList<>();
        for (int r = 0; r < JungleBoard.ROWS; r++) {
            for (int c = 0; c < JungleBoard.COLS; c++) {
                BoardCell cell = JungleBoard.at(board, r, c);
                if (cell == null || !side.equals(cell.side())) {
                    continue;
                }
                String note = "";
                String trap = JungleBoard.trapOf(r, c);
                if (JungleBoard.isWater(r, c)) {
                    note = "（在水中）";
                } else if (trap != null && !trap.equals(side)) {
                    note = "（踩在对方陷阱里，可被任意棋子吃掉）";
                }
                int rank = cell.rank() == null ? 0 : cell.rank();
                entries.add(new Entry(rank, JungleBoard.toCoord(r, c), note));
            }
        }
        entries.sort((a, b) -> Integer.compare(b.rank(), a.rank()));
        StringBuilder sb = new StringBuilder();
        sb.append(JungleBoard.sideName(side)).append("子力：");
        if (entries.isEmpty()) {
            sb.append("已被全歼");
            return sb.toString();
        }
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                sb.append("、");
            }
            Entry e = entries.get(i);
            sb.append(JungleBoard.nameOf(e.rank())).append(' ').append(e.coord()).append(e.note());
        }
        return sb.toString();
    }
}

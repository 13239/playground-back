package com.project.playground.domain.tetris.service;

import java.util.*;

/** rules.ts v1의 정수 시뮬레이션. 제출 점수를 받지 않고 서버 시드와 tick별 입력을 재생한다. */
public final class TetrisReplay {
    public static final int VERSION = 1;
    private static final int WIDTH = 10, HEIGHT = 22;
    private static final String[][] SHAPES = {{}, {"0000","1111","0000","0000"}, {"0110","0110","0000","0000"},
            {"010","111","000"}, {"100","111","000"}, {"001","111","000"}, {"011","110","000"}, {"110","011","000"}};
    private static final int[][][] KICKS = {
        {{0,0},{-1,0},{-1,1},{0,-2},{-1,-2}}, {{0,0},{1,0},{1,-1},{0,2},{1,2}},
        {{0,0},{1,0},{1,-1},{0,2},{1,2}}, {{0,0},{-1,0},{-1,1},{0,-2},{-1,-2}},
        {{0,0},{1,0},{1,1},{0,-2},{1,-2}}, {{0,0},{-1,0},{-1,-1},{0,2},{-1,2}},
        {{0,0},{-1,0},{-1,-1},{0,2},{-1,2}}, {{0,0},{1,0},{1,1},{0,-2},{1,-2}}
    };
    private static final int[][][] I_KICKS = {
        {{0,0},{-2,0},{1,0},{-2,-1},{1,2}}, {{0,0},{2,0},{-1,0},{2,1},{-1,-2}},
        {{0,0},{-1,0},{2,0},{-1,2},{2,-1}}, {{0,0},{1,0},{-2,0},{1,-2},{-2,1}},
        {{0,0},{2,0},{-1,0},{2,1},{-1,-2}}, {{0,0},{-2,0},{1,0},{-2,-1},{1,2}},
        {{0,0},{1,0},{-2,0},{1,-2},{-2,1}}, {{0,0},{-1,0},{2,0},{-1,2},{2,-1}}
    };
    private final int[] board = new int[WIDTH * HEIGHT];
    private final ArrayDeque<Integer> queue = new ArrayDeque<>();
    private int random, type, rotation, x, y, hold, gravity, grounded, resets, lines, ticks;
    private boolean holdUsed, over;
    private long score;

    public TetrisReplay(long seed) { random = (int) seed; if (random == 0) random = 1; fillQueue(); spawn(null); }
    public long getScore() { return score; }
    public int getLines() { return lines; }
    public boolean isOver() { return over; }
    // 방어 복사한 보드는 규칙의 클라이언트/서버 일치 검증에도 사용할 수 있다.
    public int[] getBoard() { return board.clone(); }
    private int level() { return lines / 10 + 1; }
    private long nextRandom() {
        random ^= random << 13; random ^= random >>> 17; random ^= random << 5;
        return Integer.toUnsignedLong(random);
    }
    private void fillQueue() {
        while (queue.size() < 6) {
            int[] bag = {1,2,3,4,5,6,7};
            for (int i = 6; i > 0; i--) { int j = (int)(nextRandom() % (i + 1)); int old = bag[i]; bag[i] = bag[j]; bag[j] = old; }
            for (int value : bag) queue.add(value);
        }
    }
    private int[][] cells(int pieceType, int r) {
        var shape = SHAPES[pieceType];
        int[][] result = new int[4][2]; int index = 0;
        for (int row = 0; row < shape.length; row++) for (int col = 0; col < shape.length; col++) {
            if (shape[row].charAt(col) != '1') continue;
            int a = col, b = row;
            for (int n = 0; n < (pieceType == 2 ? 0 : r); n++) { int old = a; a = shape.length - 1 - b; b = old; }
            result[index++] = new int[] {a, b};
        }
        return result;
    }
    private boolean fits(int t, int r, int px, int py) {
        for (int[] cell : cells(t, r)) {
            int a = px + cell[0], b = py + cell[1];
            if (a < 0 || a >= WIDTH || b < 0 || b >= HEIGHT || board[b * WIDTH + a] != 0) return false;
        }
        return true;
    }
    private void spawn(Integer next) {
        // 브라우저와 같은 숨김 행 기준으로 생성 위치를 고정한다.
        type = next == null ? queue.remove() : next; rotation = 0; x = 3; y = 1;
        fillQueue(); gravity = 0; grounded = 0; resets = 0;
        if (!fits(type, rotation, x, y)) over = true;
    }
    private void resetLock(boolean wasGrounded) { if (wasGrounded && resets < 15) { grounded = 0; resets++; } }
    private boolean move(int dx, int dy) {
        if (!fits(type, rotation, x + dx, y + dy)) return false;
        boolean wasGrounded = !fits(type, rotation, x, y + 1);
        x += dx; y += dy; if (dx != 0) resetLock(wasGrounded); return true;
    }
    private void rotate(int direction) {
        int next = (rotation + direction + 4) % 4;
        String key = "" + rotation + next;
        int index = switch (key) { case "01" -> 0; case "10" -> 1; case "12" -> 2; case "21" -> 3;
            case "23" -> 4; case "32" -> 5; case "30" -> 6; default -> 7; };
        int[][] offsets = type == 2 ? new int[][]{{0,0}} : (type == 1 ? I_KICKS : KICKS)[index];
        boolean wasGrounded = !fits(type, rotation, x, y + 1);
        for (int[] offset : offsets) if (fits(type, next, x + offset[0], y - offset[1])) {
            rotation = next; x += offset[0]; y -= offset[1]; resetLock(wasGrounded); return;
        }
    }
    private void lock() {
        for (int[] cell : cells(type, rotation)) board[(y + cell[1]) * WIDTH + x + cell[0]] = type;
        int write = HEIGHT - 1, cleared = 0;
        for (int row = HEIGHT - 1; row >= 0; row--) {
            boolean full = true;
            for (int col = 0; col < WIDTH; col++) if (board[row * WIDTH + col] == 0) { full = false; break; }
            if (full) { cleared++; continue; }
            System.arraycopy(board, row * WIDTH, board, write-- * WIDTH, WIDTH);
        }
        Arrays.fill(board, 0, (write + 1) * WIDTH, 0);
        score += new int[]{0,100,300,500,800}[cleared] * (long)level();
        lines += cleared; holdUsed = false; spawn(null);
    }
    /** 입력 순서는 0=좌, 1=우, 2=소프트드롭, 3=시계회전, 4=반시계회전, 5=하드드롭, 6=홀드다. */
    public void step(List<Integer> actions) {
        if (over) return;
        ticks++;
        for (int action : actions) {
            if (over) break;
            switch (action) {
                case 0 -> move(-1, 0);
                case 1 -> move(1, 0);
                case 2 -> { if (move(0, 1)) score++; }
                case 3 -> rotate(1);
                case 4 -> rotate(-1);
                case 5 -> { int old = y; while (fits(type, rotation, x, y + 1)) y++; score += (y - old) * 2L; lock(); }
                case 6 -> { if (!holdUsed) { int old = hold; hold = type; spawn(old == 0 ? null : old); holdUsed = true; } }
                default -> throw new IllegalArgumentException("Unknown replay action");
            }
        }
        if (over) return;
        if (++gravity >= Math.max(3, 48 - (level() - 1) * 4)) { move(0, 1); gravity = 0; }
        if (!fits(type, rotation, x, y + 1)) { if (++grounded >= 30) lock(); } else grounded = 0;
        if (ticks >= 216_000) over = true;
    }

    /** 단조 tick·입력 수 상한·종료 뒤 입력을 검증한 뒤 최대 1시간 분량만 재생한다. */
    public static TetrisReplay verify(long seed, int endTick, List<List<Integer>> inputs) {
        int previous = 0, count = 0;
        for (var input : inputs) {
            int tick = input.get(0), action = input.get(1);
            if (tick < 1 || tick > endTick || tick < previous || action < 0 || action > 6) throw new IllegalArgumentException("Invalid replay");
            count = tick == previous ? count + 1 : 1;
            if (count > 4) throw new IllegalArgumentException("Too many inputs per tick");
            previous = tick;
        }
        var game = new TetrisReplay(seed); int index = 0;
        for (int tick = 1; tick <= endTick; tick++) {
            if (game.over) throw new IllegalArgumentException("Replay continues after game over");
            List<Integer> actions = new ArrayList<>(4);
            while (index < inputs.size() && inputs.get(index).getFirst() == tick) actions.add(inputs.get(index++).get(1));
            game.step(actions);
        }
        return game;
    }
}

package com.project.playground.domain.gomoku.service;

import com.project.playground.domain.gomoku.entity.GomokuMove;
import com.project.playground.domain.gomoku.enums.ForbiddenRule;
import java.util.*;

/** 20×20 자유 룰과 흑의 금수를 판정한다. 열린 3은 합법적인 열린 4로 확장할 수 있어야 한다.
 * 금수 정의·가짜 3의 재귀 판정은 RIF 9.1~9.3을 따른다: https://gomoku.renju.net/rifrules/
 * 판 크기·제비뽑기·시간 초과 패스는 이 서비스의 별도 규칙이다. */
public final class GomokuRules {
    private static final int SIZE = 20;
    private static final int[][] DIRECTIONS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
    public enum Result { VALID, WIN, OCCUPIED, FORBIDDEN }
    private GomokuRules() { }

    public static Result evaluate(List<GomokuMove> moves, short blackSeat, short seat, int x, int y, ForbiddenRule rule) {
        byte[] board = new byte[SIZE * SIZE];
        for (var move : moves) board[index(move.getX(), move.getY())] = (byte) (move.getSeat() == blackSeat ? 1 : 2);
        int point = index(x, y);
        if (board[point] != 0) return Result.OCCUPIED;
        byte color = (byte) (seat == blackSeat ? 1 : 2);
        board[point] = color;
        if (rule == ForbiddenRule.STANDARD && color == 1) {
            if (five(board, point, true)) return Result.WIN;
            return legalBlack(board, point, new HashMap<>()) ? Result.VALID : Result.FORBIDDEN;
        }
        return five(board, point, false) ? Result.WIN : Result.VALID;
    }

    private static boolean five(byte[] board, int point, boolean exact) {
        for (int[] direction : DIRECTIONS) {
            int count = run(board, point, direction);
            if (exact ? count == 5 : count >= 5) return true;
        }
        return false;
    }
    private static int run(byte[] board, int point, int[] direction) {
        int count = 1;
        for (int sign : new int[]{-1, 1}) {
            for (int distance = 1; distance < SIZE; distance++) {
                int candidate = offset(point, direction, sign * distance);
                if (candidate < 0 || board[candidate] != board[point]) break;
                count++;
            }
        }
        return count;
    }
    private static boolean legalBlack(byte[] board, int point, Map<String, Boolean> memo) {
        // 다섯을 동시에 완성한 수는 금수보다 우선한다. 가상 확장 때마다 원래 판을 반드시 복원한다.
        if (five(board, point, true)) return true;
        String key = point + ":" + Arrays.toString(board);
        Boolean cached = memo.get(key);
        if (cached != null) return cached;
        for (int[] direction : DIRECTIONS) if (run(board, point, direction) > 5) { memo.put(key, false); return false; }
        if (fours(board, point).size() >= 2) { memo.put(key, false); return false; }
        Set<Set<Integer>> threes = new HashSet<>();
        for (int[] direction : DIRECTIONS) {
            for (int start = -3; start <= 0; start++) {
                int left = offset(point, direction, start - 1), right = offset(point, direction, start + 4);
                if (left < 0 || right < 0 || board[left] != 0 || board[right] != 0) continue;
                var stones = new HashSet<Integer>();
                int empty = -1; boolean valid = true;
                for (int step = 0; step < 4; step++) {
                    int candidate = offset(point, direction, start + step);
                    if (candidate < 0 || board[candidate] == 2) { valid = false; break; }
                    if (board[candidate] == 1) stones.add(candidate);
                    else if (empty < 0) empty = candidate;
                    else { valid = false; break; }
                }
                if (!valid || stones.size() != 3 || !stones.contains(point) || empty < 0) continue;
                board[empty] = 1;
                boolean open = !five(board, empty, true) && legalBlack(board, empty, memo);
                board[empty] = 0;
                if (open) threes.add(stones);
                if (threes.size() >= 2) { memo.put(key, false); return false; }
            }
        }
        memo.put(key, true);
        return true;
    }
    private static Set<Set<Integer>> fours(byte[] board, int point) {
        Set<Set<Integer>> result = new HashSet<>();
        for (int[] direction : DIRECTIONS) {
            for (int start = -4; start <= 0; start++) {
                var stones = new HashSet<Integer>();
                int empty = -1; boolean valid = true;
                for (int step = 0; step < 5; step++) {
                    int candidate = offset(point, direction, start + step);
                    if (candidate < 0 || board[candidate] == 2) { valid = false; break; }
                    if (board[candidate] == 1) stones.add(candidate);
                    else if (empty < 0) empty = candidate;
                    else { valid = false; break; }
                }
                if (!valid || stones.size() != 4 || !stones.contains(point) || empty < 0) continue;
                board[empty] = 1;
                if (run(board, empty, direction) == 5) result.add(stones);
                board[empty] = 0;
            }
        }
        return result;
    }
    private static int offset(int point, int[] direction, int distance) {
        int x = point % SIZE + direction[0] * distance, y = point / SIZE + direction[1] * distance;
        return x < 0 || y < 0 || x >= SIZE || y >= SIZE ? -1 : index(x, y);
    }
    private static int index(int x, int y) { return y * SIZE + x; }
}

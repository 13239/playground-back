package com.project.playground.domain.memory;

import jakarta.persistence.*;
import lombok.Getter;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

/** 서버만 정답을 보관한다. 미리보기·오답 공개·턴 마감은 클라이언트 시각과 독립적이다. */
@Embeddable @Getter
public class MemoryBoard {
    public static final int COUNT = 30;
    @Column(length = 30) private String deck;
    @Column(length = 30) private String owners = "0".repeat(COUNT);
    @Column(length = 12, nullable = false) private String phase = "WAITING";
    @Column(length = 36) private String matchId;
    private Instant startedAt;
    private Instant deadline;
    private Instant finishedAt;
    private int firstCard = -1;
    private int secondCard = -1;
    private int turn = 1;
    private int firstSeat = 1;
    private int scoreOne;
    private int scoreTwo;
    private int attempts;
    private int winner;
    @Column(length = 16) private String finishReason;
    private boolean recorded;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 재대결마다 새 배치와 판 식별자를 만들고 선공만 교대한다. */
    public void begin(boolean rematch, Instant now) {
        var cards = new ArrayList<Character>();
        for (int i = 0; i < 15; i++) { cards.add(Character.forDigit(i, 16)); cards.add(Character.forDigit(i, 16)); }
        Collections.shuffle(cards, RANDOM);
        var encoded = new StringBuilder(COUNT); cards.forEach(encoded::append); deck = encoded.toString();
        owners = "0".repeat(COUNT); phase = "PREVIEW"; matchId = UUID.randomUUID().toString();
        startedAt = now.plusSeconds(3); deadline = startedAt; finishedAt = null;
        firstCard = -1; secondCard = -1; scoreOne = 0; scoreTwo = 0; attempts = 0; winner = 0;
        finishReason = null; recorded = false;
        firstSeat = rematch ? 3 - firstSeat : RANDOM.nextInt(2) + 1; turn = firstSeat;
    }

    /** 늦게 깨어난 서버도 마감 시각에서 시간을 이어 계산해 새 10초를 부당하게 부여하지 않는다. */
    public boolean advance(boolean solo, Instant now) {
        boolean changed = false;
        if ("PREVIEW".equals(phase) && !now.isBefore(startedAt)) {
            phase = "PLAYING"; turn = solo ? 1 : firstSeat; deadline = solo ? null : startedAt.plusSeconds(10); changed = true;
        }
        if ("MISMATCH".equals(phase) && !now.isBefore(deadline)) {
            Instant hiddenAt = deadline;
            firstCard = -1; secondCard = -1; phase = "PLAYING";
            if (!solo) turn = 3 - turn;
            deadline = solo ? null : hiddenAt.plusSeconds(10); changed = true;
        }
        if (!solo && "PLAYING".equals(phase) && !now.isBefore(deadline)) {
            long passed = Math.floorDiv(now.toEpochMilli() - deadline.toEpochMilli(), 10_000) + 1;
            if (passed % 2 == 1) turn = 3 - turn;
            firstCard = -1; secondCard = -1; deadline = deadline.plusSeconds(passed * 10); changed = true;
        }
        return changed;
    }

    public boolean canFlip(boolean solo, int seat, int position) {
        return "PLAYING".equals(phase) && (solo || seat == turn) && position >= 0 && position < COUNT
                && owners.charAt(position) == '0' && position != firstCard;
    }

    /** 정답은 즉시 점수에 반영하고 오답의 두 번째 선택부터 정확히 1초 공개한다. */
    public void flip(boolean solo, int seat, int position, Instant now) {
        if (!canFlip(solo, seat, position)) throw new IllegalArgumentException("Invalid flip");
        if (firstCard < 0) { firstCard = position; return; }
        secondCard = position; attempts++;
        if (deck.charAt(firstCard) == deck.charAt(secondCard)) {
            var marked = owners.toCharArray(); marked[firstCard] = (char)('0' + seat); marked[secondCard] = (char)('0' + seat);
            owners = new String(marked);
            if (seat == 1) scoreOne++; else scoreTwo++;
            firstCard = -1; secondCard = -1;
            deadline = solo ? null : now.plusSeconds(10);
            if (scoreOne + scoreTwo == 15) finish(solo ? 1 : scoreOne > scoreTwo ? 1 : 2, "COMPLETE", now);
        } else { phase = "MISMATCH"; deadline = now.plusSeconds(1); }
    }

    /** 최초 공개 이후에는 현재 뒤집힌 카드와 맞춘 카드만 직렬화한다. 종료해도 미공개 정답은 공개하지 않는다. */
    public List<Integer> visible(Instant now) {
        var result = new ArrayList<Integer>(COUNT);
        for (int i = 0; i < COUNT; i++) {
            boolean shown = deck != null && (("PREVIEW".equals(phase) && now.isBefore(startedAt))
                    || owners.charAt(i) != '0' || i == firstCard || i == secondCard);
            result.add(shown ? Character.digit(deck.charAt(i), 16) : -1);
        }
        return result;
    }
    public boolean active() { return List.of("PREVIEW", "PLAYING", "MISMATCH").contains(phase); }
    public void finish(int winningSeat, String reason, Instant now) {
        phase = "FINISHED"; winner = winningSeat; finishReason = reason; finishedAt = now; deadline = null;
        firstCard = -1; secondCard = -1;
    }
    public void markRecorded() { recorded = true; }
}

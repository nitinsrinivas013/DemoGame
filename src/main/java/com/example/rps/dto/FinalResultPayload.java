package com.example.rps.dto;

import com.example.rps.model.Game;
import com.example.rps.model.Round;
import com.example.rps.model.RoundWinner;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Broadcast once when round 10 completes. All totals are derived purely from
 * the stored round history - never hard-coded.
 */
public class FinalResultPayload {

    private int nitinScore;
    private int saraScore;
    private int nitinWins;
    private int saraWins;
    private int draws;
    private int totalRounds;
    private String overallWinner; // "NITIN" | "SARA" | "DRAW"
    private List<RoundHistoryEntry> roundHistory;

    public static FinalResultPayload from(Game game) {
        FinalResultPayload dto = new FinalResultPayload();
        List<Round> history = game.getRoundHistory();

        long nitinWins = history.stream().filter(r -> r.getWinner() == RoundWinner.NITIN).count();
        long saraWins = history.stream().filter(r -> r.getWinner() == RoundWinner.SARA).count();
        long draws = history.stream().filter(r -> r.getWinner() == RoundWinner.DRAW).count();

        dto.nitinScore = game.getNitinScore();
        dto.saraScore = game.getSaraScore();
        dto.nitinWins = (int) nitinWins;
        dto.saraWins = (int) saraWins;
        dto.draws = (int) draws;
        dto.totalRounds = history.size();
        dto.overallWinner = game.getNitinScore() > game.getSaraScore() ? "NITIN"
                : game.getSaraScore() > game.getNitinScore() ? "SARA" : "DRAW";
        dto.roundHistory = history.stream().map(RoundHistoryEntry::new).collect(Collectors.toList());
        return dto;
    }

    public int getNitinScore() {
        return nitinScore;
    }

    public int getSaraScore() {
        return saraScore;
    }

    public int getNitinWins() {
        return nitinWins;
    }

    public int getSaraWins() {
        return saraWins;
    }

    public int getDraws() {
        return draws;
    }

    public int getTotalRounds() {
        return totalRounds;
    }

    public String getOverallWinner() {
        return overallWinner;
    }

    public List<RoundHistoryEntry> getRoundHistory() {
        return roundHistory;
    }
}

package com.example.rps.dto;

import com.example.rps.model.Game;
import com.example.rps.model.GameStatus;
import com.example.rps.model.Round;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Full, client-safe snapshot of a game's current state. Never includes an
 * in-flight, unrevealed move for the *opponent* - only whether each side has
 * moved yet, so the frontend can show "waiting for opponent" without leaking moves.
 */
public class GameStateResponse {

    private String gameId;
    private boolean nitinJoined;
    private boolean saraJoined;
    private boolean nitinConnected;
    private boolean saraConnected;
    private int currentRound;
    private int totalRounds;
    private int nitinScore;
    private int saraScore;
    private GameStatus status;
    private boolean nitinMoveSubmitted;
    private boolean saraMoveSubmitted;
    private List<RoundHistoryEntry> roundHistory;

    public static GameStateResponse from(Game game) {
        GameStateResponse dto = new GameStateResponse();
        dto.gameId = game.getGameId();
        dto.nitinJoined = game.isNitinJoined();
        dto.saraJoined = game.isSaraJoined();
        dto.nitinConnected = game.isPlayerConnected(com.example.rps.model.Player.NITIN);
        dto.saraConnected = game.isPlayerConnected(com.example.rps.model.Player.SARA);
        dto.currentRound = game.getCurrentRound();
        dto.totalRounds = Game.TOTAL_ROUNDS;
        dto.nitinScore = game.getNitinScore();
        dto.saraScore = game.getSaraScore();
        dto.status = game.getStatus();
        dto.nitinMoveSubmitted = game.hasMove(com.example.rps.model.Player.NITIN);
        dto.saraMoveSubmitted = game.hasMove(com.example.rps.model.Player.SARA);
        List<Round> history = game.getRoundHistory();
        dto.roundHistory = history.stream().map(RoundHistoryEntry::new).collect(Collectors.toList());
        return dto;
    }

    public String getGameId() {
        return gameId;
    }

    public boolean isNitinJoined() {
        return nitinJoined;
    }

    public boolean isSaraJoined() {
        return saraJoined;
    }

    public boolean isNitinConnected() {
        return nitinConnected;
    }

    public boolean isSaraConnected() {
        return saraConnected;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public int getTotalRounds() {
        return totalRounds;
    }

    public int getNitinScore() {
        return nitinScore;
    }

    public int getSaraScore() {
        return saraScore;
    }

    public GameStatus getStatus() {
        return status;
    }

    public boolean isNitinMoveSubmitted() {
        return nitinMoveSubmitted;
    }

    public boolean isSaraMoveSubmitted() {
        return saraMoveSubmitted;
    }

    public List<RoundHistoryEntry> getRoundHistory() {
        return roundHistory;
    }
}

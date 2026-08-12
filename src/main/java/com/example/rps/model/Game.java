package com.example.rps.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Authoritative, server-side state for a single game between Nitin and Sara.
 * This class is intentionally mutable and is always guarded by the owning
 * GameService (synchronized per game instance) since two players act on it concurrently.
 */
public class Game {

    public static final int TOTAL_ROUNDS = 10;

    private final String gameId;

    private boolean nitinJoined = false;
    private boolean saraJoined = false;

    private boolean nitinConnected = false;
    private boolean saraConnected = false;

    /** STOMP session ids currently associated with each role, used to route disconnect events. */
    private String nitinSessionId;
    private String saraSessionId;

    private int currentRound = 1;
    private int nitinScore = 0;
    private int saraScore = 0;

    private final Map<Player, Move> currentMoves = new EnumMap<>(Player.class);

    private GameStatus status = GameStatus.WAITING_FOR_PLAYER;

    private final List<Round> roundHistory = new ArrayList<>();

    public Game(String gameId) {
        this.gameId = gameId;
    }

    public String getGameId() {
        return gameId;
    }

    public boolean isNitinJoined() {
        return nitinJoined;
    }

    public void setNitinJoined(boolean nitinJoined) {
        this.nitinJoined = nitinJoined;
    }

    public boolean isSaraJoined() {
        return saraJoined;
    }

    public void setSaraJoined(boolean saraJoined) {
        this.saraJoined = saraJoined;
    }

    public boolean isPlayerJoined(Player player) {
        return player == Player.NITIN ? nitinJoined : saraJoined;
    }

    public void setPlayerJoined(Player player, boolean joined) {
        if (player == Player.NITIN) {
            this.nitinJoined = joined;
        } else {
            this.saraJoined = joined;
        }
    }

    public boolean isBothJoined() {
        return nitinJoined && saraJoined;
    }

    public boolean isPlayerConnected(Player player) {
        return player == Player.NITIN ? nitinConnected : saraConnected;
    }

    public void setPlayerConnected(Player player, boolean connected) {
        if (player == Player.NITIN) {
            this.nitinConnected = connected;
        } else {
            this.saraConnected = connected;
        }
    }

    public String getSessionId(Player player) {
        return player == Player.NITIN ? nitinSessionId : saraSessionId;
    }

    public void setSessionId(Player player, String sessionId) {
        if (player == Player.NITIN) {
            this.nitinSessionId = sessionId;
        } else {
            this.saraSessionId = sessionId;
        }
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public void setCurrentRound(int currentRound) {
        this.currentRound = currentRound;
    }

    public int getNitinScore() {
        return nitinScore;
    }

    public int getSaraScore() {
        return saraScore;
    }

    public void incrementScore(Player player) {
        if (player == Player.NITIN) {
            nitinScore++;
        } else {
            saraScore++;
        }
    }

    public Move getMove(Player player) {
        return currentMoves.get(player);
    }

    public void setMove(Player player, Move move) {
        currentMoves.put(player, move);
    }

    public boolean hasMove(Player player) {
        return currentMoves.containsKey(player);
    }

    public boolean bothMovesSubmitted() {
        return currentMoves.containsKey(Player.NITIN) && currentMoves.containsKey(Player.SARA);
    }

    public void clearMoves() {
        currentMoves.clear();
    }

    public GameStatus getStatus() {
        return status;
    }

    public void setStatus(GameStatus status) {
        this.status = status;
    }

    public List<Round> getRoundHistory() {
        return roundHistory;
    }

    public void addRound(Round round) {
        roundHistory.add(round);
    }
}

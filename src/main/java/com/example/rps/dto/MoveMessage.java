package com.example.rps.dto;

import com.example.rps.model.Move;
import com.example.rps.model.Player;

/**
 * Inbound message sent by a client over STOMP to submit a move.
 *
 * Destination: /app/game/{gameId}/move
 *
 * Deliberately contains ONLY gameId, player and move. It must never carry a
 * winner, score, or the opponent's move - those are exclusively server-calculated.
 */
public class MoveMessage {

    private String gameId;
    private Player player;
    private Move move;

    public MoveMessage() {
    }

    public MoveMessage(String gameId, Player player, Move move) {
        this.gameId = gameId;
        this.player = player;
        this.move = move;
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public Move getMove() {
        return move;
    }

    public void setMove(Move move) {
        this.move = move;
    }
}

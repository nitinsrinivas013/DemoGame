package com.example.rps.dto;

import com.example.rps.model.Player;

/**
 * Inbound message a client sends right after opening the STOMP connection and
 * subscribing to /topic/game/{gameId}, so the server can bind this WebSocket
 * session to the player's role (needed for disconnect detection).
 *
 * Destination: /app/game/{gameId}/connect
 */
public class ConnectMessage {

    private String gameId;
    private Player player;

    public ConnectMessage() {
    }

    public ConnectMessage(String gameId, Player player) {
        this.gameId = gameId;
        this.player = player;
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
}

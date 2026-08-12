package com.example.rps.dto;

import com.example.rps.model.Player;
import jakarta.validation.constraints.NotNull;

/**
 * Body for POST /api/games/{gameId}/join.
 */
public class JoinGameRequest {

    @NotNull(message = "player is required")
    private Player player;

    public JoinGameRequest() {
    }

    public JoinGameRequest(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }
}

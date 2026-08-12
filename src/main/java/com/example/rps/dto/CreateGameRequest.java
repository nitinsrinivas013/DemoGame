package com.example.rps.dto;

import com.example.rps.model.Player;
import jakarta.validation.constraints.NotNull;

/**
 * Body for POST /api/games. The creator immediately claims a player role.
 */
public class CreateGameRequest {

    @NotNull(message = "player is required")
    private Player player;

    public CreateGameRequest() {
    }

    public CreateGameRequest(Player player) {
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }
}

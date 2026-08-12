package com.example.rps.model;

/**
 * Lifecycle states of a game.
 */
public enum GameStatus {
    /** Only one player slot filled; waiting for the second player to join. */
    WAITING_FOR_PLAYER,
    /** Both players connected; a round is in progress and awaiting one or both moves. */
    IN_PROGRESS,
    /** All 10 rounds have been played. No further moves are accepted. */
    FINISHED
}

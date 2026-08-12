package com.example.rps.model;

/**
 * Discriminator for messages broadcast over /topic/game/{gameId}.
 */
public enum MessageType {
    PLAYER_JOINED,
    GAME_STARTED,
    WAITING_FOR_OPPONENT,
    MOVE_SUBMITTED,
    ROUND_RESULT,
    SCORE_UPDATE,
    NEXT_ROUND,
    GAME_FINISHED,
    PLAYER_DISCONNECTED,
    PLAYER_RECONNECTED,
    GAME_RESET,
    GAME_STATE,
    ERROR
}

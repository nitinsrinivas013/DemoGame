package com.example.rps.model;

/**
 * The three possible moves. Contains the authoritative Rock-Paper-Scissors
 * beats-relationship so winner determination lives in one place, on the server.
 */
public enum Move {
    ROCK,
    PAPER,
    SCISSORS;

    /**
     * Returns true if "this" move beats the given opponent move,
     * using standard Rock-Paper-Scissors rules:
     * ROCK beats SCISSORS, SCISSORS beats PAPER, PAPER beats ROCK.
     */
    public boolean beats(Move opponent) {
        if (this == opponent) {
            return false;
        }
        return switch (this) {
            case ROCK -> opponent == SCISSORS;
            case SCISSORS -> opponent == PAPER;
            case PAPER -> opponent == ROCK;
        };
    }
}

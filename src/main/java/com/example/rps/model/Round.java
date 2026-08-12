package com.example.rps.model;

/**
 * A single completed round, permanently stored in the game's history.
 */
public class Round {

    private final int roundNumber;
    private final Move nitinMove;
    private final Move saraMove;
    private final RoundWinner winner;

    public Round(int roundNumber, Move nitinMove, Move saraMove, RoundWinner winner) {
        this.roundNumber = roundNumber;
        this.nitinMove = nitinMove;
        this.saraMove = saraMove;
        this.winner = winner;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public Move getNitinMove() {
        return nitinMove;
    }

    public Move getSaraMove() {
        return saraMove;
    }

    public RoundWinner getWinner() {
        return winner;
    }
}

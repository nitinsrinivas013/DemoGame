package com.example.rps.dto;

import com.example.rps.model.Move;
import com.example.rps.model.RoundWinner;

/**
 * Broadcast to both players once both moves for a round have been received
 * and the server has authoritatively calculated the winner.
 */
public class RoundResultPayload {

    private int round;
    private Move nitinMove;
    private Move saraMove;
    private RoundWinner winner;
    private int nitinScore;
    private int saraScore;
    private boolean gameFinished;

    public RoundResultPayload(int round, Move nitinMove, Move saraMove, RoundWinner winner,
                               int nitinScore, int saraScore, boolean gameFinished) {
        this.round = round;
        this.nitinMove = nitinMove;
        this.saraMove = saraMove;
        this.winner = winner;
        this.nitinScore = nitinScore;
        this.saraScore = saraScore;
        this.gameFinished = gameFinished;
    }

    public int getRound() {
        return round;
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

    public int getNitinScore() {
        return nitinScore;
    }

    public int getSaraScore() {
        return saraScore;
    }

    public boolean isGameFinished() {
        return gameFinished;
    }
}

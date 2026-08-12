package com.example.rps.dto;

import com.example.rps.model.Move;
import com.example.rps.model.Round;
import com.example.rps.model.RoundWinner;

/**
 * Read-only view of a completed round, safe to serialize to clients.
 */
public class RoundHistoryEntry {

    private final int roundNumber;
    private final Move nitinMove;
    private final Move saraMove;
    private final RoundWinner winner;

    public RoundHistoryEntry(Round round) {
        this.roundNumber = round.getRoundNumber();
        this.nitinMove = round.getNitinMove();
        this.saraMove = round.getSaraMove();
        this.winner = round.getWinner();
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

package com.example.rps.exception;

/**
 * Raised for any illegal action: joining a taken slot, submitting a move for
 * a player you don't control, submitting twice, submitting after the round or
 * game has ended, etc.
 */
public class InvalidPlayerActionException extends RuntimeException {
    public InvalidPlayerActionException(String message) {
        super(message);
    }
}

package com.example.rps.dto;

import java.time.Instant;

/**
 * Uniform error body returned by REST endpoints and pushed over
 * /topic/game/{gameId} or /user/queue/errors when something goes wrong.
 */
public class ErrorResponse {

    private String message;
    private String code;
    private Instant timestamp = Instant.now();

    public ErrorResponse() {
    }

    public ErrorResponse(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}

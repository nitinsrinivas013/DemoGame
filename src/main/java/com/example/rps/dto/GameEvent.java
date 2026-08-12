package com.example.rps.dto;

import com.example.rps.model.MessageType;

/**
 * Generic envelope broadcast to /topic/game/{gameId}. The `type` field lets the
 * frontend dispatch to the right handler; `payload` carries the type-specific data
 * (e.g. a GameStateResponse, a RoundResultPayload, or a simple text message).
 */
public class GameEvent {

    private MessageType type;
    private Object payload;

    public GameEvent() {
    }

    public GameEvent(MessageType type, Object payload) {
        this.type = type;
        this.payload = payload;
    }

    public static GameEvent of(MessageType type, Object payload) {
        return new GameEvent(type, payload);
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }
}

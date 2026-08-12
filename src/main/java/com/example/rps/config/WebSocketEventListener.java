package com.example.rps.config;

import com.example.rps.service.GameService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Bridges Spring's WebSocket session lifecycle events into GameService so
 * disconnects (tab closed, network drop, phone locked, etc.) are detected
 * and the opponent is notified, without the client having to explicitly say goodbye.
 */
@Component
public class WebSocketEventListener {

    private final GameService gameService;

    public WebSocketEventListener(GameService gameService) {
        this.gameService = gameService;
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        gameService.handleSessionDisconnect(sessionId);
    }
}

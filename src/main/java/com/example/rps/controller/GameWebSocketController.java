package com.example.rps.controller;

import com.example.rps.dto.ConnectMessage;
import com.example.rps.dto.ErrorResponse;
import com.example.rps.dto.MoveMessage;
import com.example.rps.exception.GameNotFoundException;
import com.example.rps.exception.InvalidPlayerActionException;
import com.example.rps.model.MessageType;
import com.example.rps.service.GameService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * Handles the real-time gameplay channel.
 *
 * Client -> Server destinations (prefixed with /app):
 *   /app/game/{gameId}/connect  - bind this WebSocket session to a player role
 *   /app/game/{gameId}/move     - submit a move for the current round
 *   /app/game/{gameId}/reset    - start a fresh 10-round match (Play Again)
 *
 * Server -> Client broadcasts go to /topic/game/{gameId} (see GameService).
 */
@Controller
public class GameWebSocketController {

    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;

    public GameWebSocketController(GameService gameService, SimpMessagingTemplate messagingTemplate) {
        this.gameService = gameService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/game/{gameId}/connect")
    public void connect(@DestinationVariable String gameId, ConnectMessage message,
                         SimpMessageHeaderAccessor headerAccessor) {
        try {
            String sessionId = headerAccessor.getSessionId();
            gameService.registerConnection(gameId.toUpperCase(), message.getPlayer(), sessionId);
        } catch (GameNotFoundException | InvalidPlayerActionException ex) {
            sendError(gameId, ex.getMessage());
        }
    }

    @MessageMapping("/game/{gameId}/move")
    public void submitMove(@DestinationVariable String gameId, MoveMessage message) {
        try {
            gameService.submitMove(gameId.toUpperCase(), message.getPlayer(), message.getMove());
        } catch (GameNotFoundException | InvalidPlayerActionException ex) {
            sendError(gameId, ex.getMessage());
        }
    }

    @MessageMapping("/game/{gameId}/reset")
    public void resetGame(@DestinationVariable String gameId) {
        try {
            gameService.resetGame(gameId.toUpperCase());
        } catch (GameNotFoundException ex) {
            sendError(gameId, ex.getMessage());
        }
    }

    private void sendError(String gameId, String message) {
        messagingTemplate.convertAndSend("/topic/game/" + gameId.toUpperCase(),
                new com.example.rps.dto.GameEvent(MessageType.ERROR, new ErrorResponse("INVALID_ACTION", message)));
    }
}

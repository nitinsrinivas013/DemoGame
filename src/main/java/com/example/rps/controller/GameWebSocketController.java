package com.example.rps.controller;

import com.example.rps.dto.ConnectMessage;
import com.example.rps.dto.ErrorResponse;
import com.example.rps.dto.GameEvent;
import com.example.rps.dto.MoveMessage;
import com.example.rps.exception.GameNotFoundException;
import com.example.rps.exception.InvalidPlayerActionException;
import com.example.rps.model.MessageType;
import com.example.rps.model.Player;
import com.example.rps.service.GameService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * Handles the real-time gameplay channel.
 *
 * Client -> Server:
 *
 * /app/game/{gameId}/connect
 *      Registers/binds the WebSocket session to a player.
 *
 * /app/game/{gameId}/move
 *      Submits a move for the current round.
 *
 * /app/game/{gameId}/reset
 *      Starts a fresh 10-round match.
 *
 * Important:
 * The server uses the WebSocket session ID to identify the player
 * for moves and reset operations. The client-provided player value
 * is only used during the initial connection/binding.
 */
@Controller
public class GameWebSocketController {

    private final GameService gameService;
    private final SimpMessagingTemplate messagingTemplate;

    public GameWebSocketController(
            GameService gameService,
            SimpMessagingTemplate messagingTemplate) {

        this.gameService = gameService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Called when a player establishes their WebSocket connection.
     *
     * The player role is bound to the WebSocket session on the server.
     */
    @MessageMapping("/game/{gameId}/connect")
    public void connect(
            @DestinationVariable String gameId,
            ConnectMessage message,
            SimpMessageHeaderAccessor headerAccessor) {

        try {
            String sessionId = headerAccessor.getSessionId();

            if (sessionId == null) {
                throw new InvalidPlayerActionException(
                        "WebSocket session could not be identified."
                );
            }

            if (message == null || message.getPlayer() == null) {
                throw new InvalidPlayerActionException(
                        "Player is required."
                );
            }

            gameService.registerConnection(
                    gameId.toUpperCase(),
                    message.getPlayer(),
                    sessionId
            );

        } catch (GameNotFoundException | InvalidPlayerActionException ex) {

            sendError(
                    gameId,
                    ex.getMessage()
            );
        }
    }

    /**
     * Handles a player's move.
     *
     * IMPORTANT:
     * We do NOT trust message.getPlayer().
     *
     * The server determines the player from the WebSocket session ID.
     */
    @MessageMapping("/game/{gameId}/move")
    public void submitMove(
            @DestinationVariable String gameId,
            MoveMessage message,
            SimpMessageHeaderAccessor headerAccessor) {

        try {
            String sessionId = headerAccessor.getSessionId();

            if (sessionId == null) {
                throw new InvalidPlayerActionException(
                        "WebSocket session could not be identified."
                );
            }

            if (message == null || message.getMove() == null) {
                throw new InvalidPlayerActionException(
                        "Move is required."
                );
            }

            gameService.submitMove(
                    gameId.toUpperCase(),
                    sessionId,
                    message.getMove()
            );

        } catch (GameNotFoundException | InvalidPlayerActionException ex) {

            sendError(
                    gameId,
                    ex.getMessage()
            );
        }
    }

    /**
     * Handles Play Again / Reset.
     *
     * The server verifies that the WebSocket session belongs
     * to a player in this game.
     */
    @MessageMapping("/game/{gameId}/reset")
    public void resetGame(
            @DestinationVariable String gameId,
            SimpMessageHeaderAccessor headerAccessor) {

        try {
            String sessionId = headerAccessor.getSessionId();

            if (sessionId == null) {
                throw new InvalidPlayerActionException(
                        "WebSocket session could not be identified."
                );
            }

            gameService.resetGame(
                    gameId.toUpperCase(),
                    sessionId
            );

        } catch (GameNotFoundException | InvalidPlayerActionException ex) {

            sendError(
                    gameId,
                    ex.getMessage()
            );
        }
    }

    /**
     * Sends an error to the game topic.
     *
     * This keeps the current application's existing error-handling
     * architecture intact.
     */
    private void sendError(
            String gameId,
            String message) {

        messagingTemplate.convertAndSend(
                "/topic/game/" + gameId.toUpperCase(),
                new GameEvent(
                        MessageType.ERROR,
                        new ErrorResponse(
                                "INVALID_ACTION",
                                message
                        )
                )
        );
    }
}
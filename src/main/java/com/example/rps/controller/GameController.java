package com.example.rps.controller;

import com.example.rps.dto.CreateGameRequest;
import com.example.rps.dto.GameStateResponse;
import com.example.rps.dto.JoinGameRequest;
import com.example.rps.service.GameService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for game lifecycle (create / join / inspect).
 * Real-time gameplay itself happens over the WebSocket/STOMP channel.
 */
@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping
    public ResponseEntity<GameStateResponse> createGame(@Valid @RequestBody CreateGameRequest request) {
        GameStateResponse response = gameService.createGame(request.getPlayer());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{gameId}/join")
    public ResponseEntity<GameStateResponse> joinGame(@PathVariable String gameId,
                                                        @Valid @RequestBody JoinGameRequest request) {
        GameStateResponse response = gameService.joinGame(gameId.toUpperCase(), request.getPlayer());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{gameId}")
    public ResponseEntity<GameStateResponse> getGame(@PathVariable String gameId) {
        GameStateResponse response = gameService.getState(gameId.toUpperCase());
        return ResponseEntity.ok(response);
    }
}

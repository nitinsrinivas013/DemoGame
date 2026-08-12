package com.example.rps.service;

import com.example.rps.dto.*;
import com.example.rps.exception.GameNotFoundException;
import com.example.rps.exception.InvalidPlayerActionException;
import com.example.rps.model.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns all game state and all game rules. This is the single source of truth:
 * the frontend never calculates a winner, a score, or an opponent's move -
 * every one of those is produced here, from the two Move enums that were
 * actually submitted for the round.
 */
@Service
public class GameService {

    private static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I
    private static final int CODE_LENGTH = 6;
    private static final long DISCONNECT_GRACE_PERIOD_MS = 2 * 60 * 1000L; // 2 minutes

    private final Map<String, Game> games = new ConcurrentHashMap<>();
    private final Map<String, Long> disconnectedAt = new ConcurrentHashMap<>(); // key: gameId:PLAYER
    // sessionId -> "gameId:PLAYER", used to resolve STOMP disconnect events back to a game/player.
    private final Map<String, String> sessionIndex = new ConcurrentHashMap<>();

    private final SimpMessagingTemplate messagingTemplate;
    private final SecureRandom random = new SecureRandom();

    public GameService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    // ------------------------------------------------------------------
    // Game creation / joining
    // ------------------------------------------------------------------

    public GameStateResponse createGame(Player creator) {
        String gameId = generateUniqueGameCode();
        Game game = new Game(gameId);
        game.setPlayerJoined(creator, true);
        games.put(gameId, game);
        return GameStateResponse.from(game);
    }

    public GameStateResponse joinGame(String gameId, Player joiner) {
        Game game = getGameOrThrow(gameId);
        synchronized (game) {
            if (game.isPlayerJoined(joiner)) {
                throw new InvalidPlayerActionException(
                        joiner + " has already joined this game. Duplicate roles are not allowed.");
            }
            game.setPlayerJoined(joiner, true);
            if (game.isBothJoined()) {
                game.setStatus(GameStatus.IN_PROGRESS);
            }
            return GameStateResponse.from(game);
        }
    }

    public GameStateResponse getState(String gameId) {
        Game game = getGameOrThrow(gameId);
        return GameStateResponse.from(game);
    }

    // ------------------------------------------------------------------
    // WebSocket session binding (for join broadcasts + disconnect handling)
    // ------------------------------------------------------------------

    public void registerConnection(String gameId, Player player, String sessionId) {
        Game game = getGameOrThrow(gameId);
        synchronized (game) {
            if (!game.isPlayerJoined(player)) {
                throw new InvalidPlayerActionException(player + " has not joined game " + gameId);
            }
            game.setSessionId(player, sessionId);
            game.setPlayerConnected(player, true);
            sessionIndex.put(sessionId, gameId + ":" + player.name());
            disconnectedAt.remove(gameId + ":" + player.name());

            broadcast(gameId, MessageType.PLAYER_JOINED, GameStateResponse.from(game));

            if (game.isBothJoined() && game.isPlayerConnected(Player.NITIN) && game.isPlayerConnected(Player.SARA)) {
                if (game.getStatus() == GameStatus.WAITING_FOR_PLAYER) {
                    game.setStatus(GameStatus.IN_PROGRESS);
                }
                broadcast(gameId, MessageType.GAME_STARTED, GameStateResponse.from(game));
            } else {
                broadcast(gameId, MessageType.WAITING_FOR_OPPONENT, GameStateResponse.from(game));
            }
        }
    }

    // ------------------------------------------------------------------
    // Move submission - the authoritative round engine
    // ------------------------------------------------------------------

    public void submitMove(String gameId, Player player, Move move) {
        if (player == null || move == null) {
            throw new InvalidPlayerActionException("player and move are required");
        }
        Game game = getGameOrThrow(gameId);

        synchronized (game) {
            if (!game.isPlayerJoined(player)) {
                throw new InvalidPlayerActionException(player + " is not part of game " + gameId);
            }
            if (game.getStatus() == GameStatus.FINISHED) {
                throw new InvalidPlayerActionException("This game has already finished after 10 rounds.");
            }
            if (!game.isBothJoined()) {
                throw new InvalidPlayerActionException("Waiting for both players to join before moves can be submitted.");
            }
            if (game.getCurrentRound() > Game.TOTAL_ROUNDS) {
                throw new InvalidPlayerActionException("Round 11 does not exist. The match is over after 10 rounds.");
            }
            if (game.hasMove(player)) {
                throw new InvalidPlayerActionException(player + " has already submitted a move for round " + game.getCurrentRound());
            }

            game.setMove(player, move);

            // Let the opponent (and this player) know a move has been locked in,
            // without revealing what it was.
            broadcast(gameId, MessageType.MOVE_SUBMITTED, Map.of(
                    "player", player.name(),
                    "round", game.getCurrentRound()
            ));

            if (game.bothMovesSubmitted()) {
                processRound(game);
            }
        }
    }

    private void processRound(Game game) {
        Move nitinMove = game.getMove(Player.NITIN);
        Move saraMove = game.getMove(Player.SARA);

        RoundWinner winner = calculateWinner(nitinMove, saraMove);

        if (winner == RoundWinner.NITIN) {
            game.incrementScore(Player.NITIN);
        } else if (winner == RoundWinner.SARA) {
            game.incrementScore(Player.SARA);
        }

        Round round = new Round(game.getCurrentRound(), nitinMove, saraMove, winner);
        game.addRound(round);

        boolean gameFinished = game.getCurrentRound() >= Game.TOTAL_ROUNDS;

        RoundResultPayload result = new RoundResultPayload(
                round.getRoundNumber(), nitinMove, saraMove, winner,
                game.getNitinScore(), game.getSaraScore(), gameFinished
        );

        broadcast(game.getGameId(), MessageType.ROUND_RESULT, result);
        broadcast(game.getGameId(), MessageType.SCORE_UPDATE, Map.of(
                "nitinScore", game.getNitinScore(),
                "saraScore", game.getSaraScore()
        ));

        game.clearMoves();

        if (gameFinished) {
            game.setStatus(GameStatus.FINISHED);
            broadcast(game.getGameId(), MessageType.GAME_FINISHED, FinalResultPayload.from(game));
        } else {
            game.setCurrentRound(game.getCurrentRound() + 1);
            broadcast(game.getGameId(), MessageType.NEXT_ROUND, GameStateResponse.from(game));
        }
    }

    /**
     * The one and only place winners are decided. Pure function of the two
     * submitted moves - no randomness, no hard-coded outcomes.
     */
    public RoundWinner calculateWinner(Move nitinMove, Move saraMove) {
        if (nitinMove == saraMove) {
            return RoundWinner.DRAW;
        }
        if (nitinMove.beats(saraMove)) {
            return RoundWinner.NITIN;
        }
        return RoundWinner.SARA;
    }

    // ------------------------------------------------------------------
    // Play again
    // ------------------------------------------------------------------

    public GameStateResponse resetGame(String gameId) {
        Game game = getGameOrThrow(gameId);
        synchronized (game) {
            game.getRoundHistory().clear();
            game.clearMoves();
            game.setCurrentRound(1);
            // scores can only be reset via a brand-new Game object's fields; since
            // Game has no public score setter (scores only ever increment), we
            // replace the game state in place using a fresh Game that preserves membership.
            Game fresh = new Game(gameId);
            fresh.setPlayerJoined(Player.NITIN, game.isPlayerJoined(Player.NITIN));
            fresh.setPlayerJoined(Player.SARA, game.isPlayerJoined(Player.SARA));
            fresh.setPlayerConnected(Player.NITIN, game.isPlayerConnected(Player.NITIN));
            fresh.setPlayerConnected(Player.SARA, game.isPlayerConnected(Player.SARA));
            fresh.setSessionId(Player.NITIN, game.getSessionId(Player.NITIN));
            fresh.setSessionId(Player.SARA, game.getSessionId(Player.SARA));
            fresh.setStatus(fresh.isBothJoined() ? GameStatus.IN_PROGRESS : GameStatus.WAITING_FOR_PLAYER);
            games.put(gameId, fresh);

            broadcast(gameId, MessageType.GAME_RESET, GameStateResponse.from(fresh));
            return GameStateResponse.from(fresh);
        }
    }

    // ------------------------------------------------------------------
    // Disconnect / reconnect handling
    // ------------------------------------------------------------------

    public void handleSessionDisconnect(String sessionId) {
        String key = sessionIndex.remove(sessionId);
        if (key == null) {
            return;
        }
        String[] parts = key.split(":");
        String gameId = parts[0];
        Player player = Player.valueOf(parts[1]);

        Game game = games.get(gameId);
        if (game == null) {
            return;
        }
        synchronized (game) {
            // Ignore stale disconnects from a session that has already been replaced
            // by a newer reconnect for the same player.
            if (!sessionId.equals(game.getSessionId(player))) {
                return;
            }
            game.setPlayerConnected(player, false);
            disconnectedAt.put(gameId + ":" + player.name(), Instant.now().toEpochMilli());
            broadcast(gameId, MessageType.PLAYER_DISCONNECTED, Map.of(
                    "player", player.name(),
                    "gracePeriodSeconds", DISCONNECT_GRACE_PERIOD_MS / 1000
            ));
        }
    }

    /** Periodically evicts games where a disconnected player never returned. */
    @Scheduled(fixedDelay = 30_000)
    public void cleanupAbandonedGames() {
        long now = Instant.now().toEpochMilli();
        disconnectedAt.forEach((key, disconnectTime) -> {
            if (now - disconnectTime > DISCONNECT_GRACE_PERIOD_MS) {
                String[] parts = key.split(":");
                String gameId = parts[0];
                Game game = games.get(gameId);
                if (game != null) {
                    synchronized (game) {
                        if (!game.isPlayerConnected(Player.valueOf(parts[1]))) {
                            broadcast(gameId, MessageType.PLAYER_DISCONNECTED, Map.of(
                                    "player", parts[1],
                                    "abandoned", true
                            ));
                        }
                    }
                }
                disconnectedAt.remove(key);
            }
        });
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Game getGameOrThrow(String gameId) {
        Game game = games.get(gameId);
        if (game == null) {
            throw new GameNotFoundException(gameId);
        }
        return game;
    }

    private String generateUniqueGameCode() {
        String code;
        do {
            code = generateGameCode();
        } while (games.containsKey(code));
        return code;
    }

    private String generateGameCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    private void broadcast(String gameId, MessageType type, Object payload) {
        messagingTemplate.convertAndSend("/topic/game/" + gameId, GameEvent.of(type, payload));
    }

    /** Exposed for tests: whether a game exists. */
    public boolean gameExists(String gameId) {
        return games.containsKey(gameId);
    }

    /** Exposed for tests / controller: fetch the raw game (read-only usage expected). */
    public Game getGame(String gameId) {
        return getGameOrThrow(gameId);
    }
}

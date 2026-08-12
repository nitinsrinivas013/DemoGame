package com.example.rps.service;

import com.example.rps.dto.ErrorResponse;
import com.example.rps.dto.FinalResultPayload;
import com.example.rps.dto.GameEvent;
import com.example.rps.dto.GameStateResponse;
import com.example.rps.dto.RoundResultPayload;
import com.example.rps.exception.GameNotFoundException;
import com.example.rps.exception.InvalidPlayerActionException;
import com.example.rps.model.Game;
import com.example.rps.model.GameStatus;
import com.example.rps.model.MessageType;
import com.example.rps.model.Move;
import com.example.rps.model.Player;
import com.example.rps.model.Round;
import com.example.rps.model.RoundWinner;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns all game state and all game rules.
 *
 * The server is the single source of truth.
 *
 * The frontend never calculates:
 * - winner
 * - score
 * - opponent's move
 * - current authoritative game state
 *
 * The server determines all of these.
 */
@Service
public class GameService {

    private static final String CODE_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private static final int CODE_LENGTH = 6;

    private static final long DISCONNECT_GRACE_PERIOD_MS =
            2 * 60 * 1000L;

    /**
     * gameId -> Game
     */
    private final Map<String, Game> games =
            new ConcurrentHashMap<>();

    /**
     * gameId:PLAYER -> disconnect timestamp
     */
    private final Map<String, Long> disconnectedAt =
            new ConcurrentHashMap<>();

    /**
     * WebSocket session ID -> "gameId:PLAYER"
     *
     * This is extremely important.
     *
     * Example:
     *
     * session123 -> ABC123:NITIN
     * session456 -> ABC123:SARA
     */
    private final Map<String, String> sessionIndex =
            new ConcurrentHashMap<>();

    private final SimpMessagingTemplate messagingTemplate;

    private final SecureRandom random =
            new SecureRandom();

    public GameService(
            SimpMessagingTemplate messagingTemplate) {

        this.messagingTemplate = messagingTemplate;
    }

    // ============================================================
    // GAME CREATION / JOINING
    // ============================================================

    /**
     * Creates a new game.
     *
     * The creator is marked as joined but the game does NOT
     * become IN_PROGRESS yet.
     *
     * The game only starts after both WebSocket connections
     * are established.
     */
    public GameStateResponse createGame(Player creator) {

        String gameId = generateUniqueGameCode();

        Game game = new Game(gameId);

        game.setPlayerJoined(creator, true);

        games.put(gameId, game);

        return GameStateResponse.from(game);
    }

    /**
     * Adds the second player to an existing game.
     *
     * IMPORTANT:
     * Joining through REST does NOT start the game.
     *
     * Both WebSocket connections must be established before
     * the game becomes IN_PROGRESS.
     */
    public GameStateResponse joinGame(
            String gameId,
            Player joiner) {

        Game game = getGameOrThrow(gameId);

        synchronized (game) {

            if (game.isPlayerJoined(joiner)) {

                throw new InvalidPlayerActionException(
                        joiner
                                + " has already joined this game. "
                                + "Duplicate roles are not allowed."
                );
            }

            if (game.getStatus() == GameStatus.FINISHED) {

                throw new InvalidPlayerActionException(
                        "This game has already finished."
                );
            }

            game.setPlayerJoined(joiner, true);

            /*
             * DO NOT set IN_PROGRESS here.
             *
             * The game starts only after both WebSocket
             * connections are established.
             */

            return GameStateResponse.from(game);
        }
    }

    /**
     * Returns the current server-side game state.
     */
    public GameStateResponse getState(String gameId) {

        Game game = getGameOrThrow(gameId);

        synchronized (game) {
            return GameStateResponse.from(game);
        }
    }

    // ============================================================
    // WEBSOCKET SESSION BINDING
    // ============================================================

    /**
     * Registers a WebSocket connection for a player.
     *
     * The initial player role comes from the connection message.
     * Once registered, the sessionIndex becomes authoritative.
     */
    public void registerConnection(
            String gameId,
            Player player,
            String sessionId) {

        if (player == null) {

            throw new InvalidPlayerActionException(
                    "Player is required."
            );
        }

        if (sessionId == null || sessionId.isBlank()) {

            throw new InvalidPlayerActionException(
                    "WebSocket session could not be identified."
            );
        }

        Game game = getGameOrThrow(gameId);

        synchronized (game) {

            if (!game.isPlayerJoined(player)) {

                throw new InvalidPlayerActionException(
                        player
                                + " has not joined game "
                                + gameId
                );
            }

            /*
             * Prevent two active browser sessions from claiming
             * the same player role.
             */
            String existingSession =
                    game.getSessionId(player);

            if (game.isPlayerConnected(player)
                    && existingSession != null
                    && !existingSession.equals(sessionId)) {

                throw new InvalidPlayerActionException(
                        player
                                + " already has an active connection."
                );
            }

            /*
             * Bind this WebSocket session to the player.
             */
            game.setSessionId(player, sessionId);

            game.setPlayerConnected(
                    player,
                    true
            );

            sessionIndex.put(
                    sessionId,
                    gameId + ":" + player.name()
            );

            disconnectedAt.remove(
                    gameId + ":" + player.name()
            );

            /*
             * Tell both clients that the player connected.
             */
            broadcast(
                    gameId,
                    MessageType.PLAYER_JOINED,
                    GameStateResponse.from(game)
            );

            /*
             * Both players are now connected.
             */
            boolean bothPlayersConnected =
                    game.isBothJoined()
                            && game.isPlayerConnected(Player.NITIN)
                            && game.isPlayerConnected(Player.SARA);

            if (!bothPlayersConnected) {

                broadcast(
                        gameId,
                        MessageType.WAITING_FOR_OPPONENT,
                        GameStateResponse.from(game)
                );

                return;
            }

            /*
             * Only transition into IN_PROGRESS once.
             *
             * This prevents reconnecting from starting the
             * game again.
             */
            if (game.getStatus()
                    == GameStatus.WAITING_FOR_PLAYER) {

                game.setStatus(
                        GameStatus.IN_PROGRESS
                );

                broadcast(
                        gameId,
                        MessageType.GAME_STARTED,
                        GameStateResponse.from(game)
                );

            } else {

                /*
                 * This is a reconnect.
                 *
                 * Do NOT send GAME_STARTED again.
                 * Just send the current authoritative state.
                 */
                broadcast(
                        gameId,
                        MessageType.GAME_STATE,
                        GameStateResponse.from(game)
                );
            }
        }
    }

    // ============================================================
    // MOVE SUBMISSION
    // ============================================================

    /**
     * Submit a move using the WebSocket session ID.
     *
     * The player is NOT taken from the client's MoveMessage.
     *
     * The server determines:
     *
     * sessionId -> gameId -> player
     */
    public void submitMove(
            String gameId,
            String sessionId,
            Move move) {

        if (sessionId == null || sessionId.isBlank()) {

            throw new InvalidPlayerActionException(
                    "WebSocket session could not be identified."
            );
        }

        if (move == null) {

            throw new InvalidPlayerActionException(
                    "Move is required."
            );
        }

        Game game = getGameOrThrow(gameId);

        synchronized (game) {

            /*
             * Resolve the actual player from the server-side
             * WebSocket session mapping.
             */
            Player player =
                    getPlayerForSession(
                            gameId,
                            sessionId
                    );

            if (!game.isPlayerJoined(player)) {

                throw new InvalidPlayerActionException(
                        player
                                + " is not part of game "
                                + gameId
                );
            }

            if (!game.isPlayerConnected(player)) {

                throw new InvalidPlayerActionException(
                        player
                                + " is not currently connected."
                );
            }

            if (game.getStatus()
                    != GameStatus.IN_PROGRESS) {

                throw new InvalidPlayerActionException(
                        "The game is not currently accepting moves."
                );
            }

            if (!game.isBothJoined()
                    || !game.isPlayerConnected(Player.NITIN)
                    || !game.isPlayerConnected(Player.SARA)) {

                throw new InvalidPlayerActionException(
                        "Both players must be connected before "
                                + "moves can be submitted."
                );
            }

            if (game.getCurrentRound()
                    > Game.TOTAL_ROUNDS) {

                throw new InvalidPlayerActionException(
                        "The match is already finished."
                );
            }

            /*
             * A player gets exactly one move per round.
             */
            if (game.hasMove(player)) {

                throw new InvalidPlayerActionException(
                        player
                                + " has already submitted a move "
                                + "for round "
                                + game.getCurrentRound()
                );
            }

            /*
             * Store the actual server-authorized move.
             */


            game.setMove(
                    player,
                    move
            );

            /*
             * Tell both players that this player has submitted.
             *
             * IMPORTANT:
             * The actual move is NOT revealed here.
             */
            broadcast(
                    gameId,
                    MessageType.MOVE_SUBMITTED,
                    Map.of(
                            "player",
                            player.name(),

                            "round",
                            game.getCurrentRound()
                    )
            );

            /*
             * The round is resolved ONLY when both players
             * have explicitly submitted a move.
             */


            if (game.bothMovesSubmitted()) {

                processRound(game);
            }
        }
    }

    /**
     * Resolves exactly one round after both moves exist.
     */
    private void processRound(Game game) {

        Move nitinMove =
                game.getMove(Player.NITIN);

        Move saraMove =
                game.getMove(Player.SARA);

        /*
         * Defensive protection.
         *
         * This should never happen because submitMove()
         * checks bothMovesSubmitted(), but keep this guard
         * so processRound() can never calculate a result
         * with a missing move.
         */
        if (nitinMove == null
                || saraMove == null) {

            throw new IllegalStateException(
                    "Cannot process round without both moves."
            );
        }

        RoundWinner winner =
                calculateWinner(
                        nitinMove,
                        saraMove
                );

        /*
         * Update score.
         */
        if (winner == RoundWinner.NITIN) {

            game.incrementScore(
                    Player.NITIN
            );

        } else if (winner == RoundWinner.SARA) {

            game.incrementScore(
                    Player.SARA
            );
        }

        /*
         * Add the completed round to history.
         */
        Round round =
                new Round(
                        game.getCurrentRound(),
                        nitinMove,
                        saraMove,
                        winner
                );

        game.addRound(round);

        boolean gameFinished =
                game.getCurrentRound()
                        >= Game.TOTAL_ROUNDS;

        /*
         * Reveal both moves only after both players
         * have submitted.
         */
        RoundResultPayload result =
                new RoundResultPayload(
                        round.getRoundNumber(),
                        nitinMove,
                        saraMove,
                        winner,
                        game.getNitinScore(),
                        game.getSaraScore(),
                        gameFinished
                );

        broadcast(
                game.getGameId(),
                MessageType.ROUND_RESULT,
                result
        );

        /*
         * Send authoritative scores.
         */
        broadcast(
                game.getGameId(),
                MessageType.SCORE_UPDATE,
                Map.of(
                        "nitinScore",
                        game.getNitinScore(),

                        "saraScore",
                        game.getSaraScore()
                )
        );

        /*
         * Clear the current round's moves.
         */
        game.clearMoves();

        if (gameFinished) {

            game.setStatus(
                    GameStatus.FINISHED
            );

            broadcast(
                    game.getGameId(),
                    MessageType.GAME_FINISHED,
                    FinalResultPayload.from(game)
            );

        } else {

            /*
             * Advance exactly one round.
             */
            game.setCurrentRound(
                    game.getCurrentRound() + 1
            );

            broadcast(
                    game.getGameId(),
                    MessageType.NEXT_ROUND,
                    GameStateResponse.from(game)
            );
        }
    }

    // ============================================================
    // RPS WINNER CALCULATION
    // ============================================================

    /**
     * The only place where the winner is calculated.
     *
     * Completely legitimate Rock Paper Scissors rules.
     */
    public RoundWinner calculateWinner(
            Move nitinMove,
            Move saraMove) {

        if (nitinMove == saraMove) {

            return RoundWinner.DRAW;
        }

        if (nitinMove.beats(saraMove)) {

            return RoundWinner.NITIN;
        }

        return RoundWinner.SARA;
    }

    // ============================================================
    // PLAY AGAIN
    // ============================================================

    /**
     * Resets the match.
     *
     * The caller must be an active player in the game.
     */
    public GameStateResponse resetGame(
            String gameId,
            String sessionId) {

        Game game = getGameOrThrow(gameId);

        synchronized (game) {

            /*
             * Verify that this session belongs to a player
             * in this game.
             */
            Player player =
                    getPlayerForSession(
                            gameId,
                            sessionId
                    );

            if (!game.isPlayerJoined(player)) {

                throw new InvalidPlayerActionException(
                        "You are not a player in this game."
                );
            }

            /*
             * Preserve membership and WebSocket connections.
             *
             * Create a fresh Game state, just as the original
             * implementation did, but keep the session mappings.
             */
            Game fresh =
                    new Game(gameId);

            fresh.setPlayerJoined(
                    Player.NITIN,
                    game.isPlayerJoined(Player.NITIN)
            );

            fresh.setPlayerJoined(
                    Player.SARA,
                    game.isPlayerJoined(Player.SARA)
            );

            fresh.setPlayerConnected(
                    Player.NITIN,
                    game.isPlayerConnected(Player.NITIN)
            );

            fresh.setPlayerConnected(
                    Player.SARA,
                    game.isPlayerConnected(Player.SARA)
            );

            fresh.setSessionId(
                    Player.NITIN,
                    game.getSessionId(Player.NITIN)
            );

            fresh.setSessionId(
                    Player.SARA,
                    game.getSessionId(Player.SARA)
            );

            /*
             * If both players are still connected, the new game
             * can immediately be IN_PROGRESS.
             */
            if (fresh.isBothJoined()
                    && fresh.isPlayerConnected(Player.NITIN)
                    && fresh.isPlayerConnected(Player.SARA)) {

                fresh.setStatus(
                        GameStatus.IN_PROGRESS
                );

            } else {

                fresh.setStatus(
                        GameStatus.WAITING_FOR_PLAYER
                );
            }

            games.put(
                    gameId,
                    fresh
            );

            broadcast(
                    gameId,
                    MessageType.GAME_RESET,
                    GameStateResponse.from(fresh)
            );

            return GameStateResponse.from(fresh);
        }
    }

    // ============================================================
    // SESSION LOOKUP
    // ============================================================

    /**
     * Resolves a WebSocket session ID to the player who owns it.
     *
     * Example:
     *
     * sessionABC -> ABC123:NITIN
     *
     * therefore:
     *
     * sessionABC -> NITIN
     */
    private Player getPlayerForSession(
            String gameId,
            String sessionId) {

        String key =
                sessionIndex.get(sessionId);

        if (key == null) {

            throw new InvalidPlayerActionException(
                    "WebSocket session is not registered."
            );
        }

        String expectedPrefix =
                gameId + ":";

        if (!key.startsWith(expectedPrefix)) {

            throw new InvalidPlayerActionException(
                    "WebSocket session does not belong "
                            + "to this game."
            );
        }

        String playerName =
                key.substring(
                        expectedPrefix.length()
                );

        try {

            return Player.valueOf(
                    playerName
            );

        } catch (IllegalArgumentException ex) {

            throw new InvalidPlayerActionException(
                    "Invalid player session."
            );
        }
    }

    // ============================================================
    // DISCONNECT / RECONNECT
    // ============================================================

    /**
     * Handles a WebSocket disconnect.
     */
    public void handleSessionDisconnect(
            String sessionId) {

        String key =
                sessionIndex.remove(sessionId);

        if (key == null) {
            return;
        }

        String[] parts =
                key.split(":");

        if (parts.length != 2) {
            return;
        }

        String gameId =
                parts[0];

        Player player;

        try {

            player =
                    Player.valueOf(parts[1]);

        } catch (IllegalArgumentException ex) {

            return;
        }

        Game game =
                games.get(gameId);

        if (game == null) {
            return;
        }

        synchronized (game) {

            /*
             * Ignore an old disconnect event if this player
             * already reconnected with a newer session.
             */
            if (!sessionId.equals(
                    game.getSessionId(player))) {

                return;
            }

            game.setPlayerConnected(
                    player,
                    false
            );

            disconnectedAt.put(
                    gameId + ":" + player.name(),
                    Instant.now().toEpochMilli()
            );

            broadcast(
                    gameId,
                    MessageType.PLAYER_DISCONNECTED,
                    Map.of(
                            "player",
                            player.name(),

                            "gracePeriodSeconds",
                            DISCONNECT_GRACE_PERIOD_MS / 1000
                    )
            );
        }
    }

    /**
     * Periodically evicts games where a disconnected player
     * never returned.
     */
    @Scheduled(fixedDelay = 30_000)
    public void cleanupAbandonedGames() {

        long now =
                Instant.now().toEpochMilli();

        disconnectedAt.forEach(
                (key, disconnectTime) -> {

                    if (now - disconnectTime
                            > DISCONNECT_GRACE_PERIOD_MS) {

                        String[] parts =
                                key.split(":");

                        if (parts.length != 2) {
                            disconnectedAt.remove(key);
                            return;
                        }

                        String gameId =
                                parts[0];

                        Game game =
                                games.get(gameId);

                        if (game != null) {

                            synchronized (game) {

                                Player player;

                                try {

                                    player =
                                            Player.valueOf(parts[1]);

                                } catch (IllegalArgumentException ex) {

                                    disconnectedAt.remove(key);
                                    return;
                                }

                                if (!game.isPlayerConnected(player)) {

                                    broadcast(
                                            gameId,
                                            MessageType.PLAYER_DISCONNECTED,
                                            Map.of(
                                                    "player",
                                                    player.name(),

                                                    "abandoned",
                                                    true
                                            )
                                    );
                                }
                            }
                        }

                        disconnectedAt.remove(key);
                    }
                }
        );
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private Game getGameOrThrow(
            String gameId) {

        Game game =
                games.get(gameId);

        if (game == null) {

            throw new GameNotFoundException(
                    gameId
            );
        }

        return game;
    }

    private String generateUniqueGameCode() {

        String code;

        do {

            code =
                    generateGameCode();

        } while (games.containsKey(code));

        return code;
    }

    private String generateGameCode() {

        StringBuilder sb =
                new StringBuilder(
                        CODE_LENGTH
                );

        for (int i = 0;
             i < CODE_LENGTH;
             i++) {

            sb.append(
                    CODE_ALPHABET.charAt(
                            random.nextInt(
                                    CODE_ALPHABET.length()
                            )
                    )
            );
        }

        return sb.toString();
    }

    private void broadcast(
            String gameId,
            MessageType type,
            Object payload) {

        messagingTemplate.convertAndSend(
                "/topic/game/" + gameId,
                GameEvent.of(
                        type,
                        payload
                )
        );
    }

    /**
     * Exposed for tests.
     */
    public boolean gameExists(
            String gameId) {

        return games.containsKey(gameId);
    }

    /**
     * Exposed for tests / controller.
     */
    public Game getGame(
            String gameId) {

        return getGameOrThrow(gameId);
    }
}
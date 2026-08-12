package com.example.rps.service;

import com.example.rps.dto.GameStateResponse;
import com.example.rps.exception.GameNotFoundException;
import com.example.rps.exception.InvalidPlayerActionException;
import com.example.rps.model.Game;
import com.example.rps.model.GameStatus;
import com.example.rps.model.Move;
import com.example.rps.model.Player;
import com.example.rps.model.RoundWinner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class GameServiceTest {

    private GameService gameService;

    @BeforeEach
    void setUp() {
        // SimpMessagingTemplate is mocked: these are pure unit tests of game
        // rules/state, not of the WebSocket transport.
        gameService = new GameService(mock(SimpMessagingTemplate.class));
    }

    private String createAndJoinGame() {
        GameStateResponse created = gameService.createGame(Player.NITIN);
        gameService.joinGame(created.getGameId(), Player.SARA);
        return created.getGameId();
    }

    // ------------------------------------------------------------------
    // Winner calculation - normal RPS rules
    // ------------------------------------------------------------------

    @Test
    void rockBeatsScissors() {
        assertEquals(RoundWinner.NITIN, gameService.calculateWinner(Move.ROCK, Move.SCISSORS));
    }

    @Test
    void scissorsBeatsPaper() {
        assertEquals(RoundWinner.NITIN, gameService.calculateWinner(Move.SCISSORS, Move.PAPER));
    }

    @Test
    void paperBeatsRock() {
        assertEquals(RoundWinner.NITIN, gameService.calculateWinner(Move.PAPER, Move.ROCK));
    }

    @Test
    void scissorsLosesToRock() {
        assertEquals(RoundWinner.SARA, gameService.calculateWinner(Move.SCISSORS, Move.ROCK));
    }

    @Test
    void paperLosesToScissors() {
        assertEquals(RoundWinner.SARA, gameService.calculateWinner(Move.PAPER, Move.SCISSORS));
    }

    @Test
    void rockLosesToPaper() {
        assertEquals(RoundWinner.SARA, gameService.calculateWinner(Move.ROCK, Move.PAPER));
    }

    @Test
    void rockVsRockIsDraw() {
        assertEquals(RoundWinner.DRAW, gameService.calculateWinner(Move.ROCK, Move.ROCK));
    }

    @Test
    void paperVsPaperIsDraw() {
        assertEquals(RoundWinner.DRAW, gameService.calculateWinner(Move.PAPER, Move.PAPER));
    }

    @Test
    void scissorsVsScissorsIsDraw() {
        assertEquals(RoundWinner.DRAW, gameService.calculateWinner(Move.SCISSORS, Move.SCISSORS));
    }

    // ------------------------------------------------------------------
    // Game creation / joining
    // ------------------------------------------------------------------

    @Test
    void creatingGameClaimsCreatorRole() {
        GameStateResponse response = gameService.createGame(Player.NITIN);
        assertTrue(response.isNitinJoined());
        assertFalse(response.isSaraJoined());
        assertEquals(GameStatus.WAITING_FOR_PLAYER, response.getStatus());
    }

    @Test
    void secondPlayerCanJoinExistingGame() {
        GameStateResponse created = gameService.createGame(Player.NITIN);
        GameStateResponse joined = gameService.joinGame(created.getGameId(), Player.SARA);
        assertTrue(joined.isNitinJoined());
        assertTrue(joined.isSaraJoined());
        assertEquals(GameStatus.IN_PROGRESS, joined.getStatus());
    }

    @Test
    void cannotHaveTwoNitinPlayers() {
        GameStateResponse created = gameService.createGame(Player.NITIN);
        assertThrows(InvalidPlayerActionException.class,
                () -> gameService.joinGame(created.getGameId(), Player.NITIN));
    }

    @Test
    void cannotHaveTwoSaraPlayers() {
        GameStateResponse created = gameService.createGame(Player.SARA);
        assertThrows(InvalidPlayerActionException.class,
                () -> gameService.joinGame(created.getGameId(), Player.SARA));
    }

    @Test
    void joiningNonexistentGameThrows() {
        assertThrows(GameNotFoundException.class, () -> gameService.joinGame("NOPE99", Player.SARA));
    }

    // ------------------------------------------------------------------
    // Move submission identity rules
    // ------------------------------------------------------------------

    @Test
    void nitinCanSubmitNitinsMove() {
        String gameId = createAndJoinGame();
        assertDoesNotThrow(() -> gameService.submitMove(gameId, Player.NITIN, Move.ROCK));
    }

    @Test
    void saraCanSubmitSarasMove() {
        String gameId = createAndJoinGame();
        assertDoesNotThrow(() -> gameService.submitMove(gameId, Player.SARA, Move.PAPER));
    }

    @Test
    void aPlayerCannotSubmitTwiceInSameRound() {
        String gameId = createAndJoinGame();
        gameService.submitMove(gameId, Player.NITIN, Move.ROCK);
        assertThrows(InvalidPlayerActionException.class,
                () -> gameService.submitMove(gameId, Player.NITIN, Move.PAPER));
    }

    @Test
    void roundCannotAcceptMovesAfterCompletion() {
        String gameId = createAndJoinGame();
        gameService.submitMove(gameId, Player.NITIN, Move.ROCK);
        gameService.submitMove(gameId, Player.SARA, Move.SCISSORS); // completes round 1

        // Round 1 is over; a further NITIN move now belongs to round 2, which is legal.
        // But resubmitting the *same completed* round is impossible since moves are
        // cleared - verify instead that round 2 accepts exactly one move per player.
        gameService.submitMove(gameId, Player.NITIN, Move.PAPER);
        assertThrows(InvalidPlayerActionException.class,
                () -> gameService.submitMove(gameId, Player.NITIN, Move.ROCK));
    }

    @Test
    void movesCannotBeSubmittedBeforeBothPlayersJoin() {
        GameStateResponse created = gameService.createGame(Player.NITIN);
        assertThrows(InvalidPlayerActionException.class,
                () -> gameService.submitMove(created.getGameId(), Player.NITIN, Move.ROCK));
    }

    // ------------------------------------------------------------------
    // 10-round structure
    // ------------------------------------------------------------------

    @Test
    void gameHasExactlyTenRounds() {
        String gameId = playFullGame();
        Game game = gameService.getGame(gameId);
        assertEquals(10, game.getRoundHistory().size());
        assertEquals(GameStatus.FINISHED, game.getStatus());
    }

    @Test
    void round11CannotBePlayed() {
        String gameId = playFullGame();
        assertThrows(InvalidPlayerActionException.class,
                () -> gameService.submitMove(gameId, Player.NITIN, Move.ROCK));
    }

    @Test
    void scoresAreCalculatedCorrectly() {
        String gameId = createAndJoinGame();
        // Round 1: Nitin ROCK beats Sara SCISSORS -> Nitin +1
        gameService.submitMove(gameId, Player.NITIN, Move.ROCK);
        gameService.submitMove(gameId, Player.SARA, Move.SCISSORS);
        // Round 2: draw -> no score change
        gameService.submitMove(gameId, Player.NITIN, Move.PAPER);
        gameService.submitMove(gameId, Player.SARA, Move.PAPER);
        // Round 3: Sara SCISSORS beats Nitin PAPER -> Sara +1
        gameService.submitMove(gameId, Player.NITIN, Move.PAPER);
        gameService.submitMove(gameId, Player.SARA, Move.SCISSORS);

        Game game = gameService.getGame(gameId);
        assertEquals(1, game.getNitinScore());
        assertEquals(1, game.getSaraScore());
        assertEquals(3, game.getRoundHistory().size());
    }

    @Test
    void finalWinnerIsCalculatedFromActualRounds() {
        String gameId = createAndJoinGame();
        // Nitin wins all 10 rounds with ROCK vs SCISSORS
        for (int i = 0; i < 10; i++) {
            gameService.submitMove(gameId, Player.NITIN, Move.ROCK);
            gameService.submitMove(gameId, Player.SARA, Move.SCISSORS);
        }
        Game game = gameService.getGame(gameId);
        assertEquals(10, game.getNitinScore());
        assertEquals(0, game.getSaraScore());
        assertEquals(GameStatus.FINISHED, game.getStatus());
        long nitinWins = game.getRoundHistory().stream().filter(r -> r.getWinner() == RoundWinner.NITIN).count();
        assertEquals(10, nitinWins);
    }

    @Test
    void playAgainResetsScoreAndRoundsButKeepsMembership() {
        String gameId = playFullGame();
        GameStateResponse reset = gameService.resetGame(gameId);
        assertEquals(0, reset.getNitinScore());
        assertEquals(0, reset.getSaraScore());
        assertEquals(1, reset.getCurrentRound());
        assertTrue(reset.getRoundHistory().isEmpty());
        assertTrue(reset.isNitinJoined());
        assertTrue(reset.isSaraJoined());
    }

    private String playFullGame() {
        String gameId = createAndJoinGame();
        for (int i = 0; i < 10; i++) {
            gameService.submitMove(gameId, Player.NITIN, Move.ROCK);
            gameService.submitMove(gameId, Player.SARA, Move.SCISSORS);
        }
        return gameId;
    }
}

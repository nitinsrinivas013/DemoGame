(() => {
  'use strict';

  // ------------------------------------------------------------------
  // State
  // ------------------------------------------------------------------
  const STORAGE_KEY = 'rps.session';

  const state = {
    gameId: null,
    player: null,      // 'NITIN' | 'SARA'
    opponent: null,     // the other role
    stompClient: null,
    connected: false,
    moveLockedThisRound: false,
    resultTimer: null,
  };

  const OPPONENT_OF = { NITIN: 'SARA', SARA: 'NITIN' };
  const DISPLAY_NAME = { NITIN: 'Nitin', SARA: 'Sara' };
  const MOVE_EMOJI = { ROCK: '🪨', PAPER: '📄', SCISSORS: '✂️' };

  // ------------------------------------------------------------------
  // DOM refs
  // ------------------------------------------------------------------
  const screens = {
    landing: document.getElementById('screen-landing'),
    lobby: document.getElementById('screen-lobby'),
    waiting: document.getElementById('screen-waiting'),
    game: document.getElementById('screen-game'),
    final: document.getElementById('screen-final'),
  };

  const el = {
    lobbyPlayerName: document.getElementById('lobby-player-name'),
    lobbyError: document.getElementById('lobby-error'),
    btnCreateGame: document.getElementById('btn-create-game'),
    formJoinGame: document.getElementById('form-join-game'),
    inputGameCode: document.getElementById('input-game-code'),
    btnBackLanding: document.getElementById('btn-back-landing'),

    waitingGameCode: document.getElementById('waiting-game-code'),
    btnCopyCode: document.getElementById('btn-copy-code'),
    copyFeedback: document.getElementById('copy-feedback'),

    roundCurrent: document.getElementById('round-current'),
    roundTotal: document.getElementById('round-total'),
    scoreNitin: document.getElementById('score-nitin'),
    scoreSara: document.getElementById('score-sara'),
    opponentBanner: document.getElementById('opponent-status-banner'),

    movePrompt: document.getElementById('move-prompt'),
    moveButtons: Array.from(document.querySelectorAll('.move-btn')),
    waitingNote: document.getElementById('waiting-note'),
    myMoveDisplay: document.getElementById('my-move-display'),
    opponentNameLabel: document.getElementById('opponent-name-label'),

    resultOverlay: document.getElementById('result-overlay'),
    resultRoundLabel: document.getElementById('result-round-label'),
    resultNitinEmoji: document.getElementById('result-nitin-emoji'),
    resultSaraEmoji: document.getElementById('result-sara-emoji'),
    resultHeading: document.getElementById('result-heading'),
    resultScoreNitin: document.getElementById('result-score-nitin'),
    resultScoreSara: document.getElementById('result-score-sara'),
    resultNextNote: document.getElementById('result-next-note'),

    finalNitinScore: document.getElementById('final-nitin-score'),
    finalSaraScore: document.getElementById('final-sara-score'),
    finalWinnerLine: document.getElementById('final-winner-line'),
    finalNitinWins: document.getElementById('final-nitin-wins'),
    finalSaraWins: document.getElementById('final-sara-wins'),
    finalDraws: document.getElementById('final-draws'),
    historyList: document.getElementById('history-list'),
    btnPlayAgain: document.getElementById('btn-play-again'),

    toast: document.getElementById('toast'),
  };

  // ------------------------------------------------------------------
  // Screen management
  // ------------------------------------------------------------------
  function showScreen(name) {
    Object.entries(screens).forEach(([key, node]) => {
      node.classList.toggle('active', key === name);
    });
  }

  let toastTimer = null;
  function showToast(message) {
    el.toast.textContent = message;
    el.toast.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { el.toast.hidden = true; }, 3500);
  }

  // ------------------------------------------------------------------
  // Session persistence (survives a page refresh so a player can rejoin
  // their own in-progress game automatically)
  // ------------------------------------------------------------------
  function saveSession() {
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify({ gameId: state.gameId, player: state.player }));
    } catch (e) { /* storage unavailable - non-fatal */ }
  }

  function clearSession() {
    try { sessionStorage.removeItem(STORAGE_KEY); } catch (e) { /* ignore */ }
  }

  function loadSession() {
    try {
      const raw = sessionStorage.getItem(STORAGE_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch (e) {
      return null;
    }
  }

  // ------------------------------------------------------------------
  // REST helpers
  // ------------------------------------------------------------------
  async function apiRequest(path, options) {
    const res = await fetch(path, {
      headers: { 'Content-Type': 'application/json' },
      ...options,
    });
    const body = await res.json().catch(() => null);
    if (!res.ok) {
      const message = (body && body.message) || 'Something went wrong. Please try again.';
      throw new Error(message);
    }
    return body;
  }

  function createGame(player) {
    return apiRequest('/api/games', { method: 'POST', body: JSON.stringify({ player }) });
  }

  function joinGame(gameId, player) {
    return apiRequest(`/api/games/${gameId}/join`, { method: 'POST', body: JSON.stringify({ player }) });
  }

  function fetchGameState(gameId) {
    return apiRequest(`/api/games/${gameId}`, { method: 'GET' });
  }

  // ------------------------------------------------------------------
  // WebSocket / STOMP
  // ------------------------------------------------------------------
  function connectWebSocket() {
    const protocol = window.location.protocol === 'https:' ? 'wss://' : 'ws://';
    const socket = new WebSocket(protocol + window.location.host + '/ws');
    const client = Stomp.over(socket);
    client.debug = null; // silence verbose STOMP frame logging

    client.connect({}, () => {

      console.log(
          "========== WEBSOCKET CONNECTED =========="
      );

      console.log(
          "Game ID:",
          state.gameId
      );

      console.log(
          "Player:",
          state.player
      );

      console.log(
          "=========================================="
      );


      state.connected = true;
      client.subscribe(`/topic/game/${state.gameId}`, (frame) => {
        try {
          const event = JSON.parse(frame.body);
          handleServerEvent(event);
        } catch (e) {
          console.error('Failed to parse server event', e);
        }
      });

      client.send(`/app/game/${state.gameId}/connect`, {}, JSON.stringify({
        gameId: state.gameId,
        player: state.player,
      }));
    }, (error) => {
      console.error('STOMP connection error', error);
      showToast('Connection lost. Reconnecting…');
      setTimeout(() => connectWebSocket(), 2000);
    });

    state.stompClient = client;
  }

  function sendMove(move) {
    if (!state.stompClient || !state.connected) return;
    state.stompClient.send(`/app/game/${state.gameId}/move`, {}, JSON.stringify({
      gameId: state.gameId,
      player: state.player,
      move,
    }));
  }

  function sendReset() {
    if (!state.stompClient || !state.connected) return;
    state.stompClient.send(`/app/game/${state.gameId}/reset`, {}, JSON.stringify({}));
  }

  // ------------------------------------------------------------------
  // Server event handling
  // ------------------------------------------------------------------
  function handleServerEvent(event) {
    switch (event.type) {
      case 'PLAYER_JOINED':
      case 'GAME_STARTED':
      case 'GAME_STATE':
        onGameStateUpdate(event.payload);
        break;
      case 'WAITING_FOR_OPPONENT':
        onGameStateUpdate(event.payload);
        showScreen('waiting');
        break;
      case 'MOVE_SUBMITTED':
        onMoveSubmitted(event.payload);
        break;
      case 'ROUND_RESULT':
        onRoundResult(event.payload);
        break;
      case 'SCORE_UPDATE':
        animateScore(event.payload.nitinScore, event.payload.saraScore);
        break;
      case 'NEXT_ROUND':
        onNextRound(event.payload);
        break;
      case 'GAME_FINISHED':
        onGameFinished(event.payload);
        break;
      case 'PLAYER_DISCONNECTED':
        onPlayerDisconnected(event.payload);
        break;
      case 'GAME_RESET':
        onGameReset(event.payload);
        break;
      case 'ERROR':
        showToast(event.payload.message || 'Something went wrong.');
        break;
      default:
        break;
    }
  }

  function onGameStateUpdate(gameState) {
    if (!gameState) return;
    el.roundCurrent.textContent = Math.min(gameState.currentRound, gameState.totalRounds);
    el.roundTotal.textContent = gameState.totalRounds;
    el.scoreNitin.textContent = gameState.nitinScore;
    el.scoreSara.textContent = gameState.saraScore;

    const opponentConnected = state.player === 'NITIN' ? gameState.saraConnected : gameState.nitinConnected;
    const opponentJoined = state.player === 'NITIN' ? gameState.saraJoined : gameState.nitinJoined;

    if (opponentJoined && opponentConnected) {
      hideOpponentBanner();
    }

    if (gameState.nitinJoined && gameState.saraJoined && gameState.nitinConnected && gameState.saraConnected) {
      if (gameState.status === 'FINISHED') {
        renderFinal(gameState);
        showScreen('final');
      } else {
        showScreen('game');
        //resetMoveUI();
      }
    } else if (gameState.nitinJoined && gameState.saraJoined) {
      // Both have joined the room, but one hasn't opened the socket yet -
      // treat it the same as waiting-for-opponent visually.
      showScreen('waiting');
    } else {
      showScreen('waiting');
    }
  }

  function onMoveSubmitted(payload) {
    if (payload.player === state.player) return;
    // Opponent locked in a move - we don't know what it is, just acknowledge visually
    // by leaving our own waiting state as-is; nothing to reveal yet.
  }

  function onRoundResult(payload) {
    clearTimeout(state.resultTimer);
    //state.moveLockedThisRound = false;

    el.resultRoundLabel.textContent = `ROUND ${payload.round} RESULT`;
    el.resultNitinEmoji.textContent = MOVE_EMOJI[payload.nitinMove];
    el.resultSaraEmoji.textContent = MOVE_EMOJI[payload.saraMove];

    let outcomeText;
    let outcomeClass;
    if (payload.winner === 'DRAW') {
      outcomeText = 'DRAW!';
      outcomeClass = 'draw';
    } else if (payload.winner === 'NITIN') {
      outcomeText = 'Nitin wins this round!';
      outcomeClass = 'win-nitin';
    } else {
      outcomeText = 'Sara wins this round!';
      outcomeClass = 'win-sara';
    }
    el.resultHeading.textContent = outcomeText;
    el.resultHeading.className = `result-outcome ${outcomeClass}`;

    el.resultScoreNitin.textContent = payload.nitinScore;
    el.resultScoreSara.textContent = payload.saraScore;
    el.resultNextNote.textContent = payload.gameFinished ? 'Calculating final results…' : 'Next round starting…';

    el.resultOverlay.hidden = false;
    animateScore(payload.nitinScore, payload.saraScore);

    // The overlay auto-dismisses; NEXT_ROUND / GAME_FINISHED events (sent
    // right after this one) drive the actual screen transition.
    state.resultTimer = setTimeout(() => {
      el.resultOverlay.hidden = true;
    }, payload.gameFinished ? 2200 : 2600);
  }

  function onNextRound(gameState) {
    resetMoveUI();
    onGameStateUpdate(gameState);
  }

  function onGameFinished(payload) {
    setTimeout(() => {
      el.resultOverlay.hidden = true;
      renderFinalFromSummary(payload);
      showScreen('final');
    }, 2200);
  }

  function onPlayerDisconnected(payload) {
    if (payload.player === state.player) return; // it was us; ignore
    const name = DISPLAY_NAME[payload.player] || 'Your opponent';
    el.opponentBanner.hidden = false;
    el.opponentBanner.textContent = payload.abandoned
      ? `${name} did not reconnect. You can wait or start a new game.`
      : `${name} disconnected. Waiting for them to reconnect…`;
  }

  function hideOpponentBanner() {
    el.opponentBanner.hidden = true;
    el.opponentBanner.textContent = '';
  }

  function onGameReset(gameState) {
    clearTimeout(state.resultTimer);
    el.resultOverlay.hidden = true;
    resetMoveUI();
    onGameStateUpdate(gameState);
  }

  // ------------------------------------------------------------------
  // Round / move UI
  // ------------------------------------------------------------------
  function resetMoveUI() {
    state.moveLockedThisRound = false;
    el.moveButtons.forEach((btn) => {
      btn.disabled = false;
      btn.classList.remove('selected');
    });
    document.querySelector('.move-buttons').hidden = false;
    el.waitingNote.hidden = true;
    el.movePrompt.textContent = `${DISPLAY_NAME[state.player]}, choose your move`;
    el.opponentNameLabel.textContent = DISPLAY_NAME[OPPONENT_OF[state.player]];
  }

  function lockMoveUI(move) {
    state.moveLockedThisRound = true;
    el.moveButtons.forEach((btn) => {
      btn.disabled = true;
      btn.classList.toggle('selected', btn.dataset.move === move);
    });
    el.myMoveDisplay.textContent = `${MOVE_EMOJI[move]} ${move}`;
    el.waitingNote.hidden = false;
  }

  function animateScore(nitinScore, saraScore) {
    updateScoreValue(el.scoreNitin, nitinScore);
    updateScoreValue(el.scoreSara, saraScore);
  }

  function updateScoreValue(node, value) {
    if (node.textContent === String(value)) return;
    node.textContent = value;
    node.classList.remove('bump');
    // Force reflow so the animation can restart on rapid updates.
    void node.offsetWidth;
    node.classList.add('bump');
  }

  // ------------------------------------------------------------------
  // Final results rendering
  // ------------------------------------------------------------------
  function renderFinal(gameState) {
    el.finalNitinScore.textContent = gameState.nitinScore;
    el.finalSaraScore.textContent = gameState.saraScore;
    setWinnerLine(gameState.nitinScore, gameState.saraScore);
    renderHistory(gameState.roundHistory || []);
  }

  function renderFinalFromSummary(payload) {
    el.finalNitinScore.textContent = payload.nitinScore;
    el.finalSaraScore.textContent = payload.saraScore;
    el.finalNitinWins.textContent = payload.nitinWins;
    el.finalSaraWins.textContent = payload.saraWins;
    el.finalDraws.textContent = payload.draws;
    setWinnerLine(payload.nitinScore, payload.saraScore);
    renderHistory(payload.roundHistory || []);
  }

  function setWinnerLine(nitinScore, saraScore) {
    if (nitinScore > saraScore) {
      el.finalWinnerLine.textContent = 'Nitin wins the match!';
    } else if (saraScore > nitinScore) {
      el.finalWinnerLine.textContent = 'Sara wins the match!';
    } else {
      el.finalWinnerLine.textContent = "It's a tie match!";
    }
  }

  function renderHistory(rounds) {
    el.historyList.innerHTML = '';
    rounds.forEach((round) => {
      const row = document.createElement('div');
      row.className = 'history-row';
      row.setAttribute('role', 'listitem');

      const winnerClass = round.winner === 'NITIN' ? 'win-nitin'
        : round.winner === 'SARA' ? 'win-sara' : 'draw';
      const winnerLabel = round.winner === 'NITIN' ? 'Nitin won'
        : round.winner === 'SARA' ? 'Sara won' : 'Draw';

      row.innerHTML = `
        <span class="history-round-num">#${round.roundNumber}</span>
        <span class="history-move">${MOVE_EMOJI[round.nitinMove]} Nitin: ${round.nitinMove}</span>
        <span class="history-move">${MOVE_EMOJI[round.saraMove]} Sara: ${round.saraMove}</span>
        <span class="history-winner ${winnerClass}">${winnerLabel}</span>
      `;
      el.historyList.appendChild(row);
    });
  }

  // ------------------------------------------------------------------
  // Flow: landing -> lobby -> create/join -> waiting/game
  // ------------------------------------------------------------------
  function selectRole(player) {
    state.player = player;
    el.lobbyPlayerName.textContent = DISPLAY_NAME[player];
    el.lobbyError.textContent = '';
    el.inputGameCode.value = '';
    showScreen('lobby');
  }

  async function handleCreateGame() {
    el.lobbyError.textContent = '';
    el.btnCreateGame.disabled = true;
    try {
      const gameState = await createGame(state.player);
      state.gameId = gameState.gameId;
      saveSession();
      el.waitingGameCode.textContent = state.gameId;
      showScreen('waiting');
      connectWebSocket();
    } catch (err) {
      el.lobbyError.textContent = err.message;
    } finally {
      el.btnCreateGame.disabled = false;
    }
  }

  async function handleJoinGame(evt) {
    evt.preventDefault();
    el.lobbyError.textContent = '';
    const code = el.inputGameCode.value.trim().toUpperCase();
    if (!code) return;
    try {
      const gameState = await joinGame(code, state.player);
      state.gameId = gameState.gameId;
      saveSession();
      el.waitingGameCode.textContent = state.gameId;
      connectWebSocket();
    } catch (err) {
      el.lobbyError.textContent = err.message;
    }
  }

  async function attemptRestoreSession() {
    const saved = loadSession();
    if (!saved || !saved.gameId || !saved.player) return;
    try {
      const gameState = await fetchGameState(saved.gameId);
      state.gameId = saved.gameId;
      state.player = saved.player;
      el.lobbyPlayerName.textContent = DISPLAY_NAME[state.player];
      el.waitingGameCode.textContent = state.gameId;
      connectWebSocket(() => onGameStateUpdate(gameState));
    } catch (err) {
      // Game no longer exists on the (restarted) server - start fresh.
      clearSession();
    }
  }

  // ------------------------------------------------------------------
  // Event listeners
  // ------------------------------------------------------------------
  document.getElementById('btn-play-nitin').addEventListener('click', () => selectRole('NITIN'));
  document.getElementById('btn-play-sara').addEventListener('click', () => selectRole('SARA'));
  el.btnBackLanding.addEventListener('click', () => showScreen('landing'));
  el.btnCreateGame.addEventListener('click', handleCreateGame);
  el.formJoinGame.addEventListener('submit', handleJoinGame);

  el.inputGameCode.addEventListener('input', () => {
    el.inputGameCode.value = el.inputGameCode.value.toUpperCase().replace(/[^A-Z0-9]/g, '');
  });

  el.btnCopyCode.addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(state.gameId || '');
      el.copyFeedback.textContent = 'Copied!';
    } catch (e) {
      el.copyFeedback.textContent = state.gameId || '';
    }
    setTimeout(() => { el.copyFeedback.textContent = ''; }, 2000);
  });

  el.moveButtons.forEach((btn) => {
    btn.addEventListener('click', () => {
      if (state.moveLockedThisRound) return;
      const move = btn.dataset.move;
      lockMoveUI(move);
      sendMove(move);
    });
  });

  el.btnPlayAgain.addEventListener('click', () => {
    sendReset();
  });

  window.addEventListener('beforeunload', () => {
    // Keep the session so a refresh reconnects automatically; only an
    // explicit "Play Again" on a finished game clears nothing here since
    // the same gameId is reused.
  });

  // ------------------------------------------------------------------
  // Boot
  // ------------------------------------------------------------------
  showScreen('landing');
  //attemptRestoreSession();
})();

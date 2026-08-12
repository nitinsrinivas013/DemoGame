# Rock Paper Scissors — Nitin vs Sara

A legitimate, server-authoritative, real-time multiplayer Rock Paper Scissors game
for exactly two named players — **Nitin** and **Sara** — playing a best-of-10 match.

There is no rigging, no predetermined winners, and no player-specific advantage.
The server is the single source of truth for every move, every round, and every score.

---

## Project Overview

Two people open the site on their own device, each picks their name, and either
creates a game (getting a 6-character room code) or joins one with a code.
Once both are connected, the app plays 10 rounds of classic Rock-Paper-Scissors,
live, with the server calculating every outcome from the two moves it actually
receives — nothing is calculated, guessed, or trusted from the browser.

## Features

- Landing page with **PLAY AS NITIN** / **PLAY AS SARA** role selection
- Room-code based matchmaking (create or join with a 6-character code), no accounts
- Real-time play over STOMP-over-WebSocket
- Server-authoritative round resolution (frontend never computes a winner)
- Live scoreboard with animated updates
- Round-result overlay revealing both moves only after both are submitted
- Automatic 10-round progression → final results screen with full round history
- **Play Again** — resets score/rounds, keeps the same room and players
- Disconnect detection with a reconnection grace period
- Mobile-first, responsive, no-horizontal-scroll layout with large tap targets
- Accessible: semantic HTML, keyboard support, visible focus states, ARIA labels

## Architecture

```
Browser (Nitin)                         Browser (Sara)
     |                                        |
     |  REST: create/join game                |
     |  STOMP/WebSocket: /ws                  |
     v                                        v
              Spring Boot Application
   ┌──────────────────────────────────────────┐
   │ controller/  → REST + STOMP endpoints     │
   │ service/     → GameService (rules engine) │
   │ model/       → Game, Round, enums         │
   │ dto/         → wire-format messages       │
   │ exception/   → validation & error mapping │
   │ In-memory game state (ConcurrentHashMap)  │
   └──────────────────────────────────────────┘
```

Nothing is persisted to disk or a database — state lives in memory for the
lifetime of the server process, which is appropriate for a two-player casual game.

## Technologies

- Java 21, Spring Boot 3.3 (Web, WebSocket, Validation)
- Maven
- STOMP over WebSocket, Spring's simple in-memory broker
- Vanilla HTML5 / CSS3 / JavaScript (no frontend framework)
- `stomp.js` STOMP client, loaded from a CDN
- JUnit 5 + Mockito for `GameService` unit tests

## WebSocket Architecture

| Purpose | Destination |
|---|---|
| STOMP endpoint | `/ws` |
| Client → Server prefix | `/app` |
| Server → Client broadcast prefix | `/topic` |
| Bind this socket to a player role | `/app/game/{gameId}/connect` |
| Submit a move | `/app/game/{gameId}/move` |
| Start a new match in the same room | `/app/game/{gameId}/reset` |
| Subscribe for all game events | `/topic/game/{gameId}` |

Event types broadcast on `/topic/game/{gameId}`: `PLAYER_JOINED`, `GAME_STARTED`,
`WAITING_FOR_OPPONENT`, `MOVE_SUBMITTED`, `ROUND_RESULT`, `SCORE_UPDATE`,
`NEXT_ROUND`, `GAME_FINISHED`, `PLAYER_DISCONNECTED`, `GAME_RESET`, `ERROR`.

The client only ever sends `{ gameId, player, move }`. It never sends a winner,
a score, or the opponent's move — the server rejects anything it didn't calculate itself.

## REST API

| Method | Path | Body | Purpose |
|---|---|---|---|
| `POST` | `/api/games` | `{ "player": "NITIN" }` | Create a game, claim a role, get a room code |
| `POST` | `/api/games/{gameId}/join` | `{ "player": "SARA" }` | Join an existing game with the other role |
| `GET` | `/api/games/{gameId}` | — | Fetch the current game state (used on reconnect) |

## Game Flow

```
Landing → pick Nitin/Sara → create or join with room code
   → waiting screen (until both connected)
   → GAME_STARTED → Round 1..10:
        each player picks Rock/Paper/Scissors independently
        → server resolves the round → ROUND_RESULT + SCORE_UPDATE
        → NEXT_ROUND (or GAME_FINISHED after round 10)
   → Final Results (score, win/loss/draw counts, full round history)
   → Play Again → same room, fresh 10-round match
```

## Project Structure

```
rock-paper-scissors/
├── pom.xml
├── README.md
├── Dockerfile
├── .gitignore
├── src/
│   ├── main/
│   │   ├── java/com/example/rps/
│   │   │   ├── RpsApplication.java
│   │   │   ├── config/          (WebSocketConfig, WebSocketEventListener)
│   │   │   ├── controller/      (GameController, GameWebSocketController)
│   │   │   ├── service/         (GameService — the rules engine)
│   │   │   ├── model/           (Game, Round, Player, Move, GameStatus, RoundWinner, MessageType)
│   │   │   ├── dto/             (wire-format request/response/event objects)
│   │   │   └── exception/       (GameNotFoundException, InvalidPlayerActionException, handler)
│   │   └── resources/
│   │       ├── application.properties
│   │       └── static/          (index.html, css/style.css, js/app.js)
│   └── test/java/com/example/rps/service/GameServiceTest.java
```

## Running Locally

Prerequisites: **Java 21** and **Maven** (or use the included wrapper if you add one).

```bash
mvn clean spring-boot:run
```

The app starts on **http://localhost:8080**.

## Testing Two Players Locally

1. Open **http://localhost:8080** in one browser window → click **PLAY AS NITIN** → **CREATE NEW GAME**. Note the room code.
2. Open a **second** window (or an incognito window, or a different browser) at **http://localhost:8080** → click **PLAY AS SARA** → enter the room code → **JOIN GAME**.
3. Both windows should transition to Round 1 automatically. Play through all 10 rounds.

## Running with Maven

```bash
mvn clean package
java -jar target/rock-paper-scissors.jar
```

## Running with Docker

```bash
docker build -t rps-game .
docker run -p 8080:8080 rps-game
```

Then open **http://localhost:8080**.

---

## AWS EC2 Deployment

Assumes **Ubuntu 24.04 LTS**.

1. **Create an EC2 instance** in the AWS Console → EC2 → Launch Instance.
2. **Select a suitable small instance** — `t3.micro` or `t3.small` is plenty for a 2-player game.
3. **Create/download an SSH key pair** during launch (or reuse an existing `.pem`).
4. **Configure the security group**:
   - Allow **SSH (22)** from your IP.
   - Temporarily allow **TCP 8080** from anywhere (`0.0.0.0/0`) for initial testing — remove this once Nginx is in front (see below).
5. **SSH into the instance:**
   ```bash
   chmod 400 your-key.pem
   ssh -i your-key.pem ubuntu@EC2_PUBLIC_IP
   ```
6. **Install Java 21:**
   ```bash
   sudo apt update
   sudo apt install -y openjdk-21-jdk
   java -version
   ```
7. **Install Git:**
   ```bash
   sudo apt install -y git
   ```
8. **Clone the GitHub repository:**
   ```bash
   git clone https://github.com/YOUR_USERNAME/rock-paper-scissors.git
   cd rock-paper-scissors
   ```
9. **Build the application:**
   ```bash
   sudo apt install -y maven
   mvn clean package -DskipTests
   ```
10. **Run the Spring Boot application:**
    ```bash
    java -jar target/rock-paper-scissors.jar
    ```
    For a persistent run, use `nohup java -jar target/rock-paper-scissors.jar &` or set it up as a systemd service.
11. **Find the EC2 public IP** in the EC2 console (or `curl checkip.amazonaws.com` from inside the instance).
12. **Open** `http://EC2_PUBLIC_IP:8080` in a browser to confirm it's running.
13. **Open the site from Nitin's device**, click **PLAY AS NITIN**, create a game.
14. **Open it from Sara's device**, click **PLAY AS SARA**, join using the game code.
15–16. Confirm both screens transition into the game together.
17–19. **Create a game**, **join using the game code**, and **play all 10 rounds** end-to-end to confirm the deployment works.

---

## Production Nginx Setup

```
Internet
   |
   v
 Nginx (80 / 443)
   |
   v
 Spring Boot (127.0.0.1:8080)
   |
   v
 WebSocket /ws
```

Only ports **80** and **443** should be open to the internet; **8080** should
only be reachable from `localhost` (Nginx and the app on the same box), so
close port 8080 in the security group once Nginx is confirmed working.

Install Nginx:

```bash
sudo apt install -y nginx
```

`/etc/nginx/sites-available/rps`:

```nginx
server {
    listen 80;
    server_name yourdomain.com;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # WebSocket upgrade for STOMP over /ws
    location /ws {
        proxy_pass http://127.0.0.1:8080/ws;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 86400;
    }
}
```

Enable it:

```bash
sudo ln -s /etc/nginx/sites-available/rps /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

The `Upgrade`/`Connection: upgrade` headers on the `/ws` block are what allow
the WebSocket handshake to pass through Nginx — without them the STOMP
connection will fail even though the REST endpoints work fine.

## HTTPS

If you have a domain pointed at the EC2 instance, use **Certbot** for a free
Let's Encrypt certificate:

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d yourdomain.com
```

Certbot rewrites the Nginx config to serve `https://yourdomain.com` and
redirect HTTP → HTTPS. WebSockets then upgrade automatically to
`wss://yourdomain.com/ws` because the frontend (`app.js`) already derives the
scheme from `window.location.protocol`.

**Why HTTPS matters for WebSockets:** browsers block insecure `ws://`
connections from an HTTPS page (mixed content), and many corporate/mobile
networks throttle or block plain WebSocket traffic entirely. `wss://` also
encrypts gameplay traffic just like any other web traffic — there's no reason
to run a public game session unencrypted once you have a real domain.

---

## Troubleshooting

**WebSocket connection failure**
- Confirm the app is actually running (`curl http://localhost:8080/api/games/ZZZZZZ` should return a 404 JSON error, not a connection refusal).
- If behind Nginx, confirm the `/ws` location block has the `Upgrade`/`Connection` headers (see above) — this is the #1 cause of "works on REST, fails on WebSocket".
- Check the browser console for the exact close code/reason.

**EC2 security group issues**
- SSH (22) must be open from your IP, and either 8080 (for direct testing) or 80/443 (once Nginx is set up) must be open publicly. Both should not be open at once in production.

**Nginx WebSocket upgrade problems**
- `proxy_http_version 1.1;` is required — WebSocket upgrade doesn't work over HTTP/1.0.
- Make sure there isn't a second, conflicting `location /` block swallowing `/ws` before it reaches the dedicated block.

**Port 8080 problems**
- If `java -jar` fails with "Address already in use", another process is bound to 8080: `sudo lsof -i :8080` and stop it, or change `server.port` in `application.properties`.

**Reconnecting players**
- The frontend stores `{ gameId, player }` in `localStorage` and reconnects automatically on page reload, restoring state from `GET /api/games/{gameId}`. If the server has restarted, in-memory state is gone and the stored session is discarded automatically — the player lands back on the landing page.

**Duplicate player roles**
- The server rejects a second `NITIN` or a second `SARA` joining the same room with a `409` (`INVALID_ACTION`). If you see this unexpectedly, someone (maybe a second tab) already claimed that role in that room — use the other role or a new room code.

**Browser caching**
- If you deploy an update and the browser still shows old behavior, hard-refresh (Ctrl/Cmd+Shift+R) — static assets under `static/` are served with default caching by Spring Boot.

**Two players attempting to join simultaneously**
- Joins are handled inside a `synchronized (game)` block in `GameService`, so simultaneous join requests for the same room are serialized: the first one wins the slot, the second gets a clear "already joined" error rather than corrupting state.

---

# RUN IT NOW

Exact commands to run it locally on **Windows** (PowerShell or Command Prompt),
assuming Java 21 and Maven are installed and on your `PATH`:

```bat
cd rock-paper-scissors
mvn clean package -DskipTests
java -jar target\rock-paper-scissors.jar
```

Then open **http://localhost:8080** in two separate browser windows — one as
Nitin, one as Sara.

# DEPLOY TO EC2

Exact commands to deploy and run it on Ubuntu EC2:

```bash
ssh -i your-key.pem ubuntu@EC2_PUBLIC_IP

sudo apt update
sudo apt install -y openjdk-21-jdk maven git nginx

git clone https://github.com/YOUR_USERNAME/rock-paper-scissors.git
cd rock-paper-scissors
mvn clean package -DskipTests

nohup java -jar target/rock-paper-scissors.jar > app.log 2>&1 &

# (then configure Nginx + Certbot as described above, and close port 8080
#  in the EC2 security group once /ws proxies correctly through Nginx)
```

Open `http://EC2_PUBLIC_IP:8080` to verify directly, then `http://yourdomain.com`
(or `https://` after Certbot) once Nginx is in front.

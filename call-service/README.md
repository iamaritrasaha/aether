# Aether Call Service (development)

The small backend behind **Aether Calls** (the LiveKit-based, primary calling
path). It owns the Aether calling-identity directory, call invitations, room
creation and short-lived LiveKit participant tokens. **The LiveKit API secret
lives only here** — the Android app receives tokens, never credentials.

This is a DEVELOPMENT deployment for Aether-to-Aether calls over your LAN.
Production hardening (real identity, secure E2EE key distribution, TURN over
TLS, push) is intentionally out of scope here; see
`docs/architecture/aether-calls.md` for the boundary.

## One-time setup

1. Node ≥ 18, then:
   ```bash
   cd call-service && npm install
   ```
2. LiveKit server binary:
   ```bash
   mkdir -p bin && cd bin
   curl -sSL -o lk.tgz https://github.com/livekit/livekit/releases/download/v1.13.6/livekit_1.13.6_linux_amd64.tar.gz
   tar -xzf lk.tgz && rm lk.tgz
   mv livekit_server livekit-server 2>/dev/null || true
   cd ..
   ```
3. Keys:
   ```bash
   bin/livekit-server generate-keys
   # put the API Key    -> .env  LIVEKIT_API_KEY      (must equal the key name in
   #                                                livekit-dev.yaml: keys.<name>, e.g. devkey)
   # put the API Secret -> .env  LIVEKIT_API_SECRET   (run-dev.sh renders it into the
   #                                                server config at start; v1.13.6 does
   #                                                NOT expand ${...} in config values)
   cp .env.example .env   # then edit LIVEKIT_URL + the two key fields
   ```
4. Set `LIVEKIT_URL` in `.env` to this machine's LAN address
   (`ws://<your-ip>:7880`) and build the app with the matching service URL:
   ```bash
   ./gradlew :app:installDebug -PaetherCallServiceUrl=http://<your-ip>:8080
   ```
   (The default is `http://192.168.0.30:8080`.)

## Run

```bash
call-service/run-dev.sh
# livekit-server on :7880 (UDP 50000-60000) + call service on :8080
```

Verify: `curl http://localhost:8080/health`

## Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| POST | /register | upsert `{aetherId, displayName, telegramUserId}` |
| GET | /capabilities?aetherId= | `{callable}` — is someone an Aether-calling user |
| GET | /lookup?telegramUserId= | directory: Telegram user → Aether identity |
| POST | /call/invite | create room + caller token → `{inviteId, join}` |
| GET | /call/incoming?aetherId= | poll: ringing invite addressed to me |
| POST | /call/accept | callee token + DEV e2ee key → `{join}` |
| POST | /call/decline | reject an invite |
| POST | /call/complete | mark room ended |
| GET | /health | service + LiveKit URL + counts |

## E2EE status (do not overclaim)

LiveKit frame encryption is ON, but the per-invite media key is **handed out
by this service over plain HTTP** — the service (and anyone on the LAN) can
read every dev call's key. That is development-scoped only. Production
requires per-installation identity (Android Keystore) + authenticated
per-call key establishment; the app isolates the boundary in
`AetherCallSecurity` / `AetherCallE2EE`.

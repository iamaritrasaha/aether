#!/usr/bin/env bash
# Starts the dev LiveKit server + the Aether Call Service on this machine.
#
# Prerequisites (one-time):
#   1. node >= 18 and:  (cd call-service && npm install)
#   2. livekit-server binary:
#        curl -sSL https://github.com/livekit/livekit-server/releases/download/v1.8.4/livekit-server_1.8.4_linux_amd64.tar.gz \
#          | tar -xz -C call-service/bin   # creates call-service/bin/livekit-server
#   3. keys: call-service/bin/livekit-server generate-keys
#      -> paste the secret into call-service/.env (LIVEKIT_API_SECRET)
#      -> and into call-service/livekit-dev.yaml (keys.devkey)
#      -> set LIVEKIT_URL to this machine's LAN IP (ws://<ip>:7880)
#      -> set the SAME LAN IP as -PaetherCallServiceUrl when building the app
#         (default http://192.168.0.30:8080)
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ ! -x "$DIR/bin/livekit-server" ]; then
  echo "livekit-server binary not found at call-service/bin/livekit-server — see prerequisites in this script." >&2
  exit 1
fi
if [ ! -f "$DIR/.env" ]; then
  echo "call-service/.env missing — copy .env.example and fill in." >&2
  exit 1
fi

set -a; source "$DIR/.env"; set +a

"$DIR/bin/livekit-server" --config "$DIR/livekit-dev.yaml" &
LIVEKIT_PID=$!
trap 'kill "$LIVEKIT_PID" 2>/dev/null' EXIT

(cd "$DIR" && node server.js)

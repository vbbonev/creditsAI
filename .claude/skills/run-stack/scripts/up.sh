#!/usr/bin/env bash
# Build and start the creditsAI stack (everything in Docker) and wait until it responds through nginx.
set -euo pipefail

cd "$(git rev-parse --show-toplevel 2>/dev/null || pwd)"

if [[ ! -f .env ]]; then
  echo "Missing .env. Copy .env.example to .env and fill in the passwords first." >&2
  exit 1
fi

# --wait blocks until every service's healthcheck passes (or fails)
docker compose up -d --build --wait --wait-timeout 300

check() { # name url
  printf '%-10s ' "$1"
  if curl -fsS -o /dev/null "$2"; then echo "OK  $2"; else echo "FAIL $2"; return 1; fi
}

status=0
# Everything is reached through the frontend nginx on http://localhost (single entry point)
check frontend http://localhost/ || status=1
check keycloak http://localhost/auth/realms/credits/.well-known/openid-configuration || status=1
# The API is protected, so without a token it must answer 401 (proves nginx reaches the backend)
printf '%-10s ' api
code=$(curl -s -o /dev/null -w '%{http_code}' http://localhost/api/clients)
if [[ "$code" == 401 ]]; then echo "OK  http://localhost/api/clients (401 without token)"; else echo "FAIL http://localhost/api/clients (got $code, expected 401)"; status=1; fi

exit $status

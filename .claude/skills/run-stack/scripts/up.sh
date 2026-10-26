#!/usr/bin/env bash
# Start the creditsAI stack and wait until all services respond.
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
check keycloak http://localhost:8180/realms/credits/.well-known/openid-configuration || status=1
check backend  http://localhost:8080/actuator/health || status=1
check frontend http://localhost:4200 || status=1

exit $status

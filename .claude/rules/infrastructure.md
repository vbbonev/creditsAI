---
paths:
  - "docker-compose.yml"
  - "docker/**"
  - "keycloak/**"
  - "**/Dockerfile"
  - ".env.example"
  - "frontend/nginx.conf"
---

# Infrastructure rules (Docker Compose + Keycloak)

## Everything runs in Docker
`docker-compose.yml` runs four services, all on one network:

| Service | Image | Host port |
|---|---|---|
| `postgres` | `postgres:16` | 5432 |
| `keycloak` | `quay.io/keycloak/keycloak` (pinned 26.x version) | 8180 → 8080 |
| `backend` | built from `backend/Dockerfile` | 8080 |
| `frontend` | built from `frontend/Dockerfile` | 4200 → 80 |

- Each service has a `healthcheck`. `depends_on` uses `condition: service_healthy`, in the order postgres → keycloak → backend → frontend.
- `backend/Dockerfile`: multi-stage. A Maven + JDK 21 stage builds the app, and a JRE 21 stage (`eclipse-temurin:21-jre`) runs it as a non-root user.
- `frontend/Dockerfile`: multi-stage. A Node 20 stage runs `npm ci && npm run build`, and an `nginx:alpine` stage serves `dist/` with `frontend/nginx.conf`, including the SPA fallback `try_files $uri /index.html`.
- Pin image versions. Never use `latest`.

## One Postgres, two databases
- `docker/postgres/init/01-create-databases.sh` (mounted at `/docker-entrypoint-initdb.d`) creates:
  - database `credits`, owned by user `credits` (the application)
  - database `keycloak`, owned by user `keycloak` (Keycloak)
- Each user owns only its own database. The application never touches the Keycloak database.
- The init script runs only on an **empty** volume. Changing it requires `docker compose down -v`, which deletes data, so always ask the user first.

## Keycloak realm import
- `keycloak/import/credits-realm.json` is mounted at `/opt/keycloak/data/import`. Keycloak is started with `start-dev --import-realm`, with `KC_DB=postgres` and `KC_DB_URL=jdbc:postgresql://postgres:5432/keycloak`.
- The realm file contains:
  - realm `credits`, with realm roles `USER` and `ADMIN`
  - client `credits-web`: public client, standard flow only, PKCE `S256`, `redirectUris: ["http://localhost:4200/*"]`, `webOrigins: ["http://localhost:4200"]`. The backend needs no client, because it only validates tokens.
  - test users (dev only): `user1`/`user1` with `USER`, and `admin1`/`admin1` with `USER` and `ADMIN`
- The import skips a realm that already exists. To re-import after changing the file, the user must delete the realm or reset the volume. Ask first.
- Validate the JSON with `jq empty keycloak/import/credits-realm.json` after every edit.

## Issuer URL (common pitfall)
The browser receives tokens from `http://localhost:8180`, so the token's `iss` is `http://localhost:8180/realms/credits`. Inside Docker, though, the backend reaches Keycloak at `http://keycloak:8080`. So the backend configures **both** of these:
- `spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8180/realms/credits`, used only to check the `iss` claim
- `spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://keycloak:8080/realms/credits/protocol/openid-connect/certs`, used to fetch the signing keys

Set `KC_HOSTNAME=http://localhost:8180` on Keycloak so the issuer is stable.

## Secrets
- All passwords (Postgres superuser, `credits`/`keycloak` DB users, Keycloak admin) come from `.env` via `${VAR}` in compose.
- Commit only `.env.example`, with placeholder values. Claude cannot write `.env` (a hook blocks it), so the user copies `.env.example` to `.env`.

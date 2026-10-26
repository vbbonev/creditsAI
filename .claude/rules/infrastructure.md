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

## One mode: everything in Docker
There are no dev/prod modes, compose profiles or Spring profiles. Development and demos run the same stack:
- Start (and rebuild after every code change): `docker compose up -d --build --wait`
- Stop: `docker compose down`
- The only entry point is **nginx on `http://localhost`**, and port 80 is the only published port, bound to loopback only (`127.0.0.1:80:80`), so other machines on the network can't reach the app or the Keycloak admin console at `/auth/admin`. Postgres, Keycloak and the backend have no host ports.
- No live reload and no debug port. Code changes reach the containers by rebuilding.
- Use plain `localhost`, never a custom domain or hosts-file entry, so anyone can clone, `cp .env.example .env`, `docker compose up` and open http://localhost.
- Don't use a Docker network alias to make the backend see the public Keycloak URL: an alias can't take over `localhost` inside a container. Use the issuer/JWKS split below instead.

### Single entry point
The nginx is the **frontend container** itself (built from `frontend/Dockerfile`, compose service `frontend`). There is no separate proxy container. `frontend/nginx.conf` serves the SPA and routes everything on `http://localhost`:
- `/` → Angular SPA (`try_files $uri /index.html`)
- `/api/` → `backend:8080`, with `proxy_set_header Host $host;`. Browsers send `Origin` on same-origin writes, and the backend allows no CORS origins, so if the backend sees host `backend:8080` every POST/PUT/DELETE gets 403.
- `/auth/` → `keycloak:8080` (Keycloak `KC_PROXY_HEADERS=xforwarded`)
- In both proxy locations, **overwrite** the forwarded headers and never append: `proxy_set_header X-Forwarded-For $remote_addr;` (not `$proxy_add_x_forwarded_for`, which lets a client fake its IP in Keycloak's events and brute-force detection), plus `Host $host`, `X-Forwarded-Proto $scheme` and `X-Forwarded-Host $host`.
- After adding nginx, check end to end that a POST to `/api/...` with `Origin: http://localhost` and a valid token succeeds.

The SPA calls the API with the relative path `/api` and Keycloak at `http://localhost/auth`, so no CORS is needed.

### Services
| Service | Image | Host port |
|---|---|---|
| `postgres` | `postgres:16.15` | none |
| `keycloak` | `quay.io/keycloak/keycloak:26.8.1`, `KC_HTTP_RELATIVE_PATH=/auth` | none (behind nginx) |
| `backend` | built from `backend/Dockerfile` | none (behind nginx) |
| `frontend` (nginx: SPA + proxy) | built from `frontend/Dockerfile` | `127.0.0.1:80:80` (loopback only) |

- Keycloak's management port inherits the relative path: its health is `:9000/auth/health/ready`.
- This is a stack for local development and demos, not a production deployment: Keycloak runs `start-dev`, and the realm import includes the test users `user1`/`admin1`.
- Each service has a `healthcheck`. Images without curl use bash's `/dev/tcp`, invoked as `["CMD", "bash", "-c", ...]`: `CMD-SHELL` uses `/bin/sh`, which is `dash` in the Temurin (Ubuntu) image and has no `/dev/tcp`. `depends_on` uses `condition: service_healthy`, in the order postgres → keycloak → backend → frontend.
- `backend/Dockerfile`: multi-stage. A Maven + JDK 21 stage builds the app, and a JRE 21 stage (`eclipse-temurin:21.0.12_8-jre`) runs it as a non-root user.
- `frontend/Dockerfile`: multi-stage. A Node 20 stage runs `npm ci && npm run build`, and an `nginx:alpine` stage serves `dist/` with `frontend/nginx.conf`, which holds the SPA fallback `try_files $uri /index.html` plus the `/api/` and `/auth/` proxy locations.
- Pin image versions. Never use `latest`.

## One Postgres, two databases
- `docker/postgres/init/01-create-databases.sh` (mounted at `/docker-entrypoint-initdb.d`) creates:
  - database `credits`, owned by user `credits` (the application)
  - database `keycloak`, owned by user `keycloak` (Keycloak)
- Each user owns only its own database. The application never touches the Keycloak database.
- The init script runs only on an **empty** volume. Changing it requires `docker compose down -v`, which deletes data, so always ask the user first.
- Postgres has no host port. To inspect data, use `docker compose exec postgres psql -U credits credits`.

## Keycloak realm import
- `keycloak/import/credits-realm.json` is mounted at `/opt/keycloak/data/import`. Keycloak is started with `start-dev --import-realm`, with `KC_DB=postgres` and `KC_DB_URL=jdbc:postgresql://postgres:5432/keycloak`.
- The realm file contains:
  - realm `credits`, with realm roles `USER` and `ADMIN`
  - client `credits-web` (tokens with any other `azp` are rejected by the backend): public client, standard flow only, PKCE `S256`, `redirectUris`/`webOrigins`/post-logout redirect only for `http://localhost`. The backend needs no client, because it only validates tokens.
  - test users: `user1`/`user1` with `USER`, and `admin1`/`admin1` with `USER` and `ADMIN`
- The import skips a realm that already exists. To re-import after changing the file, the user must delete the realm or reset the volume. Ask first.
- Validate the JSON with `jq empty keycloak/import/credits-realm.json` after every edit.

## Issuer URL (common pitfall)
The token's `iss` is the Keycloak URL the **browser** used (`http://localhost/auth`), and inside Docker the backend can't reach that URL. So the backend (`application.yml`) sets two properties:
- `issuer-uri: http://localhost/auth/realms/credits`: never called. It's only compared as a string with the `iss` claim, so it is the public URL.
- `jwk-set-uri: http://keycloak:8080/auth/realms/credits/protocol/openid-connect/certs`: the only URL the backend calls (to fetch signing keys), so it uses the internal service name.

Keycloak sets `KC_HOSTNAME=http://localhost/auth` explicitly, so the issuer never depends on how Keycloak was reached.

## Secrets
- All passwords (Postgres superuser, `credits`/`keycloak` DB users, Keycloak admin) come from `.env` via `${VAR}` in compose. The backend gets `CREDITS_DB_PASSWORD` as a container environment variable.
- Commit only `.env.example`, with placeholder values. Claude cannot write `.env` (a hook blocks it), so the user copies `.env.example` to `.env`.

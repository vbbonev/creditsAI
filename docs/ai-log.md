# AI log

## Infrastructure

### 2026-10-26 — Dev stack: Postgres + Keycloak in Docker Compose
- **Approach:** The backend runs from IntelliJ during development; only Postgres (two databases, one owner each) and Keycloak (realm import) run in Docker. This keeps the edit-run loop fast, and Keycloak's issuer stays `localhost:8180` in both setups.
- **Workflow:**
  1. Claude asked open-ended how the backend should run (memory rule "ask architecture first"), then asked about the details: how config is loaded, the scope, and whether to add compose now.
  2. It wrote `docker-compose.yml`, `docker/postgres/init/01-create-databases.sh`, `keycloak/import/credits-realm.json` and `.env.example`, following `.claude/rules/infrastructure.md`, with images pinned (postgres 16.15, keycloak 26.8.1).
  3. The `security-reviewer` subagent audited it. Claude then bound the ports to 127.0.0.1 and fixed the Postgres healthcheck.
- **Testing:** `jq empty` on the realm, `docker compose config -q`, and a throwaway `creditsai-verify` stack: both services healthy, both databases with the right owners, issuer = `http://localhost:8180/realms/credits`.
- **AI tooling used:** Claude Code: infrastructure rule, `protect-files.sh` hook (`.env`), `security-reviewer` subagent, AskUserQuestion.
- **Key prompt(s):**
  > let's start wtih Spring boot first. Why develop the backend will run from Intellij but the DB and keycloak - in Docker
- **Issues / fixes:** `pg_isready` over the Unix socket could report healthy before the init script had created the databases. Fixed with `-h 127.0.0.1`, which the security review caught. The hook blocked Claude from writing `.env`, so the user ran `cp -n .env.example .env` instead.
- **Evidence:** `docker compose up -d --wait postgres keycloak` → both services healthy.

## Database layer

## Authentication

### 2026-10-26 — Backend as a JWT resource server for Keycloak
- **Approach:** The backend only validates tokens: `issuer-uri` checks `iss`, and `jwk-set-uri` fetches the keys, so it can be overridden for Docker. Realm roles map to `ROLE_*` in a single converter bean, method security is on, everything is denied by default, and CORS allows only the frontend origin.
- **Workflow:**
  1. Claude wrote `SecurityConfig`, `KeycloakRealmRoleConverter` and `CorsProperties` (security rule).
  2. Claude got a real token through the authorization-code + PKCE flow with curl and called the running backend with it.
  3. The `security-reviewer` subagent found no High or Medium issues. Claude made `CorsProperties` reject `*` or an empty list at startup. Checking `azp` and capping the page size are left for the user to decide.
- **Testing:** A `@WebMvcTest` with a test-only controller sends a real `Bearer` header to a mocked `JwtDecoder`: no token → 401, no roles → 403, USER on an admin action → 403, ADMIN → 204, CORS preflight from the allowed vs another origin. Plus unit tests for the converter and `CorsProperties`. Live: no token / bad token → 401, real admin1 token → past auth (404, no endpoint yet).
- **AI tooling used:** Claude Code: security rule, `security-reviewer` subagent.
- **Key prompt(s):** _(none specific; part of "let's create the backend infrastructure")_
- **Issues / fixes:** A first token taken from `admin-cli` had no role claims, which was misleading. Claude switched to the real `credits-web` PKCE flow, which showed `realm_access.roles` = USER, ADMIN.

## Backend API

### 2026-10-26 — Spring Boot skeleton (Boot 4.1.2)
- **Approach:** Generated the project from start.spring.io and then applied the project conventions: layer packages, MapStruct processor order, Liquibase `includeAll`, `ProblemDetail` errors, Javadoc on every method. Config imports the root `.env` so IntelliJ needs no environment variables.
- **Workflow:**
  1. User: "let's create the backend infrastructure". Claude asked about the scope and the local run mode.
  2. start.spring.io no longer offered Boot 3.5 (the version in CLAUDE.md), so Claude asked the user, who chose 4.1.2. Claude updated CLAUDE.md, `backend/CLAUDE.md`, the infrastructure rule and `docs/TODO.md`.
  3. Claude wrote `application.yml`, the Liquibase master changelog, `GlobalExceptionHandler`, `ResourceNotFoundException`/`BusinessRuleException`, and a multi-stage `Dockerfile`.
- **Testing:** `mvnw clean test`: 23 tests, all green. This includes the context load against Testcontainers Postgres (Liquibase plus `ddl-auto=validate`), 404/409/400 (field `errors`)/500 (no internals leaked), and health being public.
- **AI tooling used:** Claude Code (inline, no `backend-dev` subagent), `backend/CLAUDE.md` conventions, java-tests rule, AskUserQuestion.
- **Key prompt(s):**
  > let's create the backend infrastructure
- **Issues / fixes:** (1) Initializr wrote the parent version as `4.1.2.RELEASE`, which doesn't exist on Maven Central → changed to `4.1.2`. (2) Boot 4 moved the test annotations (`org.springframework.boot.webmvc.test.autoconfigure.*`). (3) `changes/.gitkeep` made Liquibase log a parse error → removed it and rely on `errorIfMissingOrEmpty: false`. (4) The user's IntelliJ run configuration was a Maven one with no goals ("No goals have been specified") → added `backend/pom.xml` as a Maven project and ran `BackendApplication` from the gutter with JDK 21.
- **Evidence:** `backend/mvnw -f backend/pom.xml test`; the backend running from IntelliJ with `GET http://localhost:8080/actuator/health` → `{"status":"UP"}` (good screenshot candidate for `docs/screenshots/`).

## Frontend

## Testing

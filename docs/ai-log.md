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

### 2026-10-26 — Frontend container: nginx as the single entry point
- **Approach:** The nginx in the `frontend` container serves the SPA and proxies `/api/` and `/auth/`, so everything is same-origin on `http://localhost` (no CORS). The security headers come from the frontend security review.
- **Workflow:**
  1. User: "add the Dockerfile and nginx". Claude checked the backend's path prefix (`/api` is mapped in the backend, so nginx doesn't strip it) and pinned the image tags from Docker Hub.
  2. Claude wrote a multi-stage `frontend/Dockerfile` (`node:22.23.4-alpine` → `nginx:1.30.6-alpine`, `nginx -t` at build time), `nginx.conf`, two include files (proxy headers, security headers), and the `frontend` compose service on `127.0.0.1:80:80`.
  3. Claude updated `.claude/rules/infrastructure.md` and `docs/TODO.md`.
- **Testing:** `/run-stack` (`up.sh`): all 4 services healthy. curl checks: CSP/nosniff/Referrer-Policy on the SPA but not on `/auth/`, the deep-link fallback, `/actuator` not exposed, not reachable from the LAN IP. A real `admin1` token via auth code + PKCE through nginx (`iss=http://localhost/auth/realms/credits`, `azp=credits-web`): GET/POST/PUT/DELETE with `Origin: http://localhost` reach the backend (404, no endpoint yet), a foreign Origin gets 403.
- **AI tooling used:** Claude Code inline, infrastructure rule, run-stack skill script.
- **Key prompt(s):**
  > add the Dockerfile and nginx
- **Issues / fixes:** (1) Claude inspected the built `index.html` and found that Angular's critical-CSS inlining adds an inline `<script>`, which the CSP (`script-src 'self'`) blocks, so the page would load without its main stylesheet → set `inlineCritical: false` instead of weakening the CSP. (2) Security headers at server level would also land on `/auth/` and break Keycloak's login-status iframe → included them only in the SPA locations. (3) The test of nginx's per-request DNS (recreating the backend) was inconclusive: the container got the same IP back.
- **Evidence:** `bash .claude/skills/run-stack/scripts/up.sh`; `curl -sD - -o /dev/null http://localhost/`

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

### 2026-10-26 — Frontend login with keycloak-angular + security review
- **Approach:** keycloak-angular 22's functional API: the whole app requires login (`login-required`, PKCE S256), and the bearer token is attached only to requests matching an anchored `/api` regex. Role checks live in one `AuthService`, and `adminGuard` is built with `createAuthGuard`.
- **Workflow:**
  1. User: "add keycloak-angular". Claude read the library's type definitions and source to confirm the API, then wrote the auth config, `AuthService`, `adminGuard` and the environment file.
  2. User: "let's make a security review". The `security-reviewer` subagent ran a read-only audit of the frontend auth code and the realm client: no High findings, 2 Medium (Google Fonts leak the user's IP and need a looser CSP; no CSP yet).
  3. The user chose to fix them now: fonts self-hosted from npm (`@fontsource/roboto`, `material-symbols`), new negative test cases, and the rest recorded in `docs/TODO.md` (nginx headers, stricter logout redirect, brute-force protection).
- **Testing:** Vitest: the token is attached to `/api…` (after a refresh) and not to `/apix`, `/API`, absolute or protocol-relative URLs, Keycloak or Google Fonts. Also `isAdmin`/`username`/`logout` and the guard for ADMIN, USER and logged-out users.
- **AI tooling used:** Claude Code, `security-reviewer` subagent, `frontend/CLAUDE.md` auth conventions.
- **Key prompt(s):**
  > delete the scratchpad, then add keycloak-angular
  > let's make a security review
- **Issues / fixes:** The `security-reviewer` agent is written for the backend; the prompt redirected it to the frontend files and the keycloak-angular source. The user asked where the token was stored (screenshot of Safari's Storage tab): it's in memory only by design, and the visible cookies are Keycloak's own session cookies.
- **Evidence:** `npm --prefix frontend test -- --watch=false`

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

### 2026-10-26 — Angular skeleton, layout shell, HTTP error handling
- **Approach:** Claude first asked the user, open-ended, how the frontend should look. The user decided: credits get their own list, the conventions in `frontend/CLAUDE.md` stand, and the first step is only the skeleton. Then a top horizontal menu instead of the proposed sidenav. Errors are handled centrally in one interceptor, so pages don't repeat it.
- **Workflow:**
  1. User: "let's build the frontend structure". `ng new` failed because Angular 22 needs Node ≥ 22.22.3 and only Node 20 (EOL) was installed. Claude asked; the user chose Node 22 + Angular 22. Claude scaffolded in the scratchpad (zoneless, Vitest, SCSS), copied it into `frontend/`, and ran `ng add @angular/material` (azure/blue M3 theme).
  2. "now add the layout shell": Claude proposed an ASCII mockup and the user changed it to a top horizontal menu. Claude built the `Shell` (toolbar with Clients/Credits, username, admin icon, logout), lazy-loaded feature routes, placeholder list pages and a not-found page.
  3. "add the HTTP error interceptor": 401 → login again; 400 with `errors` → left to the form; anything else → the backend's `detail` in a snackbar. Also an `authenticatedGuard` and a "login service unavailable" page for a failed Keycloak init.
- **Testing:** Vitest 3 → 36 → 76 specs, all green after each step; `npm run build` OK; the container rebuilt and smoke-tested.
- **AI tooling used:** Claude Code inline, Angular CLI (`ng new`, `ng add`, `ng g component`), AskUserQuestion, `frontend/CLAUDE.md` (JSDoc on everything, Material only, feature folders).
- **Key prompt(s):**
  > let's build the frontend structure
  > let the menu is top horizontal
  > add the HTTP error interceptor and delete the login test script
- **Issues / fixes:** (1) Node/Angular version mismatch, found only when the CLI refused to run. (2) A spec built TestBed from the full `appConfig`, which would run the real Keycloak init → moved the icon setting into `provideMaterialDefaults()`. (3) The interceptor spec didn't compile (`unknown` body type) → fixed the type. (4) Limitation: if Keycloak is down at page load, the user sees nginx's 502 page, not the app's "unavailable" page.
- **Evidence:** `npm --prefix frontend test -- --watch=false` (76 passed); http://localhost after logging in as `user1` and `admin1` (screenshot candidate: the toolbar with the admin icon)

## Testing

# TODO — next session

_Last updated: 2026-10-26. Backend skeleton and infrastructure (everything in Docker, one mode) are done; no domain entities yet._

## Before starting (user)
- [ ] `git add -A` and commit the AI setup as its own commit (the index still holds older staged versions)
- [ ] Restart Claude Code in the project and check that `/agents`, `/memory` and `/hooks` list everything
- [x] Install JDK 21
- [x] Node 22.23.4 installed with nvm (Angular 22 needs 22.22.3+; Node 20 is EOL). Run `nvm use 22` (or `nvm alias default 22`) before frontend commands; the shell default is still 16 (decided 2026-10-26)
- [ ] Decide whether to use GitHub Copilot for the tool comparison. If not, delete `.github/copilot-instructions.md`

## Choices Claude made that still need confirmation
- [x] Backend layer packages are top-level (`com.creditsai.controller`, ...), not per feature
- [x] Extra packages: `mapper`, `exception`, `security`
- [x] IDs are `BIGINT GENERATED ALWAYS AS IDENTITY` (`Long`), not UUID
- [x] Liquibase: `db.changelog-master.yaml` with `includeAll` over `changes/NNN-description.sql`
- [x] Keycloak `onLoad: 'login-required'` (the whole app requires login) (confirmed 2026-10-26)
- [x] Keycloak 26.8.1. Full stack: nginx is the single entry point on `http://localhost` (`/`, `/api/`, `/auth/`), so production needs no CORS. Plain localhost (no custom domain), so anyone can test it locally (decided 2026-10-26)
- [x] Backend accepts only tokens with `azp=credits-web` (decided 2026-10-26)
- [x] No Keycloak client for the backend (it only validates tokens)
- [x] Spring Boot 4.1.2 instead of 3.5 (3.5 is out of OSS support; decided 2026-10-26)
- [x] No dev/prod modes: everything runs in Docker, in development too. Rebuild with `docker compose up -d --build --wait` after each change; no live reload, no debug port; only port 80 is published (decided 2026-10-26, replaces the earlier IntelliJ dev mode)

## Open design questions (asked, not answered yet)
- [x] **Client fields:** name (≤100), identifier (≤50, free text for Bulgarian/foreign persons and companies, NOT unique), address (≤200), all required; description optional (≤500). ADMIN can edit every field (decided 2026-10-27)
- [ ] **Credit fields:** amount, currency, interest rate, term, start date, type?
- [ ] **Credit statuses:** which ones define an "active credit" (e.g. `ACTIVE`, `PAID_OFF`, `CLOSED`)? Can an active credit itself be archived?
- [x] **Archived records:** ADMIN can view (`GET /api/clients?archived=true`, and by id), edit and restore (`POST /api/clients/{id}/restore`) them; USER gets 403 on the list and 404 by id. Archive/restore rules live in the entity (decided 2026-10-27)
- [x] **Paging:** max page size 100 (`spring.data.web.pageable.max-page-size`), sort only by an allow-list per resource (unknown field → 400) (decided 2026-10-27)
- [ ] **Business logic:** comes later, one feature at a time (good candidates for Strategy/Factory)

## Next build steps
1. [~] **Module 1, Infrastructure:** one compose file, one mode (see "One mode" in `.claude/rules/infrastructure.md`).
   - [x] postgres, `keycloak` under `/auth`, `backend`, realm, `.env.example`. The stack was verified on 2026-10-26: all services healthy, a real `credits-web` PKCE token has `iss=http://localhost/auth/realms/credits` and the backend container accepts it
   - [x] realm: only `http://localhost` in redirect URIs, web origins and post-logout redirect (the `localhost:4200` entries were removed). The realm was re-imported on 2026-10-26 (old realm deleted with `kcadm` inside the container, Keycloak restarted)
   - [x] nginx is the frontend container (no separate proxy) (decided 2026-10-26)
   - [x] simplified to one mode: no compose/Spring profiles, one Keycloak service, one `application.yml`, no CORS origins, Postgres without host port (decided 2026-10-26)
   - [ ] `.env.example` (user edits; Claude can't read it): drop the "backend also reads this file ... from IntelliJ" comment, and change the admin console URL from `http://localhost:8180` to `http://localhost/auth/admin`
   - [x] `frontend` port is published on loopback only, `127.0.0.1:80:80`, so the LAN can't reach the app or the Keycloak admin console at `/auth/admin` (decided 2026-10-26)
   - [x] HTTP error interceptor (2026-10-26): 401 → `login()`, `detail` in a snackbar, 400 with `errors` left to the form; `authenticatedGuard` sends a failed Keycloak init to `/unavailable` (76 specs); nginx shows `nginx-auth-unavailable.html` when Keycloak is unreachable (verified with an isolated container: 502 + the page, Keycloak's own 404 JSON still passes through)
   - [x] `frontend` service (`127.0.0.1:80:80`) + `frontend/Dockerfile` + `frontend/nginx.conf`: `/` SPA, `/api/` → `backend:8080`, `/auth/` → `keycloak:8080` with overwritten X-Forwarded headers. Verified 2026-10-26: all 4 services healthy, PKCE token through nginx reaches the backend on GET/POST/PUT/DELETE with `Origin: http://localhost`, foreign Origin 403, not reachable from the LAN
   - [x] frontend `environment.ts` (the only one): `apiUrl: '/api'`, Keycloak `url: 'http://localhost/auth'`
   - [ ] optional: Keycloak in production mode (`start` instead of `start-dev`) with a realm import without the test users
   - [ ] optional: `KC_PROXY_TRUSTED_ADDRESSES` = the compose subnet for Keycloak
   - [x] `nginx.conf` security headers (from the frontend security review, 2026-10-26; Angular `inlineCritical` turned off so the CSP needs no inline script): CSP `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; font-src 'self'; img-src 'self' data:; connect-src 'self'; frame-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'; object-src 'none'` on the SPA location only (not on `/auth/`, Keycloak sends its own and its login-status iframe must load), `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `Cache-Control: no-cache` for `index.html`, `server_tokens off`, never log `$http_authorization`
   - [ ] at the next realm re-import (ask first, needs realm delete or volume reset): `post.logout.redirect.uris` → exactly `http://localhost/`, and `"bruteForceProtected": true`
2. [~] **Module 2–4, Backend:** ~~Spring Boot skeleton, security config, error handling, `azp` restriction~~ done (30 tests; Spring profiles and CORS properties removed 2026-10-26). ~~Client resource~~ done 2026-10-27 (104 tests, security review OK, its 2 Medium findings fixed). Next: `/new-endpoint Credit` once the fields are decided (then add the "active credit blocks archiving" check to `Client.archive()`)
3. [~] **Module 5, Frontend:** ~~Angular 22 + Material skeleton and theme~~ done; ~~keycloak-angular 22 (login-required + PKCE, bearer token only for `/api`, `AuthService.isAdmin()`, `adminGuard`)~~ done, not yet tried in a browser (2026-10-26, 23 specs). Credits get their own list, not nested under a client (decided 2026-10-26). Fonts self-hosted (no Google Fonts). Docker + nginx done. ~~Layout shell~~ done (2026-10-26, 36 specs): top horizontal menu (Clients, Credits) in a `mat-toolbar`, no sidenav (user's choice); username, admin icon, logout; `/` → `/clients`; not-found page; placeholder list pages. Next: the clients and credits features
4. [ ] **Module 6, Testing:** Testcontainers integration tests, frontend specs
   - [ ] E2E tests: Cypress or Playwright (undecided, user decides later). Cypress: interactive runner, good for demos, Keycloak login on the same origin works with `cy.session()`. Playwright: the same tool as the planned Playwright MCP (item 5), faster parallel runs, WebKit. Open questions: test data (nothing is deleted, so use unique names per test or a throwaway compose stack), run against the Docker stack on `http://localhost` (not `ng serve`), location (`frontend/cypress/` vs a top-level `e2e/`). Start with one smoke test (login, then the shell loads) and add scenarios per feature: no edit/archive for USER, ADMIN archive with the confirm dialog, the 409 active-credit message, a 400 mapped onto form fields
5. [ ] Re-add the Playwright MCP (`.mcp.json`) once the UI exists, for screenshots
6. [ ] Run `/exam-log` after each module. Write the Google Doc from `docs/ai-log.md` and `docs/prompts.log`

## Report material collected so far
- Challenge: the compose healthcheck using bash `/dev/tcp` worked for Keycloak but not for the backend, because `CMD-SHELL` runs `/bin/sh`, which is `dash` in the Temurin image. Fixed by calling bash explicitly. Found only by actually running the full stack.
- Design discussion: a custom domain + network alias would give a single Keycloak URL, but would make local testing harder for others (the user's point). Kept plain localhost with the issuer/JWKS split and nginx as the single entry point.
- Challenge: start.spring.io no longer offered Boot 3.5, so the planned version was outdated. Moved to Boot 4.1, where test annotations moved packages (`org.springframework.boot.webmvc.test...`).
- Challenge: IntelliJ didn't detect `backend/pom.xml` (no root pom), and the first run configuration was a Maven one with no goals ("No goals have been specified"). Fixed by adding the pom as a Maven project and running `BackendApplication` from the gutter with JDK 21.
- Verification beyond unit tests: ran a throwaway compose stack, got a real token via the auth-code + PKCE flow with curl, and confirmed the backend accepts it (issuer + JWKS) and rejects missing/bad tokens with 401.
- Challenge: the first AI setup was based on assumed architecture and was over-engineered (about 20 files, with rules repeated in many places). It was trimmed to one place per rule, and design decisions are now asked of the user first.
- Deterministic enforcement through a PreToolUse hook rather than prompts: native SQL, physical deletes, reactive forms, edits to existing Liquibase changesets, and `.env` are all blocked.
- Expected challenge: the Keycloak issuer mismatch in Docker (`localhost/auth` vs `keycloak:8080`), solved with the issuer/JWKS split.

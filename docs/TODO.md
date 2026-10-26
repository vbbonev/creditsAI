# TODO — next session

_Last updated: 2026-10-26. Backend skeleton and infrastructure (everything in Docker, one mode) are done; no domain entities yet._

## Before starting (user)
- [ ] `git add -A` and commit the AI setup as its own commit (the index still holds older staged versions)
- [ ] Restart Claude Code in the project and check that `/agents`, `/memory` and `/hooks` list everything
- [x] Install JDK 21
- [ ] Switch to Node 20+ (`nvm install 20 && nvm use 20`, currently 16), needed only for frontend tests and `ng` scaffolding; the build runs in Docker
- [ ] Decide whether to use GitHub Copilot for the tool comparison. If not, delete `.github/copilot-instructions.md`

## Choices Claude made that still need confirmation
- [x] Backend layer packages are top-level (`com.creditsai.controller`, ...), not per feature
- [x] Extra packages: `mapper`, `exception`, `security`
- [x] IDs are `BIGINT GENERATED ALWAYS AS IDENTITY` (`Long`), not UUID
- [x] Liquibase: `db.changelog-master.yaml` with `includeAll` over `changes/NNN-description.sql`
- [ ] Keycloak `onLoad: 'login-required'` (the whole app requires login)
- [x] Keycloak 26.8.1. Full stack: nginx is the single entry point on `http://localhost` (`/`, `/api/`, `/auth/`), so production needs no CORS. Plain localhost (no custom domain), so anyone can test it locally (decided 2026-10-26)
- [x] Backend accepts only tokens with `azp=credits-web` (decided 2026-10-26)
- [x] No Keycloak client for the backend (it only validates tokens)
- [x] Spring Boot 4.1.2 instead of 3.5 (3.5 is out of OSS support; decided 2026-10-26)
- [x] No dev/prod modes: everything runs in Docker, in development too. Rebuild with `docker compose up -d --build --wait` after each change; no live reload, no debug port; only port 80 is published (decided 2026-10-26, replaces the earlier IntelliJ dev mode)

## Open design questions (asked, not answered yet)
- [ ] **Client fields:** which ones, which are required, which are unique (e.g. EGN)?
- [ ] **Credit fields:** amount, currency, interest rate, term, start date, type?
- [ ] **Credit statuses:** which ones define an "active credit" (e.g. `ACTIVE`, `PAID_OFF`, `CLOSED`)? Can an active credit itself be archived?
- [ ] **Archived records:** can an ADMIN see them (`?includeArchived=true`) or restore them?
- [ ] **Business logic:** comes later, one feature at a time (good candidates for Strategy/Factory)

## Next build steps
1. [~] **Module 1, Infrastructure:** one compose file, one mode (see "One mode" in `.claude/rules/infrastructure.md`).
   - [x] postgres, `keycloak` under `/auth`, `backend`, realm, `.env.example`. The stack was verified on 2026-10-26: all services healthy, a real `credits-web` PKCE token has `iss=http://localhost/auth/realms/credits` and the backend container accepts it
   - [x] realm: only `http://localhost` in redirect URIs, web origins and post-logout redirect (the `localhost:4200` entries were removed). The realm was re-imported on 2026-10-26 (old realm deleted with `kcadm` inside the container, Keycloak restarted)
   - [x] nginx is the frontend container (no separate proxy) (decided 2026-10-26)
   - [x] simplified to one mode: no compose/Spring profiles, one Keycloak service, one `application.yml`, no CORS origins, Postgres without host port (decided 2026-10-26)
   - [ ] `.env.example` (user edits; Claude can't read it): drop the "backend also reads this file ... from IntelliJ" comment, and change the admin console URL from `http://localhost:8180` to `http://localhost/auth/admin`
   - [x] `frontend` port is published on loopback only, `127.0.0.1:80:80`, so the LAN can't reach the app or the Keycloak admin console at `/auth/admin` (decided 2026-10-26)
   - [ ] `frontend` service (`127.0.0.1:80:80`) + `frontend/nginx.conf`: `/` SPA, `/api/` → `backend:8080`, `/auth/` → `keycloak:8080` with X-Forwarded headers
   - [ ] frontend `environment.ts` (the only one): `apiUrl: '/api'`, Keycloak `url: 'http://localhost/auth'`
   - [ ] optional: Keycloak in production mode (`start` instead of `start-dev`) with a realm import without the test users
   - [ ] optional: `KC_PROXY_TRUSTED_ADDRESSES` = the compose subnet for Keycloak
2. [~] **Module 2–4, Backend:** ~~Spring Boot skeleton, security config, error handling, `azp` restriction~~ done (30 tests; Spring profiles and CORS properties removed 2026-10-26). Next: `/new-endpoint Client` and `/new-endpoint Credit` once the fields are decided
3. [ ] **Module 5, Frontend:** Angular + Material skeleton, keycloak-angular, then the clients and credits features
4. [ ] **Module 6, Testing:** Testcontainers integration tests, frontend specs
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

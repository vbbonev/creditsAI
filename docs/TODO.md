# TODO — next session

_Last updated: 2026-10-28. Infrastructure, backend skeleton, the Client feature (backend + frontend) and client attachments are done. Credit backend done (523 tests); Credit frontend done (319 specs), not yet checked in a browser._

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
- [x] **Credit fields** (decided 2026-10-28): auto-generated `creditNumber` (`CR-{yyyy}-{000123}` from a DB sequence), client (autocomplete by name/identifier), type (`CONSUMER`, `MORTGAGE`, `AUTO`, `BUSINESS`), amount, currency (`EUR`, `USD`, `CHF`), fixed annual interest rate (%), start and end date (whole number of months apart), collateral flag; if set, collateral value (in the credit's currency) and description (≤500) are required. Limits in `app.credits`: amount > 0 up to 100,000,000, rate 0–100 %, term 1–480 months, start date may be in the past
- [x] **Payment plan** (decided 2026-10-28): annuity, 30/360 (monthly rate = annual/12), payment dates on the same day of month as the start date (clamped to month end, e.g. 31 Jan → 28 Feb), instalment and interest rounded to 2 decimals HALF_UP, last payment absorbs the rounding difference. `POST /api/credits/calculate` is a stateless preview; on save the server ignores any client-sent plan and recalculates. The plan is stored as rows in `credit_payments`; ADMIN edit regenerates it
- [x] **Credit statuses** (decided 2026-10-28): explicit `ACTIVE`/`INACTIVE`, new credits are `ACTIVE`, only ADMIN changes status. An ACTIVE credit can't be archived (deactivate first). Archived credits mirror the Client rules (ADMIN view/edit/restore, USER 403/404). No new credits for an archived client
- [x] **Credit list** (decided 2026-10-28): one list across all clients, text search (credit number, client name, client identifier) and status filter
- [x] **Credits of archived clients** (decided 2026-10-28, from the security review): USER sees them like archived credits (404 by id, hidden from list/search); ADMIN sees them with `includeArchived`. Creating a credit for an archived client returns 404, same as a missing client. Dates: start ≥ 1900-01-01, end ≤ 2200-12-31. Money columns NUMERIC(12,2)
- [x] **Credit UI** (decided 2026-10-28): full pages, no dialogs: `/credits/new` and `/credits/:id/edit` (ADMIN) with inputs → Calculate → plan → Save. Save is enabled only after a successful Calculate, any input change clears the plan, and there is no auto-recalculation. Not startable from the client detail page. List columns: number, client, type, amount + currency, rate, start, end, monthly payment, status; search + status filter; ADMIN archived toggle. Detail page: ADMIN Edit / Activate-Deactivate / Archive (only INACTIVE, confirm) / Restore. Formats `€10,000.00` and `dd.MM.yyyy`. The plan table scrolls (sticky header, no paginator). Edit page shows the saved plan with Save enabled until an input changes. Confirmed frontend choices: currency defaults to EUR; archived credit shows only Restore; status change updates in place, archive/restore return to the list; autocomplete 300 ms, max 10 non-archived clients; list sortable by number, amount, start, end; timestamps `dd.MM.yyyy HH:mm`
- [x] **Credit implementation choices** (implementer, 2026-10-28; recommended to accept, user has not objected): business-rule errors are 409 (existing handler); ADMIN may edit a credit of an archived client if it stays on that client; `term_months` and `monthly_payment` stored on `credits`; restoring a credit of an archived client is allowed (stays INACTIVE); new `validation` package; `currency` required in `/calculate` though unused
- [x] **Archived records:** ADMIN can view (`GET /api/clients?archived=true`, and by id), edit and restore (`POST /api/clients/{id}/restore`) them; USER gets 403 on the list and 404 by id. Archive/restore rules live in the entity (decided 2026-10-27)
- [x] **Client attachments** (decided 2026-10-28):
  - Files live on disk in the `attachments-data` volume, under `/data/attachments/{clientId}/{name}`, not as BLOBs.
  - Names are stored in lower case. A duplicate name in the same client is rejected (409).
  - Allowed types: PDF, DOCX, XLSX, ODT, TXT, JPEG, PNG, GIF, WEBP, HEIC, TIFF. They are checked by content.
  - Limits (`app.attachments`): 5 MB per file, 20 MB and 30 files per client.
  - A USER attaches files only in the multipart create. After that only ADMIN adds or deletes, and an archived client's attachments are ADMIN-only.
  - Attachments are physically deleted, the one exception to soft delete.
  - Picture preview for JPEG/PNG/GIF/WEBP.
  - Hourly cleanup job: it deletes stale `.deleted-*`/`.upload-*.tmp` files and only reports orphans.
- [ ] **Attachments, small open choices:**
  - Should the 409 quote the uploaded name instead of the stored lower-case one?
  - The 413 for more than 32 multipart parts talks about size limits.
  - Keep or remove the Download icon now that clicking the file name downloads?
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
   - [x] attachments volume `attachments-data`: mounted only into backend, `/data/attachments` is `app:app 0750`, files 0640 (a named volume can't be mounted `noexec`), nginx `/api/` has `client_max_body_size 21m`, CSP `img-src blob:` (2026-10-28)
   - [ ] at the next realm re-import (ask first, needs realm delete or volume reset): `post.logout.redirect.uris` → exactly `http://localhost/`, and `"bruteForceProtected": true`
2. [~] **Module 2–4, Backend:** ~~Spring Boot skeleton, security config, error handling, `azp` restriction~~ done (30 tests; Spring profiles and CORS properties removed 2026-10-26). ~~Client resource~~ done 2026-10-27 (104 tests, security review OK, its 2 Medium findings fixed). ~~Client attachments~~ done 2026-10-28 (288 tests). The security review found 1 High (Tika temp copies) and 1 Medium (delete/re-upload race), plus 3 Low; all are fixed. Changesets 002–004 and a cleanup job were added. Untested: the HALT path of changeset 004 against mixed-case rows. ~~Credit resource~~ done 2026-10-28 (523 tests): payment plan calculator (annuity 30/360), `/api/credits` CRUD + `/calculate`, `/status`, `/restore`; changesets 005–006; `Client.archive()` blocks active credits. Security review: no High; 1 Medium (archived clients' credits visible to USER) and 2 Low (existence probe on create, unbounded dates) fixed. Not yet tried against the Docker stack
3. [~] **Module 5, Frontend:** ~~Angular 22 + Material skeleton and theme~~ done; ~~keycloak-angular 22 (login-required + PKCE, bearer token only for `/api`, `AuthService.isAdmin()`, `adminGuard`)~~ done, not yet tried in a browser (2026-10-26, 23 specs). Credits get their own list, not nested under a client (decided 2026-10-26). Fonts self-hosted (no Google Fonts). Docker + nginx done. ~~Layout shell~~ done (2026-10-26, 36 specs): top horizontal menu (Clients, Credits) in a `mat-toolbar`, no sidenav (user's choice); username, admin icon, logout; `/` → `/clients`; not-found page; placeholder list pages. ~~Clients feature~~ done 2026-10-27 (list/search, form dialog, detail page). ~~Client attachments UI~~ done 2026-10-28 (183 specs): files in the create dialog, a Files section on the detail page, thumbnails and a preview dialog, and the file name click downloads. **Not yet checked in a browser.** Next: the credits feature
   - [ ] The build warns that the initial bundle is over the 500 kB budget: 570 kB before credits, 603 kB after the Credits feature (2026-10-28, datepicker/date adapter in the eager `core/material` providers). Decide whether to raise the budget or trim the bundle
4. [ ] **Module 6, Testing:** Testcontainers integration tests, frontend specs
   - [ ] E2E tests: Cypress or Playwright (undecided, user decides later). Cypress: interactive runner, good for demos, Keycloak login on the same origin works with `cy.session()`. Playwright: the same tool as the planned Playwright MCP (item 5), faster parallel runs, WebKit. Open questions: test data (nothing is deleted, so use unique names per test or a throwaway compose stack), run against the Docker stack on `http://localhost` (not `ng serve`), location (`frontend/cypress/` vs a top-level `e2e/`). Start with one smoke test (login, then the shell loads) and add scenarios per feature: no edit/archive for USER, ADMIN archive with the confirm dialog, the 409 active-credit message, a 400 mapped onto form fields
5. [ ] Re-add the Playwright MCP (`.mcp.json`) once the UI exists, for screenshots
6. [ ] Run `/exam-log` after each module. Write the Google Doc from `docs/ai-log.md` and `docs/prompts.log`

## Report material collected so far
- Security review value (attachments): it caught an unclosed `TikaInputStream` that left a full copy of every DOCX/XLSX in `/tmp`, so a disk could fill and deleted files survived. It also caught a delete-after-commit race that could wipe a re-uploaded file with the same name. Both were invisible to the functional tests.
- Hook vs. a user decision: the `protect-files.sh` hook blocks `repository.delete*`, but the user chose physical deletion for attachments. It was solved through `orphanRemoval` in the `Client` aggregate and documented as the one exception, not by weakening the hook.
- Iterative design: duplicate names were first case-sensitive, then case-insensitive with a functional index, then lowercase-only with a plain unique constraint. Each step was one changeset, never an edit to an old one.
- Housekeeping: during cleanup, two integration tests turned out to leave a temp storage folder after every run (38 folders) → fixed with `@AfterAll`.
- Challenge: the compose healthcheck using bash `/dev/tcp` worked for Keycloak but not for the backend, because `CMD-SHELL` runs `/bin/sh`, which is `dash` in the Temurin image. Fixed by calling bash explicitly. Found only by actually running the full stack.
- Design discussion: a custom domain + network alias would give a single Keycloak URL, but would make local testing harder for others (the user's point). Kept plain localhost with the issuer/JWKS split and nginx as the single entry point.
- Challenge: start.spring.io no longer offered Boot 3.5, so the planned version was outdated. Moved to Boot 4.1, where test annotations moved packages (`org.springframework.boot.webmvc.test...`).
- Challenge: IntelliJ didn't detect `backend/pom.xml` (no root pom), and the first run configuration was a Maven one with no goals ("No goals have been specified"). Fixed by adding the pom as a Maven project and running `BackendApplication` from the gutter with JDK 21.
- Verification beyond unit tests: ran a throwaway compose stack, got a real token via the auth-code + PKCE flow with curl, and confirmed the backend accepts it (issuer + JWKS) and rejects missing/bad tokens with 401.
- Challenge: the first AI setup was based on assumed architecture and was over-engineered (about 20 files, with rules repeated in many places). It was trimmed to one place per rule, and design decisions are now asked of the user first.
- Deterministic enforcement through a PreToolUse hook rather than prompts: native SQL, physical deletes, reactive forms, edits to existing Liquibase changesets, and `.env` are all blocked.
- Expected challenge: the Keycloak issuer mismatch in Docker (`localhost/auth` vs `keycloak:8080`), solved with the issuer/JWKS split.

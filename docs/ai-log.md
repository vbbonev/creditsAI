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

### 2026-10-27 — Client resource (CRUD + archive/restore)
- **Approach:** Claude asked about the design before writing code (identifier format and uniqueness, lengths, archived visibility, scope). Decisions: `identifier` is free text and not unique (foreign persons/companies), ADMIN can view, edit and restore archived clients, backend only. Archive/restore rules live in the entity (`client.archive()`/`restore()`, no setters for `archived`), which the user chose after asking what that meant.
- **Workflow:**
  1. Claude asked 6 design questions, then presented a plan via the `/new-endpoint` skill (table, endpoints, queries) for confirmation.
  2. The `backend-dev` subagent wrote the changeset `001-create-clients.sql`, entity, repository (derived queries), DTOs, MapStruct mapper, service + impl, and controller.
  3. `test-writer` and `security-reviewer` ran in parallel. The review found no High issues and 2 Medium: unknown sort field → 500, and no page-size cap.
  4. The user chose a max page size of 100 plus a sort allow-list. Claude added `InvalidRequestException` (400), a `PropertyReferenceException` → 400 fallback, the allow-list in the controller, and `@P("archived")`.
- **Testing:** 74 new tests: entity, service (Mockito), controller role matrix (`@WebMvcTest`), repository and full-flow integration (Testcontainers Postgres). The whole suite has 104 tests, all green.
- **AI tooling used:** Claude Code: `/new-endpoint` skill, `backend-dev`, `test-writer` and `security-reviewer` subagents, the liquibase, jpa-repositories, security and java-tests rules.
- **Key prompt(s):**
  > let's implement the Client entity. The required fields are id, name, identifier, address and description (optional field)
  > 100 is ok, use the 400 handler plus allow-list
- **Issues / fixes:** (1) Bash permission checks failed transiently ("auto mode classifier gave no verdict"), so the subagent couldn't compile; the user ran `! backend/mvnw ... compile` by hand. (2) The agent put `@EnableSpringDataWebSupport(VIA_DTO)` on the app class, which makes Boot's auto-configuration step aside and silently ignore `spring.data.web.pageable.*` such as `max-page-size` → moved to `application.yml`. (3) Spring Data 4 moved `PropertyReferenceException`/`PropertyPath` to `org.springframework.data.core` (compile error). (4) An old test expected 404 on `/api/clients` because the endpoint hadn't existed → now 200.
- **Evidence:** `backend/mvnw -f backend/pom.xml test` → Tests run: 104, Failures: 0.

### 2026-10-28 — Client attachments: files on disk, limits, cleanup job
- **Approach:** Claude asked about seven design points first (file types, roles, delete, archived clients, storage, limits, UI). Then three follow-up multiple-choice questions on how a USER attaches files to a "new" client, duplicate names, and noexec. The user decided:
  - The backend checks real content (magic bytes).
  - A USER attaches files only in the create request (one atomic multipart POST). After that only ADMIN adds or deletes.
  - Attachments are physically deleted, the one exception to soft delete.
  - Storage is a named volume with one folder per client and real names (later lowercased).
  - Limits: 5 MB per file, 20 MB and 30 files per client, configurable.
- **Workflow:**
  1. `backend-dev` and `frontend-dev` ran in parallel against an API contract written by Claude. Claude did the infra itself: the `attachments-data` volume, a 0750 folder in the Dockerfile, nginx `client_max_body_size 21m`, CSP `img-src blob:`, plus updates to CLAUDE.md and the infrastructure rule.
  2. `security-reviewer` found 1 High, 1 Medium and 3 Low. All were fixed by `backend-dev`.
  3. The user then asked for case-insensitive names (changeset 003), then lowercase-only names (changeset 004, which halts if non-lowercase rows exist). Last came a cleanup job for leftover files: it deletes stale `.deleted-*`/`.upload-*.tmp` files and only reports orphans.
- **Testing:** backend 111 → 288 tests (Tika detection with real sample bytes, sanitizer, `@TempDir` storage, entity rules, WebMvc role matrix, Testcontainers integration, a real-Tomcat 413 test, 6 parallel uploads against the row lock), all green. Claude also checked the running stack through nginx with curl and a PKCE token script: rights, 415 spoofing with rollback, 413, 409, 0640 files on disk, download headers, and cleanup logs with planted files.
- **AI tooling used:** Claude Code, the `backend-dev`/`frontend-dev`/`security-reviewer` subagents (resumed with SendMessage for each change), AskUserQuestion, the liquibase/security/java-tests rules, and the `protect-files.sh` hook.
- **Key prompt(s):**
  > let's add the possibility to attach file to the Client ... saved in the file system not as BLOB in DB ... 1 file max 5MB, all files attached to a client - max 20MB
  > remind me about the situation in which after deletion a file can stay on the server
- **Issues / fixes:**
  1. The hook blocks `repository.delete*`, so physical delete was done through `orphanRemoval` in the `Client` aggregate.
  2. Review High: an unclosed `TikaInputStream` left a full copy of every DOCX/XLSX in `/tmp` → try-with-resources.
  3. Review Medium: a delete-after-commit race with a same-name re-upload → a tombstone rename under the row lock.
  4. tika-core can't tell DOCX/XLSX from zip → a small JDK `OoxmlInspector`.
  5. The frontend compared names case-insensitively and the backend did not → aligned.
  6. During cleanup Claude found 38 leftover `attachments-it*`/`attachments-http*` folders in the system temp dir: the two integration tests created a storage folder per run and never removed it → `@AfterAll` with `FileSystemUtils.deleteRecursively`; after a full run (288 green) nothing is left behind.
- **Evidence:** `backend/mvnw -f backend/pom.xml test` (288 passed); `docker compose logs backend | grep "Attachment cleanup"`

### 2026-10-28 — Credit resource with payment plan (annuity, 30/360)
- **Approach:** Claude asked open questions first (repayment type, day-count convention, dates, rounding, calculate/save flow, statuses), then two rounds of multiple-choice questions on the details. The user decided:
  - Annuity with 30/360 (the user changed it from ACT/365 "for simplicity"). Payment dates fall on the same day of the month, clamped to month end, and the end date must be a whole number of months after the start. The last payment absorbs the rounding difference.
  - `/calculate` is a stateless preview. On save the server recalculates from the inputs and stores the plan as rows.
  - An auto-generated credit number, type, and collateral (value + description).
  - An explicit ACTIVE/INACTIVE status that only ADMIN changes. An ACTIVE credit can't be archived. One credit list with search and status filter.
- **Workflow:**
  1. The `/new-endpoint` skill led to `backend-dev`, which built changesets 005/006, the entity, the calculator, the class-level validators, the service and the controller. It also added the "active credit blocks client archive" rule.
  2. Claude reviewed the calculator. Then `test-writer` and `security-reviewer` ran in parallel.
  3. The review had no High findings, 1 Medium and 2 Low. The user chose the fixes, `backend-dev` applied them (resumed via SendMessage), and `test-writer` added tests for them.
- **Testing:** 288 → 487 → 523 tests, all green (Claude re-ran the full suite). Coverage: calculator known values (10,000 at 12% for 12 months gives 888.49, 0%, 1 and 480 months, 31 Jan in leap years), entity rules, validators, the WebMvc role matrix, a schema constraints test, and an API lifecycle on Testcontainers.
- **AI tooling used:** Claude Code with the `/new-endpoint` skill, the `backend-dev`/`test-writer`/`security-reviewer` subagents, AskUserQuestion, and the jpa-repositories/liquibase rules.
- **Key prompt(s):**
  > For new credit the user must select client(autocomplete by name/identifier), credit start and end date, amount, interest rate, currency(EUR,USD,CHF). Then the backend need to calculate payment plan (monthly payments using ACT/365 convention)...
  > When user saves the plan we don't trust the plan but recalculate on the server from the inputs.
- **Issues / fixes:**
  1. Review Medium: USER could see INACTIVE credits of archived clients, and with them the client's name and identifier → these credits are now hidden from USER like archived credits.
  2. Review Low: create returned 409 for an archived client, which let a USER find out which archived clients exist → now it returns the same 404 as a missing client.
  3. Review Low: dates had no bounds and the term was cast to int without a check (wraparound or 500) → start ≥ 1900 and end ≤ 2200, plus `Math.toIntExact`.
  4. Money columns were NUMERIC(15,2) against the liquibase rule's (12,2) → fixed before commit.
  5. Create and PUT responses lost the decimal scale (`5000` vs `5000.00`) → the entity normalizes the scale.
  6. Hibernate inserts before it deletes, so regenerating a plan broke the unique `(credit_id, payment_number)` → clear the rows and flush first.
- **Evidence:** `backend/mvnw -f backend/pom.xml test` → Tests run: 523, Failures: 0, Errors: 0

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

### 2026-10-27 — Client feature: list with search, form dialog, detail page
- **Approach:** Claude asked open questions about screens, columns, archived clients, actions and what happens after saving, and the user answered each one. Search needed a backend change, so the API was extended first and then the UI was built on it. The user decided that the toggle shows active and archived clients together.
- **Workflow:**
  1. User: "let's implement the client functionality in the frontend". Claude asked 5 open questions. The user chose create/edit in a dialog, the columns name/identifier/address, sort by name, 10 rows per page, search by name or identifier, an archived toggle on the list, and actions on a detail page. After a save the app returns to the list with a message.
  2. A follow-up multiple-choice question was rejected so the user could explain first ([[ask-architecture-first]] again). The user then chose active and archived together, and accepted the defaults (one box, contains, 300 ms debounce).
  3. Backend: `?search=` (a JPQL `lower(...) like`, with `%`/`_` escaped) and `?includeArchived=true` (ADMIN only, `@PreAuthorize`), which replaced the old `?archived=true`.
  4. Frontend: `ClientService`, `ClientList` (mat-table, sort, paginator, debounced search, toggle), the `ClientForm` dialog (template-driven, a `NotBlankValidator` directive, 400 field errors shown on the fields), and `ClientDetail` (Edit/Archive with a confirmation/Restore for ADMIN). Also `shared/` pieces: `ConfirmDialog`, `applyServerErrors`, `NotificationService` and `Page<T>`.
- **Testing:** backend 104 → 111 tests (repository against Testcontainers Postgres, incl. Cyrillic and a literal `%`; service; WebMvc 403 for USER; integration); frontend 76 → 141 Vitest specs; `ng build` OK. All green. The app has not been checked in a browser yet, because the stack needs a rebuild.
- **AI tooling used:** Claude Code inline, AskUserQuestion, `frontend/CLAUDE.md` and `backend/CLAUDE.md`, the `jpa-repositories` and `java-tests` rules, the PreToolUse hook, and Prettier.
- **Key prompt(s):**
  > let's implement the client functionality in the frontend
  > 1. dialog over the list; 2. The list will include name, identifier, address. Default sort is on name, default page size is 10. We will add search oportunity by name or identifier; 3. toggle on the same list; 4. button on the detail page; 5. go back to the list and show the success message
  > active and archived together, defaults are OK
- **Issues / fixes:** (1) The PreToolUse hook blocked `FormControl` even in a spec → the validator is tested with a plain control stub. (2) Claude caught a bug of its own: binding the search box to the debounced query would overwrite text typed during the debounce window → a separate `searchText` field. (3) Prettier reformatted 7 new files. (4) Claude chose on its own that Archive/Restore also return to the list (the user only specified this for saving); this was reported to the user for confirmation.
- **Evidence:** `backend/mvnw -f backend/pom.xml test` (111 passed); `npm --prefix frontend test -- --watch=false` (141 passed); after `docker compose up -d --build --wait`: http://localhost/clients as `user1` and `admin1` (screenshot candidates: the list with search, the archived toggle, the form dialog with errors, the archive confirmation)

### 2026-10-28 — Client attachments UI: upload on create, list, preview, download
- **Approach:** Following the user's decisions, files are picked in the create dialog and sent with the client in one multipart request. The detail page gets a Files section: download for everyone; upload and delete (with a "permanently deleted" confirm) for ADMIN only. Downloads and thumbnails are fetched as blobs with the bearer token, never as plain links.
- **Workflow:**
  1. `frontend-dev` built `client-attachments`, `attachment-preview-dialog`, `attachment.validation`/`constants`, a `fileSize` pipe and the multipart `createWithFiles`. Limits are mirrored for UX only.
  2. Claude added NFC normalization to the duplicate-name check, matching the backend.
  3. On the user's request, clicking a file name now downloads the file.
- **Testing:** Vitest 141 → 183 specs, all green: multipart body shape, limits and duplicates, USER vs ADMIN visibility, thumbnail URL revocation, download on name click. `npm run build` OK. The UI was not checked in a browser.
- **AI tooling used:** the `frontend-dev` subagent, Claude Code inline edits, and `frontend/CLAUDE.md`.
- **Key prompt(s):**
  > A section on the client detail/edit page. Let the pictures have preview
  > in the frontend i want if user click on the file name to download the attachment
- **Issues / fixes:**
  1. HEIC/TIFF can't be previewed by most browsers → icon + download only.
  2. The CSP needed `img-src blob:` for thumbnails.
  3. The build warns that the initial bundle is over the 500 kB budget. It's caused by the vendor chunks, not this feature.
- **Evidence:** `npm --prefix frontend test -- --watch=false` (183 passed); http://localhost/clients/{id} as `user1`/`admin1` (screenshot candidates: the create dialog with files, the Files section with thumbnails, the preview dialog)

## Testing

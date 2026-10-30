# creditsAI

A small system for registering clients and their credits, built for the *AI-Assisted Development* course.
Any logged-in **USER** can register clients and credits. Only an **ADMIN** can edit or delete them.

## Modules
| Module | Location | Tech |
|---|---|---|
| Infrastructure | `docker-compose.yml`, `docker/`, `keycloak/import/` | Docker Compose, one mode for development and demos: postgres (app + keycloak DBs), keycloak (realm import), backend, frontend (nginx: SPA + proxy for `/api/` and `/auth/`, the single entry point on `http://localhost`). See `.claude/rules/infrastructure.md` |
| Database layer | `backend/src/main/resources/db/changelog` | Liquibase (formatted SQL), Spring Data JPA |
| Authentication | `keycloak/import/credits-realm.json`, `backend/**/security`, `frontend/src/app/core/auth` | Keycloak OIDC, Spring Security resource server, keycloak-angular |
| Backend API | `backend/` | Java 21, Spring Boot 4.1, Maven, MapStruct (see `backend/CLAUDE.md`) |
| Frontend | `frontend/` | Angular, Angular Material, template-driven forms (see `frontend/CLAUDE.md`) |
| Testing | `backend/src/test` | JUnit 5, Testcontainers |

## Commands
- The demo passwords are in `.env`, which is committed on purpose so a fresh clone runs with no setup (decided 2026-10-30).
- Everything runs in Docker, in development too (no dev/prod modes): `/run-stack` (or `docker compose up -d --build --wait`) starts postgres, keycloak, backend and the frontend nginx. Run the same command again to rebuild after a code change. Stop with `docker compose down`.
- Test users: `user1`/`user1` (USER), `admin1`/`admin1` (ADMIN)
- Backend tests: `backend/mvnw -f backend/pom.xml test`
- Frontend tests: `npm --prefix frontend test -- --watch=false`
- URL: everything on http://localhost (the only published port), with nginx routing `/`, `/api/` and `/auth/` (Keycloak)

## Working rules
- Never run `git add` / `git commit` / `git push`. The user commits.
- Put secrets only in `.env` (committed, demo values only). Never hardcode them anywhere else and never read `.env`.
- Every change comes with tests. Run them before saying a task is done.
- Ask the user about design decisions instead of assuming them.
- Subagents: `backend-dev` and `frontend-dev` implement, `test-writer` writes backend tests, and `security-reviewer` audits auth-related changes.
- After finishing a module, suggest `/exam-log` so the course report stays up to date.

## Domain
- **Client** 1 — * **Credit**: a client can have many credits, and every credit belongs to exactly one client.
- Credit fields, payment plan (annuity, 30/360) and statuses are recorded in `docs/TODO.md` (decided 2026-10-28). Ask before adding fields to other entities.
- Every logged-in user can read and list all clients and credits.
- **Nothing is physically deleted.** "Delete" means archive: the record gets an `archived` flag.
  - The one exception is **client attachments**: removing one deletes its row and its file (decided 2026-10-28).
- **Client attachments** are stored on the file system (`attachments-data` volume, `/data/attachments/{clientId}/{fileName}`), not as BLOBs. Allowed: PDF, DOCX, XLSX, ODT, TXT, JPEG, PNG, GIF, WEBP, HEIC, TIFF, checked by file content. Limits are in `app.attachments` in `application.yml`: 5 MB per file, 20 MB and 30 files per client. File names are stored in lower case (DB and disk: `Report.PDF` becomes `report.pdf`), and a duplicate name in the same client is rejected (decided 2026-10-28). A USER attaches files only when creating a client (one multipart request). After that, only an ADMIN adds or removes them. Everyone logged in can download them, but only an ADMIN can do anything with an archived client's attachments.
- A credit has an explicit status `ACTIVE`/`INACTIVE` (only ADMIN changes it). An ACTIVE credit can't be archived; ADMIN must deactivate it first.
- A client that has an **active credit** can't be archived. The request fails with a message the user can read.
- Business logic (calculations etc.) is added later, one feature at a time. Ask before adding any.
- Keycloak realm `credits`, realm roles `USER` and `ADMIN`. The backend only validates tokens and has no local users table.

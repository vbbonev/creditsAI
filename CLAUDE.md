# creditsAI

A small system for registering clients and their credits, built for the *AI-Assisted Development* course.
Any logged-in **USER** can register clients and credits. Only an **ADMIN** can edit or delete them.

## Modules
| Module | Location | Tech |
|---|---|---|
| Infrastructure | `docker-compose.yml`, `docker/`, `keycloak/import/` | Docker Compose: postgres (app + keycloak DBs), keycloak (realm import), backend, frontend (nginx). See `.claude/rules/infrastructure.md` |
| Database layer | `backend/src/main/resources/db/changelog` | Liquibase (formatted SQL), Spring Data JPA |
| Authentication | `keycloak/import/credits-realm.json`, `backend/**/security`, `frontend/src/app/core/auth` | Keycloak OIDC, Spring Security resource server, keycloak-angular |
| Backend API | `backend/` | Java 21, Spring Boot 3.5, Maven, MapStruct (see `backend/CLAUDE.md`) |
| Frontend | `frontend/` | Angular, Angular Material, template-driven forms (see `frontend/CLAUDE.md`) |
| Testing | `backend/src/test` | JUnit 5, Testcontainers |

## Commands
- Start everything: `/run-stack` (or `docker compose up -d --build --wait`). First copy `.env.example` to `.env`.
- Test users: `user1`/`user1` (USER), `admin1`/`admin1` (ADMIN)
- Backend tests: `backend/mvnw -f backend/pom.xml test`
- Frontend: `npm --prefix frontend start` · tests: `npm --prefix frontend test -- --watch=false`
- URLs: frontend http://localhost:4200, API http://localhost:8080/api, Keycloak http://localhost:8180

## Working rules
- Never run `git add` / `git commit` / `git push`. The user commits.
- Put secrets only in `.env` (gitignored). Never hardcode them and never read `.env`.
- Every change comes with tests. Run them before saying a task is done.
- Ask the user about design decisions instead of assuming them.
- Subagents: `backend-dev` and `frontend-dev` implement, `test-writer` writes backend tests, and `security-reviewer` audits auth-related changes.
- After finishing a module, suggest `/exam-log` so the course report stays up to date.

## Domain
- **Client** 1 — * **Credit**: a client can have many credits, and every credit belongs to exactly one client.
- Fields are not decided yet. Ask the user and don't invent them.
- Every logged-in user can read and list all clients and credits.
- **Nothing is physically deleted.** "Delete" means archive: the record gets an `archived` flag.
- A client that has an **active credit** can't be archived. The request fails with a message the user can read.
- Business logic (calculations etc.) is added later, one feature at a time. Ask before adding any.
- Keycloak realm `credits`, realm roles `USER` and `ADMIN`. The backend only validates tokens and has no local users table.

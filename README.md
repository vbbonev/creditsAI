# creditsAI

A small web application for registering **clients** and their **credits** (loans), built for the
*AI-Assisted Development* course together with Claude Code.

- Any logged-in **USER** can register clients and credits, browse them and download their files.
- Only an **ADMIN** can edit, archive, restore, change a credit's status and manage attachments afterwards.
- Nothing is physically deleted: "delete" means **archive** (the only exception is removing an attachment).

The whole stack runs in Docker, so you need nothing else installed to start and try it.

> **For the lecturers:** the record of how the project was built with AI (approach, prompts, tests,
> issues per module) is in [`docs/ai-log.md`](docs/ai-log.md) and [`docs/prompts.log`](docs/prompts.log).
> See [section 8, Exam log](#8-exam-log-for-the-lecturers).

---

## 1. Prerequisites

| What | Why |
|---|---|
| [Docker Desktop](https://www.docker.com/products/docker-desktop/) (or Docker Engine + Compose v2) | runs the whole stack |
| Port **80** free on your machine | the app is served on `http://localhost` |
| *(optional)* JDK 21 | only to run the backend tests outside Docker |
| *(optional)* Node.js 22.22.3+ | only to run the frontend tests outside Docker |

The first build downloads the images and the Maven/npm dependencies, so it needs internet access and
takes a few minutes. Later starts are much faster.

## 2. Start the application

```bash
git clone <repository-url> creditsAI
cd creditsAI

# Build and start everything, and wait until all services are healthy
docker compose up -d --build --wait
```

The demo passwords are already in the committed `.env` file, so there is nothing to configure.
(This is a course demo: committing `.env` is deliberate and not meant for a real deployment.)

When the command returns, all four containers are healthy. Open **http://localhost** in a browser.
You will be redirected to the Keycloak login page.

> Keycloak needs about 30–60 seconds to start on the first run. If `/auth` shows a
> "Login service unavailable" page, wait a few seconds and reload.

### Test users

| Username | Password | Roles | What they can do |
|---|---|---|---|
| `user1` | `user1` | USER | create and view clients and credits, download files, print payment plans |
| `admin1` | `admin1` | USER, ADMIN | everything above, plus edit, archive/restore, activate/deactivate, manage attachments, see archived records |

To switch users, use the logout button in the top toolbar.

The Keycloak admin console is at http://localhost/auth/admin (user `admin`, password = `KEYCLOAK_ADMIN_PASSWORD` from `.env`).

### Stop / restart

```bash
docker compose down                       # stop; data is kept in Docker volumes
docker compose up -d --build --wait       # start again (also rebuilds after a code change)
docker compose down -v                    # stop AND delete all data (database + uploaded files)
```

## 3. What to try (suggested demo)

1. **Log in as `user1`.**
2. **Clients → New client.** Fill in name, identifier, address (description is optional) and attach
   a few files (PDF, DOCX, XLSX, ODT, TXT, JPEG, PNG, GIF, WEBP, HEIC, TIFF; max 5 MB each, 20 MB / 30 files per client).
   The file type is checked by content, so a renamed `.exe` is rejected.
3. Open the client: see its details and files, preview images, download a file.
   Note that `user1` sees no *Edit* or *Archive* buttons.
4. **Credits → New credit.** Pick the client (autocomplete), type, amount, currency, annual interest rate,
   start and end date, and optionally collateral. Click **Calculate** to see the annuity payment plan
   (30/360), then **Save**. *Save* is only enabled after a successful calculation, and any change clears the plan.
5. Open the credit and click **Payment plan PDF** to download the credit with its payment plan as a PDF.
6. **Log out and log in as `admin1`.**
7. Edit the client or the credit (editing a credit recalculates its plan).
8. Try to **archive the client**: it fails with a readable message because the client has an active credit.
9. **Deactivate** the credit, then **archive** it, then archive the client. Turn on **Show archived clients** / **Show archived credits**
   in the list to see them again and **restore** them.
10. Add or remove attachments on an existing client or credit (ADMIN only).

## 4. Architecture

```
Browser ──► http://localhost  (nginx, the only published port, bound to 127.0.0.1)
              ├── /        → Angular SPA (static files)
              ├── /api/    → backend   (Spring Boot, :8080 inside Docker)
              └── /auth/   → keycloak  (OIDC login, :8080 inside Docker)
                                 │
                        postgres (two databases: credits, keycloak)
```

| Module | Location | Tech |
|---|---|---|
| Infrastructure | `docker-compose.yml`, `docker/`, `keycloak/import/` | Docker Compose, nginx, PostgreSQL 16, Keycloak 26 |
| Database | `backend/src/main/resources/db/changelog` | Liquibase (SQL changesets), Spring Data JPA |
| Authentication | `keycloak/import/credits-realm.json`, `backend/**/security`, `frontend/src/app/core/auth` | Keycloak OIDC (auth code + PKCE), Spring Security resource server, keycloak-angular |
| Backend API | `backend/` | Java 21, Spring Boot 4.1, Maven, MapStruct |
| Frontend | `frontend/` | Angular, Angular Material, template-driven forms |
| Tests | `backend/src/test`, `frontend/src/**/*.spec.ts` | JUnit 5, Mockito, Testcontainers; Angular unit specs |

Some notes:
- The realm `credits` (roles `USER`/`ADMIN`, client `credits-web`, test users) is imported automatically on the first start.
- The backend has no users table; it only validates Keycloak tokens.
- Attachments are stored on the file system in the Docker volume `attachments-data`, not in the database.
- Business rules: an ACTIVE credit can't be archived, and a client with an active credit can't be archived.

## 5. Running the tests

```bash
# Backend: unit + integration tests (Testcontainers starts its own Postgres, so Docker must be running)
backend/mvnw -f backend/pom.xml test

# Frontend: unit specs
npm --prefix frontend ci            # first time only
npm --prefix frontend test -- --watch=false
```

## 6. Troubleshooting

| Problem | Fix |
|---|---|
| `set POSTGRES_PASSWORD in .env` (or another variable) | `.env` is missing or a variable is empty; restore it with `git checkout -- .env` |
| `port 80 is already allocated` / `address already in use` | stop the other web server using port 80 (e.g. a local Apache/nginx) |
| `docker compose up --wait` times out | run `docker compose ps` and `docker compose logs <service>`; usually Keycloak just needs more time on a slow machine, so run the command again |
| Login loops or "invalid redirect" | open the app exactly as `http://localhost` (not `127.0.0.1` or another host name) |
| Changed `.env` passwords after the first start | Postgres keeps the old passwords in its volume; reset with `docker compose down -v` (deletes all data) |

## 7. How it was built with AI

The project was developed with Claude Code as a pair programmer. The AI setup and the record of
the work are part of the repository:

- `CLAUDE.md`, `backend/CLAUDE.md`, `frontend/CLAUDE.md`, `.claude/rules/`: project rules and conventions for the AI
- `.claude/agents/`: subagents (`backend-dev`, `frontend-dev`, `test-writer`, `security-reviewer`)
- `.claude/skills/`: custom skills (`run-stack`, `new-endpoint`, `exam-log`)
- `.claude/hooks/`: hooks that deterministically block forbidden actions (e.g. physical deletes, editing old Liquibase changesets, touching `.env`), and one that records every prompt
- `docs/ai-log.md`, `docs/prompts.log`: the exam log, see below
- `docs/TODO.md`: design decisions (with dates) and open questions

## 8. Exam log (for the lecturers)

The development was recorded while it happened, not reconstructed afterwards. There are two files:

| File | What it contains | How it is written |
|---|---|---|
| [`docs/ai-log.md`](docs/ai-log.md) | Structured log of the work, grouped by module (Infrastructure, Database layer, Authentication, Backend API, Frontend, Testing) | By the custom `/exam-log` skill, run after each finished piece of work |
| [`docs/prompts.log`](docs/prompts.log) | Every prompt sent to Claude Code, with a timestamp, unedited | Automatically, by the `UserPromptSubmit` hook `.claude/hooks/log-prompt.sh` |

Each entry in `docs/ai-log.md` follows the structure the assignment asks for (`docs/Project-Assignment.docx`):

- **Approach:** why the problem was solved this way
- **Workflow:** the steps, and which subagent, skill, rule or hook took part
- **Testing:** the tests written and run, and their results
- **AI tooling used:** which Claude Code features were used
- **Key prompt(s):** the exact prompts that drove the work (these can be found in `docs/prompts.log` by date)
- **Issues / fixes:** where the AI got it wrong and how it was corrected
- **Evidence:** commands to reproduce the result

**How `/exam-log` works:** the skill is defined in `.claude/skills/exam-log/SKILL.md`. Running
`/exam-log <module>` in Claude Code makes Claude read the current conversation and the latest lines of
`docs/prompts.log`, and append a factual entry under that module in `docs/ai-log.md`. It is only started by
the user (`disable-model-invocation: true`), so the log contains only what the developer chose to record.

**Suggested reading order:** `docs/ai-log.md` from top to bottom for the story per module; `docs/TODO.md`
for the design decisions and their dates; `docs/prompts.log` for the raw, unfiltered conversation.

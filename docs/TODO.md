# TODO — next session

_Last updated: 2026-10-25. The AI customization (rules, agents, skills, hooks, permissions) is finished. No application code exists yet._

## Before starting (user)
- [ ] `git add -A` and commit the AI setup as its own commit (the index still holds older staged versions)
- [ ] Restart Claude Code in the project and check that `/agents`, `/memory` and `/hooks` list everything
- [ ] Install JDK 21 (only 17 is installed)
- [ ] Switch to Node 20+: `nvm install 20 && nvm use 20` (currently 16)
- [ ] Decide whether to use GitHub Copilot for the tool comparison. If not, delete `.github/copilot-instructions.md`

## Choices Claude made that still need confirmation
- [ ] Backend layer packages are top-level (`com.creditsai.controller`, ...), not per feature
- [ ] Extra packages: `mapper`, `exception`, `security`
- [ ] IDs are `BIGINT GENERATED ALWAYS AS IDENTITY` (`Long`), not UUID
- [ ] Liquibase: `db.changelog-master.yaml` with `includeAll` over `changes/NNN-description.sql`
- [ ] Keycloak `onLoad: 'login-required'` (the whole app requires login)
- [ ] Keycloak 26.x. Frontend served by nginx, calling the API directly with CORS (alternative: nginx proxy for `/api`)
- [ ] No Keycloak client for the backend (it only validates tokens)

## Open design questions (asked, not answered yet)
- [ ] **Client fields:** which ones, which are required, which are unique (e.g. EGN)?
- [ ] **Credit fields:** amount, currency, interest rate, term, start date, type?
- [ ] **Credit statuses:** which ones define an "active credit" (e.g. `ACTIVE`, `PAID_OFF`, `CLOSED`)? Can an active credit itself be archived?
- [ ] **Archived records:** can an ADMIN see them (`?includeArchived=true`) or restore them?
- [ ] **Business logic:** comes later, one feature at a time (good candidates for Strategy/Factory)

## Next build steps
1. [ ] **Module 1, Infrastructure:** `docker-compose.yml`, `docker/postgres/init/01-create-databases.sh`, `keycloak/import/credits-realm.json`, `.env.example` (follow `.claude/rules/infrastructure.md`)
2. [ ] **Module 2–4, Backend:** Spring Boot skeleton (start.spring.io, Java 21, Boot 3.5, Maven wrapper), security config, then `/new-endpoint Client` and `/new-endpoint Credit` once the fields are decided
3. [ ] **Module 5, Frontend:** Angular + Material skeleton, keycloak-angular, then the clients and credits features
4. [ ] **Module 6, Testing:** Testcontainers integration tests, frontend specs
5. [ ] Re-add the Playwright MCP (`.mcp.json`) once the UI exists, for screenshots
6. [ ] Run `/exam-log` after each module. Write the Google Doc from `docs/ai-log.md` and `docs/prompts.log`

## Report material collected so far
- Challenge: the first AI setup was based on assumed architecture and was over-engineered (about 20 files, with rules repeated in many places). It was trimmed to one place per rule, and design decisions are now asked of the user first.
- Deterministic enforcement through a PreToolUse hook rather than prompts: native SQL, physical deletes, reactive forms, edits to existing Liquibase changesets, and `.env` are all blocked.
- Expected challenge: the Keycloak issuer mismatch in Docker (`localhost:8180` vs `keycloak:8080`).

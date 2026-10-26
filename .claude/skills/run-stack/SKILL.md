---
name: run-stack
description: Start the full creditsAI stack (Postgres, Keycloak, backend, frontend) with Docker Compose, wait until every service is healthy, and print the URLs and test users. Use when the user wants to run, start, or demo the app, or before taking screenshots.
allowed-tools: Bash(bash .claude/skills/run-stack/scripts/*), Bash(docker compose *)
---

# Run the stack

1. Run `bash .claude/skills/run-stack/scripts/up.sh`. It checks that `.env` exists, builds and starts all four services, waits for their healthchecks, and then calls each public URL.
2. If something fails, show the last 50 lines of `docker compose logs <service>` and diagnose the cause. Common causes:
   - **postgres:** the init script didn't run because the volume already existed. Changing it requires `docker compose down -v`, so ask first.
   - **keycloak:** the realm import failed because of invalid JSON in `keycloak/import/credits-realm.json`, or it can't reach the `keycloak` database.
   - **backend:**
     - Liquibase failed (checksum mismatch or bad changeset).
     - `ddl-auto=validate` found an entity/schema mismatch.
     - Every request gets 401 because of an issuer mismatch. Check `issuer-uri` vs `jwk-set-uri` (see `.claude/rules/infrastructure.md`).
   - **frontend:** the build failed in the Node stage, or nginx is missing the SPA fallback.
   - A port is already in use (5432, 8080, 8180, 4200).
3. When everything is green, print the URLs and test users listed under "Commands" in the root `CLAUDE.md`.
4. Tell the user the stack is still running. Don't stop it unless they ask (`docker compose down`).

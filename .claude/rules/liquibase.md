---
paths:
  - "backend/src/main/resources/db/changelog/**"
  - "backend/src/main/java/com/creditsai/model/**"
---

# Liquibase migration rules

- Layout:
  - `db/changelog/db.changelog-master.yaml` uses `includeAll: { path: db/changelog/changes/, relativeToChangelogFile: false }`
  - `db/changelog/changes/NNN-short-description.sql`, numbered with three digits: `001-create-clients.sql`
- Every file is **formatted SQL**:
  ```sql
  --liquibase formatted sql

  --changeset creditsai:001-create-clients
  CREATE TABLE ...;
  --rollback DROP TABLE ...;
  ```
  Use one logical change per changeset, and always include a `--rollback`.
- **Never modify an existing changeset.** Liquibase checksums would fail. Always add a new file with the next number (list the folder to find it, don't guess). A PreToolUse hook enforces this.
- Use `snake_case`, plural table names (`clients`, `credits`).
- Primary keys: `id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY`, unless told otherwise. Timestamps: `TIMESTAMPTZ NOT NULL DEFAULT now()`.
- Money: `NUMERIC(12,2)`. Never use floats.
- Enums are stored as `VARCHAR` with a `CHECK` constraint, mapped with `@Enumerated(EnumType.STRING)`.
- Index foreign keys and columns used in derived-query filters (e.g. `client_id` on `credits`).
- When you add a changeset, update the matching entity in `model/` so `ddl-auto=validate` passes.

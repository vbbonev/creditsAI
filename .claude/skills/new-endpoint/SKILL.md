---
name: new-endpoint
description: Scaffold a complete secured REST resource in the Spring Boot backend across all layer packages — Liquibase changeset, entity, repository, DTO records, MapStruct mapper, service interface + impl, REST controller and tests. Use when the user asks for a new API resource/endpoint/CRUD.
argument-hint: <ResourceName> [fields...]
---

# New REST endpoint

Arguments: `$ARGUMENTS` (the resource name in PascalCase, plus optional field hints such as `amount:BigDecimal`).

1. **Plan**: state the fields, the endpoints (method, path, role, paginated?) and the repository queries (derived or JPQL). If something is unclear, ask the user before writing code.
2. **Implement**: delegate to the `backend-dev` subagent. Create one file per package (`model`, `repository`, `dto`, `mapper`, `service`, `service/impl`, `controller`) plus a new Liquibase changeset. If a resource already exists, copy the structure and style of its classes in each package.
3. **Test**: delegate to the `test-writer` subagent.
4. **Report**: give a table of endpoints (method, path, role, request, response) and suggest `/exam-log`.

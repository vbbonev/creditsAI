# Copilot instructions — creditsAI

This mirrors `CLAUDE.md` so GitHub Copilot follows the same conventions (used for the tool comparison in the course report).

- Stack: Spring Boot 3.5 (Java 21, Maven wrapper), PostgreSQL + Liquibase (formatted SQL), Keycloak (realm `credits`, roles `USER`/`ADMIN`), Angular (standalone components).
- Domain: Client 1 — * Credit. Nothing is physically deleted: "delete" archives the record (`archived` flag), and archived records are excluded from reads (404 by id). A client with an active credit can't be archived (409 with a readable message).
- Backend packages by layer: `controller`, `service` (interfaces), `service.impl` (XxxServiceImpl), `repository` (extends JpaRepository), `model` (entities), `dto` (records), `mapper` (MapStruct).
- Queries: derived query methods first, then JPQL `@Query`. Never native SQL. List endpoints use `Pageable` and return `Page<Dto>`.
- Use record DTOs, Jakarta Validation, `@PreAuthorize` (create/read: USER or ADMIN; update/delete: ADMIN), and `ProblemDetail` errors. Never edit existing Liquibase changesets.
- Lombok on entities (`@Getter`/`@Setter`, never `@Data`) and `@RequiredArgsConstructor` for injection. Every method gets Javadoc. Use SOLID and name design patterns in the Javadoc.
- Frontend: Angular Material components only. Template-driven forms only (`ngModel`, validators in the template, custom validator directives); never ReactiveFormsModule/FormBuilder/FormGroup. Template validators mirror the backend DTO constraints. Edit/archive actions are shown to ADMIN only. Folders by feature (`core/`, `shared/`, `features/clients`, `features/credits`). Auth via keycloak-angular (`provideKeycloak`, bearer interceptor only for the API URL). JSDoc on every method. UI texts in English.
- Tests: JUnit 5 + AssertJ + Testcontainers (no H2). Every secured endpoint is tested for 401, USER vs ADMIN (403/2xx), and 400 validation errors. Angular: a `*.spec.ts` per component and service.
- Never hardcode secrets or URLs.

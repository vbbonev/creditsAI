# Backend conventions

Java 21, Spring Boot 4.1 (Spring Framework 7, Spring Security 7, Jackson 3), Maven wrapper, Lombok, MapStruct. Base package `com.creditsai`.

## Running
- The backend only runs in Docker (`docker compose up -d --build --wait`, rebuild after every change). There are no Spring profiles: one `application.yml`, with DB `postgres:5432`, issuer `http://localhost/auth/realms/credits`, JWKS from `keycloak:8080/auth/...`, and no CORS origins (same origin behind nginx; cross-origin preflights get 403).
- Only secrets come from env vars (`CREDITS_DB_PASSWORD`, set by compose). Tests get the database from Testcontainers (`@ServiceConnection`).
- Boot 4 moved test annotations into modules: `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` / `AutoConfigureMockMvc`, `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`. Mock beans with `@MockitoBean`.

## Packages (by layer)
```
com.creditsai
├── controller/     @RestController: XxxController
├── service/        interfaces: XxxService
│   └── impl/       XxxServiceImpl (@Service, @Transactional)
├── repository/     XxxRepository extends JpaRepository
├── model/          JPA entities + enums
├── dto/            request/response records
├── mapper/         MapStruct: XxxMapper
├── exception/      custom exceptions + GlobalExceptionHandler
└── security/       Spring Security config (JWT validation only)
```

## Layers
- Controller → service **interface** → impl → repository. Controllers see only service interfaces and DTOs.
- Transactions go on the impl: `@Transactional(readOnly = true)` on the class, `@Transactional` on write methods.
- Entities never leave the service layer.
- REST: plural kebab-case paths under `/api`, `@PreAuthorize` on every endpoint, 201 on create, 204 on delete.
- List endpoints take a `Pageable` (`@PageableDefault`) and return `Page<XxxResponse>`.

## Authorization
| Operation | Role |
|---|---|
| Create client / create credit | `USER` or `ADMIN` |
| Read / list | `USER` or `ADMIN` |
| Update / archive ("delete") client or credit | `ADMIN` only |

## Archiving (soft delete)
- Entities have `archived BOOLEAN NOT NULL DEFAULT false` and `archived_at TIMESTAMPTZ`. **Never physically delete.** In `src/main`, a hook blocks `repository.delete*(...)` and JPQL `DELETE` statements.
- `DELETE /api/clients/{id}` (ADMIN) archives the record and returns 204.
- Archived records are excluded from normal reads. Use derived queries such as `findByArchivedFalse(Pageable)` and `findByIdAndArchivedFalse(id)`. An archived record behaves like 404.
- The rule lives in the entity (encapsulation): `client.archive()` throws `BusinessRuleException` (409) if the client has an active credit, otherwise it sets `archived`/`archivedAt`. The service only loads the entity, calls `archive()`, and saves.

## Validation and errors
- Inputs are validated twice: in the frontend and in the backend. Backend validation is the authority. Never rely on the frontend.
- Request DTOs carry Jakarta Validation annotations (`@NotBlank`, `@Size`, `@Positive`, `@Email`, ...), and controllers use `@Valid`.
- Business rule violations throw custom exceptions from `exception/` (e.g. `ResourceNotFoundException`, `BusinessRuleException`). Don't return null or error codes.
- `GlobalExceptionHandler` (`@RestControllerAdvice`) returns every exception to the user as a `ProblemDetail`:
  - The exception message becomes `detail`, so it must be written for the user to read, in English.
  - Validation errors give 400 with a per-field `errors` map.
  - Not found gives 404, and business rule violations give 409 or 422.
  - Unexpected exceptions give 500 with a generic message. Log the stack trace, but never send it to the user.

## Lombok
- Entities: `@Getter`, `@Setter`, `@NoArgsConstructor`, plus `@Builder` with `@AllArgsConstructor` when a builder helps. **Never `@Data` or `@EqualsAndHashCode` on entities**, because JPA proxies and lazy collections break them. Exclude associations from `@ToString`.
- Services, controllers and mappers' dependencies: use `@RequiredArgsConstructor` with `private final` fields (constructor injection).
- DTOs stay Java `record`s, without Lombok.
- `@Slf4j` for logging.

## MapStruct
- `@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)`, called only from service impls.
- In `toEntity`/`updateEntity`, mark server-set fields (`id`, `createdAt`, ...) with `ignore = true`. Map pages with `page.map(mapper::toResponse)`.
- The `maven-compiler-plugin` annotation processor order is `lombok`, then `lombok-mapstruct-binding`, then `mapstruct-processor`.

## Javadoc
- **Every method** in `src/main/java` gets a Javadoc comment, including private ones and every method declared in an interface:
  - one sentence on **what it does** (and why, when it isn't obvious)
  - `@param` for each parameter
  - `@return` for non-void methods
  - `@throws` for each exception the caller should handle
- Every class and interface gets a class-level Javadoc. If it implements a design pattern, name the pattern (see below).
- Implementations may use `{@inheritDoc}` plus implementation-specific notes instead of copying the interface text.
- Lombok-generated methods need no Javadoc. Document the field instead when its meaning isn't obvious.
- Test methods are documented by their descriptive names (`methodName_condition_expectedResult`).

## OOP and design patterns
- **SOLID**:
  - Single responsibility per class.
  - Depend on interfaces (service interfaces, repositories).
  - Open for extension through polymorphism, not `if/else` on types.
- **Encapsulation**: business rules about an entity's own state live in the entity, as methods (e.g. `credit.close()` checks its own state). Services orchestrate. They don't manipulate fields one by one.
- Patterns already part of the architecture, named in the Javadoc where used:
  - **Layered architecture**
  - **Repository** (Spring Data)
  - **Service layer / Facade**
  - **DTO**
  - **Mapper** (MapStruct)
  - **Dependency Injection**
  - **Builder** (Lombok `@Builder`)
- Add a pattern **only when it solves a real problem**, and say in the Javadoc why:
  - **Strategy**, for interchangeable algorithms such as interest or installment calculation per credit type
  - **Factory**, for creating the right strategy or object variant
  - **Template Method**, for shared validation flows
- No speculative abstractions.

## Detailed rules (loaded automatically when the relevant files are touched)
- Queries and repositories: `.claude/rules/jpa-repositories.md`. Never native SQL; a hook blocks it.
- Liquibase changesets: `.claude/rules/liquibase.md`. Existing changesets are immutable; a hook enforces this.
- Tests: `.claude/rules/java-tests.md`
- Security: `.claude/rules/security.md`

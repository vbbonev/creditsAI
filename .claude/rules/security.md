---
paths:
  - "backend/src/main/java/com/creditsai/security/**"
  - "backend/src/main/java/com/creditsai/controller/**"
  - "backend/src/main/resources/application*.yml"
  - "keycloak/**"
---

# Security rules (backend)

- The backend is an OAuth2 **resource server** that only validates Keycloak JWTs. Configure both `issuer-uri` (the public `http://localhost/auth` URL) and `jwk-set-uri` (the internal `keycloak:8080` URL); see "Issuer URL" in `.claude/rules/infrastructure.md`. There's no login, no password handling, and no local users table.
- Map Keycloak `realm_access.roles` to `ROLE_USER` / `ROLE_ADMIN` in a single `JwtAuthenticationConverter` bean in `security/`.
- The filter chain is stateless (`SessionCreationPolicy.STATELESS`), CSRF is disabled (no cookies), and it is default-deny: `anyRequest().authenticated()`. Explicitly permit only `/actuator/health` and API docs.
- Every controller method has `@PreAuthorize` matching the authorization table in `backend/CLAUDE.md`: `hasAnyRole('USER','ADMIN')` or `hasRole('ADMIN')`. Enable it with `@EnableMethodSecurity`.
- CORS: no cross-origin requests are allowed. The SPA and the API share the origin `http://localhost` behind nginx, and `/api/**` is registered with no allowed origins, so cross-origin preflights get 403. Never add `*`.
- Never put client secrets, passwords or tokens in source, `application*.yml`, tests or logs. Use `${ENV_VAR}` placeholders.
- Error responses must not leak stack traces, SQL or internal class names (see "Validation and errors" in `backend/CLAUDE.md`).
- Run the `security-reviewer` subagent after changing anything covered by this rule.

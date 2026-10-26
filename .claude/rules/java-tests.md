---
paths:
  - "backend/src/test/**/*.java"
---

# Java test rules

- JUnit 5 + AssertJ (`assertThat`). Don't use JUnit `assertEquals`.
- Unit tests: plain Mockito (`@ExtendWith(MockitoExtension.class)`), with no Spring context. Test `XxxServiceImpl` with a mocked repository and the real MapStruct mapper (`Mappers.getMapper(XxxMapper.class)`).
- Repository and integration tests: `@DataJpaTest` (or `@SpringBootTest`) with **Testcontainers** Postgres (`@ServiceConnection`). Cover every derived and JPQL query, including pagination and sorting. Never use H2, because the schema must match production.
- Web layer: `@WebMvcTest` + `MockMvc`. Fake auth with `SecurityMockMvcRequestPostProcessors.jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))`.
- Name tests `methodName_condition_expectedResult`. Use one behavior per test with given/when/then comments.
- Every secured endpoint needs auth tests: without a token → 401. Create and read as USER → 2xx. Update and delete as USER → 403, and as ADMIN → 2xx.
- Every validation constraint on a request DTO needs a 400 test that checks the field error is returned to the user.
- Archiving needs these tests:
  - Archived records disappear from lists and return 404 by id.
  - Archiving a client with an active credit returns 409 with a readable message, and the client stays unarchived.
  - The row still exists in the database after archiving.

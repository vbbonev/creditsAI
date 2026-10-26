---
paths:
  - "backend/src/main/java/com/creditsai/repository/**"
  - "backend/src/main/java/com/creditsai/service/**"
---

# Repository & query rules

Choose queries in this order:
1. **Inherited `JpaRepository` methods** (`findById`, `save`, `findAll(Pageable)`, ...). Don't redeclare them.
2. **Derived query methods**, e.g. `Page<Credit> findByClientIdAndStatus(Long clientId, CreditStatus status, Pageable pageable)`. Use these whenever the name stays readable (roughly three conditions or fewer).
3. **`@Query` with JPQL**, for joins/fetch joins, aggregates, or conditions that would make a derived name unreadable. Use named parameters (`:status`) and entity/field names, not table/column names. Add a `countQuery` when it's paginated and the query has a fetch join.
4. **Never native SQL.** No `nativeQuery = true`, `createNativeQuery`, or `JdbcTemplate` for domain data. If JPQL can't express something, stop and ask the user.

Other rules:
- Pagination (see `backend/CLAUDE.md` → Layers), repository side: a method that can return many rows takes a `Pageable`. Return `Slice<T>` instead of `Page<T>` when the total count isn't needed.
- Use `Optional<T>` for single results. The service impl turns an empty result into a `ResourceNotFoundException`.
- Avoid N+1 queries: use `@EntityGraph` or a JPQL `join fetch` when the response needs associations.
- Repositories are injected only into `service/impl` classes.
- Soft delete: follow "Archiving (soft delete)" in `backend/CLAUDE.md`.

# PR: Low-risk production hardening (caching, pool tuning, asset cache, image pinning)

- **Branch:** `feature/studio-quick-wins-2026-08-28`
- **Base:** `apache/rocketmq-studio` (`upstream/rocketmq-studio`)
- **Scope:** Backend + frontend + deploy configuration only. No feature behavior change.

## Summary

A batch of low-risk, high-confidence optimizations identified by the
`docs/optimization-audit-2026-08-28.md` review (Wave 1, plus the two caching
items that are safe to land now). None of these change user-facing behavior;
they harden the runtime, shrink images' build drift, and remove dead/duplicate
code.

## Motivation

The audit found several "free" wins that reduce production incident surface
without touching business logic:

- A placeholder Spring Security config class that does nothing and misleads
  readers.
- No caching on the most-hit settings endpoint, causing a redundant DB round
  trip on every metrics tab first paint.
- Default HikariCP pool (10 connections) saturating under concurrent dashboard
  polling + alerting.
- Logback configured to allow ~10 GB of logs per instance.
- nginx serving the SPA without `Cache-Control`, re-downloading the ~1.78 MB
  hashed bundle on every visit.
- Docker base images on rolling tags, making CI builds non-reproducible.
- Frontend `no-explicit-any` only a warning, letting type-escapes slip through.
- `topic.tsx` re-declaring `formatDateTime` / `formatNumber` already exported
  from `utils/format`.

## Changes

### Remove dead security placeholder
- **Deleted** `server/src/main/java/org/apache/rocketmq/studio/auth/SecurityConfig.java`
  — an empty `@Configuration` with a `TODO` and no Spring Security dependency on
  the classpath. Safe to remove; no bean or import references it.

### Enable in-process caching for settings datasources
- `server/src/main/java/org/apache/rocketmq/studio/StudioApplication.java`:
  added `@EnableCaching`.
- `server/src/main/java/org/apache/rocketmq/studio/settings/SettingsService.java`:
  - `@Cacheable("data-sources")` on `listDataSources()` and the paged overload.
  - `@CacheEvict(value = "data-sources", allEntries = true)` on
    `createDataSource` / `updateDataSource` / `deleteDataSource`.
  - Spring Boot auto-configures a `ConcurrentMapCacheManager`; writes fully
    evict the cache, so the cached list stays consistent.

### Connection pool & logging tuning
- `server/src/main/resources/application.yml`: HikariCP
  `maximum-pool-size=30`, `minimum-idle=5`, `connection-timeout=5000`,
  `idle-timeout=300000`, `max-lifetime=1200000`, `leak-detection-threshold=30000`.
- `server/src/main/resources/logback-spring.xml`: `maxFileSize` 1 GB → 100 MB,
  `maxHistory` 10 → 30, added `totalSizeCap=5GB`.

### Edge / build reproducibility
- `deploy/nginx.conf`: long-cache hashed assets (`/assets/`, 1y `immutable`)
  and `no-cache` on `index.html` so new deploys reach users immediately.
- `server/Dockerfile` & `web/Dockerfile`: pin base images
  (`dragonwell:21.0.10-anolis`, `node:20.19.0-alpine`, `nginx:1.27.2-alpine`)
  instead of rolling tags.

### Frontend hygiene
- `web/eslint.config.js`: `@typescript-eslint/no-explicit-any` promoted from
  `warn` to `error` (codebase currently has 0 explicit `any`).
- `web/src/pages/instance/topic.tsx`: removed locally-declared
  `formatDateTime` / `formatNumber`; now imported from `utils/format`.
- `web/src/pages/settings/GeneralSettingsTab.tsx`: replaced `catch (e: any)`
  with a typed error access.
- `server/src/main/java/org/apache/rocketmq/studio/instance/message/MessageService.java`:
  added explanatory comments only (no behavior change).

## Verification

- Frontend: `npx tsc -b` → **0 errors**.
- Backend: `mvn -o test-compile` (JDK 21, local repo) → **0 errors**.
- Backend targeted tests:
  `SettingsServiceTest`, `MessageServiceTest`, `SettingsControllerTest`,
  `MessageControllerTest` → **all pass**.
- `grep` for `: any` / `as any` / `<any>` across `web/src` (incl. tests) → **0
  matches**, so the promoted ESLint rule cannot break CI.
- Confirmed `SecurityConfig` has no source references and `spring-security` is
  absent from `server/pom.xml`.

## Risk / Notes

- Caching is in-process (`ConcurrentMapCacheManager`); it is appropriate for a
  single-instance dashboard. A multi-node deployment would need a shared cache
  — tracked as future work.
- Image pins assume the exact patch tags exist in their registries; they were
  chosen from currently-available releases.
- The `MessageService` paging is still in-memory within the broker-side cap
  (documented in code comments); true cursor-based paging is future work.

## Out of scope

Higher-risk items from the audit (real auth rework, React Query adoption,
giant-page decomposition, i18n split, ArchUnit rules) are intentionally left
for later waves and are **not** part of this PR.

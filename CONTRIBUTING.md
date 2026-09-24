# Contributing to RocketMQ Studio

Thanks for your interest in RocketMQ Studio. This document explains how to set up a
development environment, how changes are reviewed and merged, and what we expect from a
pull request.

RocketMQ Studio is developed in this repository (`apache/rocketmq-dashboard`) on the
**`rocketmq-studio`** branch, which is also the repository default branch. All pull requests must
target `rocketmq-studio`.

| Branch | Role |
|--------|------|
| `rocketmq-studio` | RocketMQ Studio trunk — base your branches and pull requests on it |
| `master_archive` | Archive of the legacy rocketmq-dashboard code that lived on the trunk before. Read-only history, not for development |

Recommended background reading:

- [Apache Contributors Tech Guide](http://www.apache.org/dev/contributors)
- [Get involved!](http://www.apache.org/foundation/getinvolved.html)
- [README](README.md) for the feature list and architecture overview

## Development environment

| Component | Version |
|-----------|---------|
| JDK | 21 (Dragonwell or Temurin) |
| Maven | 3.9+ |
| Node.js | >= 20.19.0 (npm, `package-lock.json` is committed) |
| Docker + Docker Compose | latest, for MySQL and the local RocketMQ topology |

### Run the full stack

```bash
# Run from the repository root; rocketmq_net is external to both compose files and must exist first
(docker network create rocketmq_net 2>/dev/null || true) && \
  docker compose -f deploy/rocketmq/docker-compose.yml up -d && \
  docker compose -f deploy/docker-compose.yml up -d --build
```

Studio is then available at <http://127.0.0.1:6789> (frontend) and <http://127.0.0.1:8888>
(backend). See [`deploy/README.md`](deploy/README.md) for the configuration options.

### Run the backend only

```bash
cd server
mvn -B -ntp spring-boot:run          # or: mvn -B -ntp package -DskipTests
```

### Run the frontend only

```bash
cd web
npm ci
npm run dev                          # Vite dev server with hot reload
```

## Tests

```bash
# Backend unit + integration tests (also runs the ArchUnit architecture checks)
cd server && mvn -B -ntp test

# Frontend tests (vitest), lint and production build
cd web && npm test && npm run lint && npm run build
```

The backend integration tests (`@SpringBootTest`) talk to a real datasource, so they need a
MySQL 8 instance reachable at `localhost:3306` with the Studio schema loaded. The easiest way
is to start the bundled one, which loads `server/src/main/resources/db/schema.sql`
automatically:

```bash
cd deploy && docker compose up -d mysql
```

Non-trivial changes need tests. Name test methods `somethingHappensTest` (suffix `Test`),
keep frontend and backend tests next to the code they cover, and add both Chinese and
English strings to `web/src/i18n/` whenever you add UI text.

## Contribution workflow

1. **Search first.** Look through the [open issues](https://github.com/apache/rocketmq-dashboard/issues)
   to see whether the problem or idea is already tracked.
2. **Open an issue before you write code**, using one of the issue templates (Bug Report,
   Feature Request, Enhancement Request, Doc). Trivial fixes such as typos do not need an
   issue.
3. **Discuss non-trivial designs in the issue and wait for maintainer feedback.** Apache
   projects decide by consensus: a new feature, a new API or a large refactor should be
   agreed on in the issue before the implementation lands. This saves you from having a
   finished pull request rejected on design grounds.
4. **Fork, branch, commit.** Create your branch from `rocketmq-studio`:

   ```bash
   git clone git@github.com:<your-username>/rocketmq-dashboard.git
   git remote add upstream https://github.com/apache/rocketmq-dashboard.git
   git fetch upstream rocketmq-studio
   git checkout -b <your-topic> upstream/rocketmq-studio
   ```

5. **Open a pull request against `rocketmq-studio`** and link the issue with `Fixes #<issue-id>` so
   it closes automatically on merge.
6. **Keep it current.** Rebase onto `upstream/rocketmq-studio` when the branch moves; force-push
   your own fork branch as needed.

Pull requests are merged with **squash merge only**, so the merged commit message becomes
`type: description (#N)`. Write commit subjects in the Conventional Commits style
(`feat:` / `fix:` / `refactor:` / `chore:` / `docs:` / `perf:`).

## What we expect from a pull request

- **One coherent change per pull request.** Do not split a single fix into a series of
  one-line pull requests, and do not bundle unrelated changes together.
- **It builds and the tests pass.** CI runs the backend build, the backend test suite, the
  frontend build, the frontend image build, and the Go CLI checks (`rmqctl`: `gofmt`,
  catalog/manifest consistency, `go vet`, `go test -race`, build). A pull request that does
  not compile will be closed.
- **It is reviewed code, not just generated code.** Using AI assistance is fine, but you are
  responsible for the result: read the diff, understand it, and verify it with tests. Bulk
  submissions of unverified changes are closed without review.
- **No unrequested rewrites.** Large refactors, dependency upgrades and formatting sweeps
  need an issue and maintainer agreement first.
- **License headers on new source files**, as required by the ASF
  ([policy](https://www.apache.org/legal/src-headers.html)).

## Code standards

The rules below are enforced by review or by the build; details and rationale live in
[README](README.md#development-guidelines) and [`docs/`](docs).

- **Package root** `org.apache.rocketmq.studio`, sources under `server/src/`. The repository
  also holds a Go module, `rmqctl/`, which is compiled into the server image and is what a
  hosted agent uses to reach the MCP tools; it follows Go conventions (`gofmt`, `go vet`,
  `go test -race` — `make -C rmqctl ci` runs exactly what CI runs) and its tool catalog must stay
  in sync with the server's `tool-catalog` manifest, which `make -C rmqctl catalog-verify` checks.
- **Hexagonal architecture** (domain / application / adapter) is asserted by ArchUnit tests
  that run as part of `mvn test` — a violation fails the build.
- **Lombok** everywhere: `@Data` / `@Builder` / `@NoArgsConstructor` / `@AllArgsConstructor`
  on POJOs, `@RequiredArgsConstructor` for constructor injection, `@Slf4j` for logging.
- **REST layer**: write operations take a DTO with Jakarta validation and return a VO; every
  response is wrapped in `Result<T>`; errors are raised as `BusinessException(400, msg)`.
- **The AI event vocabulary is a cross-language contract.** An agent transcript has to render
  identically whether it is watched live over SSE or replayed from the database, so the event
  types are pinned by one committed fixture, `server/src/test/resources/ai/ai-event-contract.json`,
  which both `AiEventContractTest` (Java) and `web/src/api/aiEvents.contract.test.ts` (TypeScript)
  read. Adding, renaming or removing a `LiveEvent` / `TimelineEvent` subtype means updating the
  fixture **and** the type set on **both** sides in the same pull request; changing only one turns
  CI red, which is the point. The Java switch over `AgentEvent` is deliberately exhaustive with no
  `default` branch, so a new subtype that has not been given a projection is a compile error rather
  than a silently dropped event.
- **RocketMQ clients are long-lived and pooled.** Use the existing factories and pools
  (`MqAdminExtFactory`, `MqClientPool`) instead of creating, starting and shutting down a
  client per request.
- **Query failures are graded.** RPC-level failures (connect / timeout / broker unreachable)
  surface as errors; an empty business result (for example "consumer group not online",
  "route not found") returns `200` with empty data instead of an error.
- **Dependencies**: `org.apache.rocketmq:*` must be Apache open-source releases only — no
  internal or vendor-specific builds, and no `com.aliyun.openservices:ons-client`. Cloud
  control-plane access goes through the official OpenAPI SDKs.
- **Frontend**: minimum font size 14px; tables must not need a horizontal scrollbar at
  normal widths (use `tableScrollX(columns)`); page-level neutral notes use the shared
  `InfoBanner` component.
- **List tables**: the Group management page (`web/src/pages/instance/consumer.tsx`) is the
  reference implementation. Exactly one column — the primary text column — uses `minWidth`
  and absorbs the surplus width of wide windows; every other column declares a fixed
  `width`. The action column is always the last column with a fixed width, and its buttons
  are right-aligned (`<Flex gap={6} justify="flex-end">`) so they sit flush with the right
  edge of the table instead of leaving trailing whitespace. `scroll.x` is always derived via
  `tableScrollX(columns)`; never hand-write a magic number. The declared widths are a budget:
  their sum (plus the selection/expand columns) must stay within the content area at the
  standard 1560px window — the reference page totals ~1290px — so no horizontal scrollbar
  appears at the default width. Never leave a column without `width`/`minWidth`: under
  `tableLayout="fixed"` unsized columns share the whole `scroll.x` evenly and push the
  action column past the viewport. Verify in a real browser after adding or resizing
  columns.
- **Configuration**: `server/src/main/resources/application.yml` keeps environment-variable
  overrides minimal. Only genuinely deployment-specific values — datasource coordinates,
  credentials and secrets, external endpoints (Prometheus, NameServer, LLM), and deployment
  policy switches (login required, CORS origins, cookie Secure) — may use the
  `${ENV_VAR:default}` form. Internal tuning knobs (retention days, cleanup intervals, batch
  sizes, timeouts, parallelism) are written as plain literal values; promote one to an
  environment variable only when a real deployment needs to override it. The file carries no
  comments — rationale belongs in the pull request or `docs/`, not in the configuration.

## Review, merge and the stale bot

Maintainers review pull requests in the order they arrive. Expect a `Request changes` review
when something needs rework — push the fixes to the same branch and the review continues.

This repository runs a **stale bot that closes issues and pull requests after 7 days without
activity** (`.github/workflows/stale.yml`). If your pull request is waiting on you, respond
within that window; if it is waiting on a maintainer, leave a comment to reset the timer or
ask for the `pinned` label. A closed pull request can be reopened as long as its branch still
exists.

## Apache contributor license

For substantial contributions the ASF asks for a signed
[Individual Contributor License Agreement (ICLA)](https://www.apache.org/licenses/contributor-agreements.html).
Corporate contributions may need a
[CCLA](https://www.apache.org/licenses/#clas). You keep the copyright to your work; the
license only grants the ASF the right to distribute it under the Apache License 2.0.

## Where to talk

- **Issues in this repository** — bugs, features and design discussion for RocketMQ Studio.
- **[dev@rocketmq.apache.org](https://rocketmq.apache.org/about/contact/)** — the RocketMQ
  project mailing list, for cross-project topics and release discussions.
- **[RocketMQ Discussions](https://github.com/apache/rocketmq/discussions)** — usage
  questions about Apache RocketMQ itself.

## Becoming a committer

We are always interested in adding new committers. What we look for is a sustained record of
useful contributions, good judgement in review and ongoing interest in the project. If you
would like to become a committer, talk to one of the existing committers and they will walk
you through the process.

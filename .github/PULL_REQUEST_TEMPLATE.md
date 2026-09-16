<!-- Make sure the base branch is `master`: that is the RocketMQ Studio trunk. -->

### Which Issue(s) This PR Fixes

<!-- Link the issue with a keyword so it closes on merge. Trivial fixes need no issue.
     https://docs.github.com/en/issues/tracking-your-work-with-issues/linking-a-pull-request-to-an-issue -->

- Fixes #<issue-id>

### Brief Description

<!-- What changes and why. Keep it short — the diff already shows how. -->

### How Did You Test This Change?

<!-- Paste the commands you ran and what they printed. Typical verification:
     backend  `cd server && mvn -B -ntp test`   (integration tests need MySQL 8, see CONTRIBUTING.md)
     frontend `cd web && npm test && npm run lint && npm run build`
     A pull request with no verification will not be merged. -->

### Checklist

- [ ] One coherent change; unrelated modifications are not bundled in
- [ ] Commit subject follows Conventional Commits (`feat:` / `fix:` / `refactor:` / `chore:` / `docs:` / `perf:`)
- [ ] Tests added or updated for non-trivial changes, test methods named `...Test`
- [ ] New UI text has both Chinese and English entries under `web/src/i18n/`
- [ ] Architecture constraints stay green (`mvn test` runs the ArchUnit checks)
- [ ] New source files carry the ASF license header
- [ ] Documentation touched where behaviour changed (README / `docs/` / in-app help)

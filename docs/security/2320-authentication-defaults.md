# #2320 — Secure management endpoint defaults

## Remediation scope

- Require authentication for Dashboard management paths by default.
- Define the minimal public bootstrap, login, and CSRF paths.
- Protect or remove the test task endpoint.

## Verification

Add integration coverage for unauthenticated denial and documented deployment configuration.

# #2324 — CSRF token log removal

## Remediation scope

- Remove browser-console logging of CSRF tokens.
- Retain the existing token retrieval and request-header behavior.

## Verification

Add a focused regression test for the API helper.

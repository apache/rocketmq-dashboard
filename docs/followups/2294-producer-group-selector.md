# #2294 — Producer-group selector discovery

## Delivery scope

- Add a selector-only producer-group endpoint, distinct from detailed connection lookup.
- Bound, deduplicate, and deterministically sort results; support topic narrowing where available.
- Update the producer selector and add focused backend/frontend tests.

## Acceptance

The selector never obtains a full connection inventory merely to render options.

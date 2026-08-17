# #2295 — Consumer Group pagination

## Delivery scope

- Return the existing 1-based `PageResult` contract from the Consumer Group inventory.
- Preserve `clusterId` and `search` filtering before paging, including cloud-provider paths.
- Test page boundaries and filtered totals.

## Constraint

Do not modify the unresolved Consumer/Topic frontend conflict files in this PR.

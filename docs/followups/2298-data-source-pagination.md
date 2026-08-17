# #2298 — Data-source pagination and stable ordering

## Delivery scope

- Add the paginated settings API/UI flow using the existing `PageResult` contract.
- Establish persisted stable ordering by update time with a deterministic key tie-breaker.
- Cover repeated-request ordering and page boundaries with focused tests.

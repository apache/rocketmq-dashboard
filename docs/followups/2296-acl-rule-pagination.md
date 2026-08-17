# #2296 — ACL Rule pagination and filters

## Delivery scope

- Add a paginated ACL Rule repository/service/controller query.
- Apply `principal`, `scope`, and `resource` filters before paging.
- Cover filtered totals and post-last-page behavior with focused tests.

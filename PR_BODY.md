Fixes #3150

## Summary
`/api/groups/page` now accepts an optional `subscriptionMode` query parameter (`Push`/`Pop`) and applies the Push/Pop subscription-mode filter on the server instead of filtering only the current page in the browser. The total count and pagination are computed on the filtered result set, so paging stays correct when the filter is active.

## Why
The consumer group list previously fetched one page and then filtered it by `subscriptionMode` in memory. When a page contained few (or no) groups of the selected mode, the table appeared empty or under-filled even though matching groups existed on other pages, and the pagination total did not reflect the filter. Moving the filter to the backend makes the page, total and CSV export agree with each other.

## Testing
- Backend:
  - `ConsumerGroupControllerTest` verifies the `subscriptionMode` parameter is propagated from the `/api/groups/page` endpoint to `MetadataService` (and `null` when omitted).
  - `RocketMQMetadataProviderTest` verifies the database query gains a `message_model` condition for `Pop` (equality) and `Push` (NULL or anything but Pop, matching the VO fallback semantics).
  - `MetadataServiceTest` verifies the mode is forwarded to both the cluster metadata provider and the instance provider, and that blank/unknown modes are ignored.
  - `InstanceProviderTest` verifies the shared in-memory cloud-provider fallback filters before pagination and recomputes the total.
  - `ApacheInstanceProviderTest` was updated for the new delegation signature.
- Frontend (`ConsumerPage.test.tsx`): changing the Push/Pop mode filter triggers a fresh server request carrying `subscriptionMode`, and the default request passes `subscriptionMode: undefined`.
- Local type check: `./node_modules/.bin/tsc -b tsconfig.app.json` exits 0.

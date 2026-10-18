# Proposal: finalizing a conversation before deletion

Status: **proposed**, pending maintainer agreement in #6953. This document does not
describe an implemented guarantee.

## Current boundary

`AiConversationService.delete` stops the active run through `AgentRunRegistry`,
then deletes events, runs, the conversation and its workspace. `AgentRunHandle.stop`
waits for the subprocess tree and calls `Future.cancel(true)` on the worker.
`AiRunExecutor.finalizeRun` subsequently flushes its sink and writes terminal state.
The service explicitly accepts a race in which a terminal event survives deletion.

Cancelling a future makes it done to its callers before the interrupted task has
necessarily left its `finally` block. Neither `Future.isDone()` nor a cancelled
`Future.get()` therefore proves that persistence has stopped.

## Proposed lifecycle

1. Check ownership and enter an authoritative deleting state for the conversation.
   Run admission must check the same state under the same coordination boundary.
2. Stop any admitted run using the existing process-tree shutdown path.
3. Wait for a separate completion signal issued after worker finalization and sink
   closure. Handle tasks cancelled before they start: they need their own terminal
   path and must not leave a waiter blocked forever.
4. Delete events, runs and the conversation atomically where supported, then remove
   the workspace. A workspace cleanup failure must remain observable and retryable.
5. A timeout or interruption before the cascade preserves the rows and workspace.
   It must not turn an incomplete drain into a successful deletion.

New admission and deletion cannot be coordinated by an active-run lookup alone:
a new run could be inserted between that lookup and the cascade. Similarly, a
completion signal tied only to a run's status is insufficient because an early
projected success can precede worker cleanup.

## Decisions requested

- **State ownership:** a persisted deletion marker with restart recovery, or an
  in-process conversation gate consistent with the documented single-instance
  deployment. Do not imply the latter provides multi-node coordination.
- **Timeout:** choose a bounded drain budget and a clear retryable response; do not
  reuse a subprocess grace period as evidence that database work has completed.
- **Retry:** define how repeated deletes observe a drain already in progress and
  how unfinished workspace removal is recovered.
- **Scope:** scheduled retention should use the agreed lifecycle only after its
  skip-versus-stop policy is settled in its existing PRs (#5810 and #4852).

## Verification required before implementation is ready

Use latches around final persistence and admission, not timing sleeps:

- Hold the worker immediately before its terminal insert, start deletion, and
  assert the cascade has not run; release it and assert no rows survive.
- Race a new admission against deletion; exactly one must win the shared gate.
- Cancel a worker before execution starts and prove the drain finishes.
- Exercise terminal exceptions, timeout and interruption; preserve data until
  finalization is proven complete and report failure to the caller.
- Retry after a partial database failure and after workspace-removal failure.
- Keep owner checks, existing Stop attribution and observer completion unchanged.

This proposal changes no runtime API, database schema, retention behavior or
deployment support. Those changes need the decisions above first.

# Proposal: hosted-agent mutation approval guarantees

Status: **proposed**, pending the product decision in #6027. None of the approval
states or guarantees below are implemented by this document.

## Existing boundary

`ToolMutationFilter` returns a preview and confirmation token for a dry run, then
verifies the token for apply. `ToolTokenService` binds the token to tool, principal,
instance, business input and expiry. MCP calls authenticate through the instance
credential; they do not authenticate the human browser session. Obtaining a preview
token therefore is not evidence of a server-recorded human approval.

Atomic single-use consumption is reviewed separately in #5452. It prevents reuse
of an execution capability, not an agent from obtaining one through preview.

## Product choice

| Model | Human-visible guarantee | Required boundary |
| --- | --- | --- |
| A: read-only hosted run | The agent can diagnose and preview; the operator applies changes in the UI | The server enforces a hosted read-only tool scope |
| B: per-operation approval | Each exact proposed mutation awaits its authenticated owner | Server-side pending operation and human approval endpoint |
| C: explicit delegation | An administrator authorizes a clearly bounded autonomous run | Recorded scope, expiry and revocation policy shown before start |

Model A has the smallest operational surface. Models B and C provide different
products, so neither should be silently selected as a security patch. Maintainers
must select the intended guarantee and how existing hosted sessions transition.

## Trusted identity comes first

Distinguish hosted-run calls using credentials or capabilities issued and scoped
by the server to an owner/conversation/run. A client-controlled header, prompt text
or CLI flag is not trusted classification: an agent with the same external
instance credential could omit it. The hosted subprocess must not retain an
unrestricted credential that bypasses its assigned scope.

External trusted rmqctl automation keeps its documented workflow unless a separate
compatibility change is agreed. Granting it the same authentication principal
while expecting different approval rules requires an explicit authorization model.

## If model B is chosen

1. Preview creates a pending operation bound to authenticated owner, conversation,
   run, exact tool, canonical instance, normalized input, reason and expiry.
2. The browser displays that immutable plan. Editing inputs creates a new plan;
   it does not reuse approval for another operation.
3. An authenticated human-only endpoint checks ownership, authorization, expiry
   and the operation state, then atomically claims it for execution.
4. Repeated/concurrent approvals cannot start two executions. A failed or timed-out
   execution reports its outcome, including an unknown outcome where necessary;
   it must not automatically retry a non-idempotent mutation.
5. Cancellation and expiry prevent approval/execution only before the execution
   claim. Revocation after a broker operation begins cannot promise rollback.
6. Persist enough state and attribution for restart recovery and audit. Do not
   record a preview as an applied mutation or attribute the human choice solely
   to an instance credential principal.

Stopping a run, deleting a conversation, changing credentials or losing an owner
must have a defined effect on pending operations. Multi-node guarantees require a
shared atomic store; a process-local map must not be described as durable approval.

## Acceptance tests for the chosen implementation

- Agent-only calls cannot self-approve or obtain a broader external credential.
- Another user, run, instance or edited input cannot reuse the approval.
- Concurrent/repeated approval yields one execution claim.
- Expiry, cancellation, stop, deletion and credential changes cover both sides of
  the execution boundary.
- Restart and partial execution failure preserve honest audit/result semantics.
- Supported external rmqctl workflows retain their agreed compatibility.

Implementation waits for the model, identity and restart-policy decisions above.

# Ownership write concurrency proposal

Status: proposal for discussion, related to issue #7194. No runtime locking changes are approved by this document.

## Observed behavior

`ResourceOwnershipGuard.withOwned` opens or joins a transaction, locks the instance identity
and registered instance row, locks the requested ownership rows in a fixed order, and invokes
the action before committing. `write` also holds instance and ownership locks during its
second transaction. Actions can include remote send, reset, replay, or broker configuration RPCs.

Two independent resources in the same instance consequently contend on the same identity
mutex. The second operation can wait even when its resource rows are disjoint. Both remote
latency and the wait consume a database connection. This document identifies the locking
scope from code; it does not claim measured throughput or a production incident.

## Required invariants

- Every admitted write uses a validated instance endpoint, vendor, credential reference,
  physical cluster, and resource owner. Validation must remain valid through remote admission.
- Endpoint and credential updates and instance deletion cannot overtake an admitted write.
- Conflicting operations on a resource remain ordered; multi-resource operations use a
  deterministic lock order.
- Pending claims remain durable after remote failure. Claim creation, remote writes, and
  resource deletion retain their current ownership and recovery guarantees.
- Coordination works across JVMs, not only between threads of one Studio process.
- Remote effects are not presented as rolled back by a database transaction.

## Measurement before implementation

Use an isolated MySQL 8 database with the Studio schema and two Studio application processes.
Seed one registered instance with two owned topics and a second instance as a control. Replace
remote actions with a controllable test action so broker latency cannot obscure lock waits.
Do not use a live broker to block requests artificially.

1. Hold an action on topic A after ownership admission. Attempt a write on topic B in the
   same instance, a conflicting write on A, and a write in the second instance.
2. Record when each transaction acquires its instance and resource locks and enters its
   remote action. Use bounded latches and futures, not timing-only assertions.
3. Repeat across both application processes. Capture MySQL lock waits and connection use;
   repeat with fixed remote delays and increasing concurrency to measure throughput and
   tail latency. Publish the commands, database settings, workload, and raw results.
4. Concurrently attempt endpoint/credential changes and deletion. They must remain blocked
   or rejected until admitted writes finish, including failed remote actions.
5. Run a separate H2 compatibility test. H2 results establish test portability, not MySQL
   shared-lock semantics or production performance.

## Alternatives to evaluate

| Alternative | Potential benefit | Required proof or limitation |
| --- | --- | --- |
| Keep current exclusive instance lock | Simplest existing cross-process safety | Accept serialization if measurements show adequate capacity |
| Shared instance admission lock, exclusive configuration/deletion lock | Independent resources could proceed together | Remove the current identity-row UPDATE from the read-admission path; prove shared/exclusive compatibility, virtual-instance registration exclusion, lock ordering, and MySQL/H2 behavior |
| Durable operation reservations with instance generations | Could shorten database transactions around remote RPCs | Define reservation recovery, generation changes, crash behavior, and how configuration changes wait for active reservations |

An in-memory read/write lock alone is insufficient across application processes. Releasing the
instance lock after checking a version is also insufficient: a configuration change can occur
between that check and the remote effect. Expiring a reservation does not prove that the old
worker has stopped; a fencing design must explain which remote operations can enforce it.

The shared-lock option is a candidate to prototype, not a selected design. Its ordering must
include both the identity row and registered instance row. Virtual instances and concurrent
registration need the same exclusion guarantee; creating a missing mutex row cannot silently
upgrade every admitted reader to an exclusive writer.

## Review and rollout gates

Agree on the measured problem, selected coordination mechanism, and supported database
semantics in the issue before changing runtime behavior. A follow-up implementation must
include deterministic tests for independent and conflicting resources, multi-resource
ordering, registration/deletion/configuration races, two application processes, remote
failure, cancellation, and process death where relevant to the chosen design.

Compare the prototype with the current implementation using the same workload. Report
throughput, action admission latency, lock waits, and connection occupancy. Keep the existing
exclusive path until the safety tests and measured benefit justify replacing it. Schema or
API changes require a separate migration and compatibility review.

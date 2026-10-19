# Proposal: a payload-bound MCP authentication revision

Status: **proposed**, pending agreement in #6956. The current wire contract is the
subject of #6028 / #5530; this document neither replaces it nor changes runtime code.

## Current guarantee

The Go client's `canonicalRequest` and Java's `McpAuthenticator.canonicalRequest`
sign six lines: algorithm, access key, instance, timestamp, method and request target
(path plus query). The timestamp permits five minutes of clock skew in either
direction. The body is explicitly outside that signature. The signature verifies
credential possession and the named target; trusted transport supplies body integrity.

HTTPS protects the message in transit. The client's private-address HTTP exceptions
assume a trusted network. Possession of a valid authentication header is not proof
of one specific JSON body; adding that guarantee requires a coordinated revision.

## Proposed stronger guarantee

A new revision would authenticate a digest of the **exact transmitted bytes**, with
an unambiguous canonical order for algorithm, access key, instance, timestamp,
method, escaped path/query, body digest and freshness identifier. Select the new
algorithm identifier and field encoding in maintainer review, rather than silently
changing what the existing `rmq-hmac-sha256` token means.

Use SHA-256 over the raw body, including whitespace and encoding. Empty bodies use
the SHA-256 digest of zero bytes. Do not parse and reserialize JSON for signing:
numeric precision, key order and escaping must not change the signed message.
The client and server need one explicit body-byte limit and consistent rejection.

The server must hash a bounded, replayable request body before the controller reads
it. Reading it in the authentication filter must not consume it for downstream code.
The client must preserve request cancellation and body-closing semantics while
hashing; chunked requests and missing `Content-Length` cannot bypass the limit.

## Replay and retry decisions

Body binding alone does not reject an identical replay. If replay rejection is
required, a signed nonce needs atomic consumption, bounded storage and expiry that
covers the entire timestamp acceptance window. A process-local cache does not
provide cross-process or restart persistence. The deployment contract must say so.

Transport retries and MCP reconnects should generate fresh authentication after
the business retry policy permits another send. A fresh nonce still allows the
same business operation to execute twice. At-most-once request acceptance is not
exactly-once mutation execution after an ambiguous network failure.

Keep confirmation-token single-use in #5452. Keep server-recorded human approval
or explicit hosted automation delegation in #6027. Neither can be inferred merely
from a stronger transport signature.

## Compatibility decisions

Choose the supported client/server matrix, deployment order, old-client policy and
the end of any compatibility window before implementation. A server that accepts
the old revision still offers its old guarantee. A client must not silently fall
back to an older revision after a verification error. Failure responses should
stay opaque and must not echo signing secrets or the canonical payload.

## Required verification

- Shared Go/Java golden vectors for empty and nonempty bodies, queries, non-ASCII
  text, whitespace differences and large integers without JSON normalization.
- Rejection when any signed field or body byte changes.
- Exact-limit, over-limit, chunked-body and missing-length cases.
- Concurrent duplicate nonces, expiry edges, clock skew and bounded-store behavior.
- Explicit restart and supported-version behavior for the chosen replay policy.
- A controller can still read the verified body exactly once after authentication.
- Supported HTTP/MCP transports, notifications, reconnects and retry paths.

No new format, nonce cache or downgrade behavior is implemented by this proposal.

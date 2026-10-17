# Studio request-signing contract (`rmq-hmac-sha256`)

This document specifies the HMAC request-signing contract that the Studio
server (`McpAuthenticator` / `McpAuthenticationFilter`) and the rmqctl Go
client (`rmqctl/internal/studio/auth.go`) both implement. It exists so
operators can plan upgrades and other clients can stay compatible; the
coordinated changes in #4310 and #4606 are the motivating examples.

Revision identifier: **`rmq-hmac-sha256/1`** (see "Revisions" below).

## Operator-facing guarantees

- Every request to a protected endpoint must carry a valid per-instance
  credential signature; requests without credentials are rejected.
- Verification failures return an opaque `401` with the single message
  `MCP authentication failed.` — the server never reveals which check failed
  (unknown instance, wrong access key, bad signature, stale timestamp), so an
  attacker cannot use the response to enumerate instances or probe which
  part of a forgery was wrong.
- The signature authenticates the request target (method + path + query) and
  the timestamp; the request **body is not signed**. Transport integrity for
  the body is a transport concern (see "Transport assumptions").

## Protected endpoints

`McpConfiguration` registers the filter for:

- the MCP streamable endpoint (default `/api/mcp`, property
  `rocketmq.studio.ops.ai.mcp.mcp-endpoint`), and
- `/api/mcp/tools/call`.

## Transport assumptions

- Public endpoints must be reached over HTTPS: HMAC credentials must never
  travel in the clear over the Internet. The rmqctl client additionally
  permits plain HTTP only for loopback, RFC1918 private-network, and
  link-local targets (local development / same-VPC deployments).
- The client follows no redirects (`CheckRedirect` policy), so a redirect
  cannot silently re-target a signed request to a different host.

## Wire format

Each request carries three headers:

| Header | Meaning |
|---|---|
| `Authorization` | `rmq-hmac-sha256 Credential=<rfc3986-encoded access key>, Signature=<64 lowercase hex>` |
| `x-rmq-instance-id` | Studio instance identifier |
| `x-rmq-timestamp` | Epoch milliseconds, decimal, at signing time |

The credential access key is RFC 3986 percent-encoded (unreserved characters
`A-Z a-z 0-9 - . _ ~` kept as-is) because it may contain characters that are
not header-safe; the server percent-decodes it before comparison.

## Canonical request (string-to-sign)

Six lines joined with `\n` (in this exact order):

```
rmq-hmac-sha256
<access key>
<instance id>
<timestamp>
<HTTP method, uppercase>
<request target: path plus "?" + query when a query is present>
```

Reference implementations:

- Go client: `canonicalRequest` in `rmqctl/internal/studio/auth.go`
- Java server: `McpAuthenticator.canonicalRequest`

The request target **includes the query string** so that adding or removing
query parameters invalidates the signature. It is the request URI exactly as
sent (`URL.RequestURI()` on the client, `getRequestURI() + '?' + getQueryString()`
on the server) — no re-canonicalization, re-ordering, or re-encoding of
parameters happens on either side.

## Signature

```
signature = hex_lower(HMAC-SHA256(secretKey, UTF8(canonicalRequest)))
```

- Algorithm: `HmacSHA256`, key and message both UTF-8 bytes.
- Encoding: lowercase hexadecimal (64 characters).
- The server compares signatures in constant time (`MessageDigest.isEqual`)
  and compares the access key with a constant-time equality check as well.

## Timestamp and clock-skew rules

- Timestamps are epoch **milliseconds**.
- A request is accepted when `|now - timestamp| <= 5 minutes`
  (`MAX_CLOCK_SKEW = Duration.ofMinutes(5)`).
- A non-numeric timestamp is rejected identically to a stale one (opaque
  401).
- Retry behavior: the client signs per attempt with a fresh timestamp, so a
  retry after a clock-skew rejection needs no special handling; servers that
  return 401 for skew do not distinguish it from any other failure.

## Credentials

Access/secret key pairs are resolved per Studio instance by
`InstanceCredentialResolver` (instance cloud credentials). An instance with
no usable credential is indistinguishable from a wrong key (opaque 401);
a server-side credential-repository outage propagates as 500 rather than
being reported as a client authentication failure.

## Golden vectors and conformance

The shared golden vectors live in
`rmqctl/internal/studio/testdata/auth-hmac-vectors.json` and are consumed by
BOTH ends:

- Go client: `TestSigningRoundTripperMatchesGoldenVectors`
  (`rmqctl/internal/studio/auth_test.go`);
- Java server: `McpHmacGoldenVectorTest`
  (`server/src/test/.../auth/`), which recomputes every vector's canonical
  request and signature through `McpAuthenticator` itself.

Any change to the canonicalization or signing rules must update the vectors
in the same commit, keeping both conformance tests meaningful.

## Revisions and compatibility policy

- The algorithm token in the `Authorization` header (`rmq-hmac-sha256`)
  doubles as the revision identifier. The current contract is revision 1;
  any incompatible change (reordering the canonical lines, signing the body,
  changing the encoding) must mint a new algorithm token (for example
  `rmq-hmac-sha256/2`) so old and new clients can be distinguished on the
  wire.
- The server accepts exactly one revision at a time — the one it implements.
  An unsupported revision fails the `Authorization` header parse and gets the
  same opaque 401 as any other failure. Clients receiving 401 from a server
  should not retry with a different revision; they should surface the
  failure to the operator (the likely cause is a version-skewed server).
- Compatible additions (a new optional header, a new endpoint) do not need a
  new revision. When a new revision is introduced, the project should keep
  the previous revision accepted for one minor release cycle as a
  compatibility window, then remove it with a deprecation note in the
  release notes.

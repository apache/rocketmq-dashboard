# RocketMQ Studio security

RocketMQ Studio authentication is enabled unconditionally. There is no default
account, default password, or default password hash. If the configured registry
is absent, unreadable, insecure, or invalid, login fails closed.

## User registry

Set `STUDIO_SECURITY_USER_FILE` to one JSON file owned by the operating-system
user that runs Studio. The file uses this strict schema:

```json
{
  "schemaVersion": "v1",
  "users": [
    {
      "username": "replace-with-an-operator-selected-name",
      "passwordHash": "{bcrypt}<replace-with-a-cost-12-bcrypt-hash>",
      "role": "ADMIN"
    }
  ]
}
```

The parser rejects duplicate JSON keys, trailing input, unknown or missing
properties, invalid UTF-8, duplicate usernames, unsupported schema versions,
and files larger than 1 MiB. Usernames must match
`[A-Za-z0-9._@-]{1,128}`. `role` is exactly `USER` or `ADMIN`. Every
`passwordHash` must have the Spring `{bcrypt}` prefix and be a bcrypt hash with
cost 12 (`$2a$12$`, `$2b$12$`, or `$2y$12$`). The default maximum is 1,000
users and is configurable with `STUDIO_SECURITY_MAX_USERS`.

Generate passwords offline. For example, `htpasswd -nBC 12 <username>` prompts
without putting the password in shell history; copy only the generated hash,
prefix it with `{bcrypt}`, and do not reuse a RocketMQ credential. Treat hashes
as sensitive authentication material even though they are not plaintext.

### Ownership and permissions

On a POSIX filesystem, the registry must:

- be a regular file, never a symlink;
- be owned by the same operating-system user as the Studio process;
- grant owner read permission and no permissions to group or other users
  (`0600` is recommended; `0400` is also accepted at runtime); and
- have a trusted directory ancestry: directories cannot be group- or
  other-writable and must be owned by the Studio user or filesystem root.

The Compose and Podman procedures use a private named volume, install
`studio-users.json` with mode `0600`, and mount the private registry directory
read-only. `STUDIO_SECURITY_USER_FILE=/run/secrets/studio-users.json` selects
the exact file. The base Compose file contains no host-path bind. An empty
volume contains no account: Studio starts fail-closed and unready, and never
synthesizes credentials.

### Atomic replacement

Never edit the live file in place. Copy from an already validated immutable
snapshot into a temporary file in the same directory, set mode `0600`, then use
a same-filesystem atomic rename:

```bash
umask 077
registry_dir=/run/secrets
temporary=$(mktemp /run/secrets/.studio-users.json.tmp.XXXXXX)
trap 'rm -f -- "$temporary"' EXIT HUP INT TERM
cp /private-snapshot/studio-users.json "$temporary"
chmod 600 "$temporary"
mv -f -- "$temporary" "$registry_dir/studio-users.json"
trap - EXIT HUP INT TERM
```

The random `mktemp` name prevents concurrent helper processes from selecting
the same PID-based path. `deploy.sh` additionally serializes the account-level
Podman resource namespace with an owner-checked remote lock and uses per-run
image and staging names. Mounting the containing directory lets the running
server resolve the replacement inode after the atomic rename and observe
registry changes.

Before any build or network connection, `deploy.sh` opens every source ancestor
and the registry itself with no-follow semantics, validates ownership and mode
with `fstat`, and copies from the held file descriptor into a private `0600`
snapshot. It never reopens the operator pathname for transfer. Do not print,
pass as a command-line argument, or commit the registry content. Registry
changes are detected periodically; a change invalidates sessions tied to the
previous registry revision.

## Authorization policy

`POST /api/auth/login` and the documented GET health probes are public. Every
application API has an explicit minimum role:

- `USER` covers the narrowly enumerated read/query operations and logout.
- `ADMIN` covers mutation, credentials, configuration, diagnostics, and other
  sensitive operations.

Unknown or malformed routes default to `ADMIN`, not `USER`. Authentication and
authorization failures are produced by Spring; Nginx must pass the
`Authorization` request header and `WWW-Authenticate`/`Retry-After` response
headers unchanged.

Studio roles protect the Studio console only. RocketMQ ACL is a separate
authorization system enforced by NameServers, Brokers, and Proxies. A Studio
`ADMIN` does not automatically receive RocketMQ ACL privileges, and RocketMQ
access keys must not be used as Studio passwords.

## Sessions and process model

Login returns a cryptographically random opaque bearer token, not a JWT. Send
it as `Authorization: Bearer <token>`. Sessions expire after
`STUDIO_SECURITY_SESSION_TTL` (24 hours by default, allowed range 5 minutes to
24 hours), and the per-user default maximum is five active sessions.

Sessions are stored only in process memory. A server restart invalidates all
sessions. This single-process limitation means multiple server replicas do not
share login state; do not scale the server horizontally until a shared,
revocation-capable session store is implemented.

The browser currently stores the bearer token in `localStorage`. Any successful
same-origin XSS could read it. The restrictive Content-Security-Policy reduces
exposure but does not make `localStorage` secret: deploy only reviewed frontend
assets, avoid third-party scripts, use HTTPS, keep session TTLs bounded, log out
on shared devices, and rotate the affected registry entry after suspected
compromise.

## Health checks and fail-closed behavior

- `GET /actuator/health/liveness` reports process liveness. A bad or missing
  registry does not make the JVM dead.
- `GET /actuator/health/readiness` includes `studioSecurity`. It becomes
  unavailable when the user registry is not safely usable.

Route traffic only to a ready instance. Never replace readiness with liveness
for rollout decisions. Health responses do not expose registry paths, usernames,
hashes, or tokens.

## Network and TLS boundary

The backend has no published host port. The bundled web proxy is bound only to
`127.0.0.1`; use a local SSH tunnel for administrative access:

```bash
ssh -L 6789:127.0.0.1:6789 deploy-user@studio.example.com
```

Then open `http://127.0.0.1:6789` on the operator workstation. Do not expose
this loopback HTTP listener on a public interface.

For a network-accessible deployment, terminate HTTPS with
`deploy/nginx/rocketmq-studio-tls.conf.example`. Replace its example hostname,
mount the certificate at `/etc/nginx/tls/tls.crt` and key at
`/etc/nginx/tls/tls.key`, use an image containing the built Studio static
assets, and join the same private container network as `rocketmq-server`.
Keep the Studio upstream private. HSTS is emitted only by the HTTPS server. The
example log format omits query strings and `Authorization`.

Only the trusted TLS terminator may set forwarding headers. It must overwrite,
not append untrusted client values for `X-Forwarded-For`,
`X-Forwarded-Proto`, `X-Forwarded-Host`, and `X-Forwarded-Port`. Restrict direct
network access so clients cannot bypass the terminator.

## Migration from legacy settings

Legacy plaintext `users.properties` files are not a supported authentication
source. Migrate offline:

1. Restrict the old file to its owner and stop Studio.
2. For each retained user, choose a new password and generate a cost-12 bcrypt
   hash; do not copy plaintext passwords into JSON.
3. Create the strict `v1` registry in a mode-`0600` temporary file, validate it,
   and atomically replace the target as described above.
4. Set `STUDIO_SECURITY_USER_FILE` (or
   `STUDIO_SECURITY_USER_FILE_LOCAL` for `deploy.sh`), start Studio, and require
   users to log in again.
5. Remove the plaintext file and any backups according to your secure-deletion
   and retention policy.

The old general-setting fields `requireLogin` and `sessionTimeout` are
deprecated compatibility data and do not control Studio authentication.
Authentication is always required, and the effective TTL is
`STUDIO_SECURITY_SESSION_TTL`.

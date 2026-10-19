# Concurrent local rmqctl configuration updates

`config set-context`, `config use-context` (also `use`) and `config delete-context`
(also `delete`) serialize their complete read/modify/save transaction. After waiting,
a command reloads the latest configuration before applying its change. Independent
commands that modify different contexts therefore preserve each other's changes.
Updates to the same field apply in lock acquisition order; deletion and selection
are validated against the latest document.

The CLI waits up to 30 seconds for an exclusive native file lock. Ctrl-C cancels the
wait. A lock failure or timeout reports an error without writing the configuration.
Linux/macOS use flock and Windows uses LockFileEx through platform-specific
standard-library code, with no new Go modules. This protocol targets local filesystems;
network filesystem locking and third-party editors are outside its guarantees.

The stable `<config>.lock` sidecar remains after the command exits. Do not delete it
while commands may be running: doing so can create independent locks on different
files. The operating system releases a held lock when a process exits, so a surviving
sidecar does not indicate a stale lock and requires no manual recovery. The config
file itself continues to be replaced atomically, independently of the lock file.

Absolute path normalization and resolution of parent-directory symlinks give
alternative spellings of the same config path a shared lock. Config-file symlink or
hard-link aliases are not a supported way to share transactions: saving still
replaces the named file, following the existing `Store.Save` semantics.

On POSIX platforms, the existing 0700 parent directory and private config permission
rules also apply to the lock sidecar (created as 0600). Windows retains inherited
profile-directory ACL handling. The lock contains no configuration or credentials.
Strict YAML validation and environment-only credential references are unchanged.

Read-only config commands remain lock-free and see a complete configuration document.
`Store.Save` remains a complete document replacement; callers must use `Store.Update`
for incremental transactions. Older rmqctl versions and external writers do not
participate in the locking protocol, so they can still overwrite a concurrent update.

Regression tests run actual CLI command handlers in independent processes, verify
that mutations wait and reload the latest document, and cover rollback, cancellation,
lock permissions, parent-directory aliases and lock release after process death.

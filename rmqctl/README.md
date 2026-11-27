# rmqctl: standalone CLI and MCP bridge

[中文](README_zh.md) · [Studio setup](../README.md#quick-start)

`rmqctl` calls the Studio server; it does not connect directly to a broker.
The same binary can run read-only/catalog commands from a terminal or expose
Studio tools to an external MCP client.

## Build and inspect offline

The examples below use a POSIX shell on Linux or macOS, from the repository root.
Install Go **1.27.1 or newer** and Make first. Clear cross-compilation overrides
when building the executable you will run on this machine:

```sh
unset GOOS GOARCH
make -C rmqctl build
./rmqctl/bin/rmqctl version
./rmqctl/bin/rmqctl --help
./rmqctl/bin/rmqctl explain topic
./rmqctl/bin/rmqctl topic list --help
```

`version`, `explain`, and `--help` work without a running Studio or credentials.
The build also writes the binary's license material under `rmqctl/bin/legal/native`.
For other platforms, `make -C rmqctl build-all` builds the six targets declared in
the [Makefile](Makefile); those cross-compiled files are not necessarily runnable
on the build host.

## Configure a Studio access context

Start Studio using the [deployment guide](../deploy/README.md) and register the
instance you want to inspect. Obtain the access-key/secret-key pair configured
for that instance from its administrator. For Apache instances this is the
configured admin credential referenced by `adminCredentialRef`; cloud instances
use their referenced cloud credential. A Studio browser username/password or
session cookie is not this HMAC credential pair.

Have your shell or secret manager supply `RMQ_ACCESS_KEY` and `RMQ_SECRET_KEY` in
the environment. Do not put secret values in command arguments, this repository,
or an MCP configuration file. The flags below take **environment variable names**,
not the key values:

```sh
./rmqctl/bin/rmqctl config set-context local \
  --server http://127.0.0.1:6789 \
  --access-key-env RMQ_ACCESS_KEY \
  --secret-key-env RMQ_SECRET_KEY
./rmqctl/bin/rmqctl config use-context local
./rmqctl/bin/rmqctl config get-contexts
```

The context stores the server URL and `env:RMQ_ACCESS_KEY` / `env:RMQ_SECRET_KEY`
references. It does not store the resolved secrets or a default instance.
Configuration path precedence is `--config` > `RMQCTL_CONFIG` >
`~/.rmqctl/config.yaml`; use the same selected file on later invocations.
`--context local` overrides the selected current context for one invocation.

The example URL is the bundled frontend proxy, which also serves `/api`.
Use the externally reachable Studio base URL for your deployment, **without**
appending `/api` or `/api/mcp`. Public endpoints require HTTPS. Plain HTTP is
accepted only for loopback/private/link-local IP addresses and `localhost`.

## Make a read-only request

Replace `my-instance` with the registered **Studio instance name** (`rmq_instance.name`),
not its numeric database primary key or its cloud-provider instance ID:

```sh
./rmqctl/bin/rmqctl --instance-id my-instance topic list --output json
```

Every tool invocation needs `--instance-id`, even when a platform-level tool does
not declare an `instanceId` argument: the header is still used for authentication.
The key pair must match that instance. Table output is the default; `--output json`
and `--output yaml` are also available. Use `explain <resource>` and each command's
`--help` for the current catalog, required flags, capabilities, and risk level.

Mutating commands are not part of this quick start. L2/L3 changes use the server's
preview/confirmation-token flow. `--yes` only skips the terminal confirmation
prompt; it does not bypass server capability, permission, or L3 policy checks.

## Connect an external MCP client

From the same repository root, generate a snippet with an absolute executable path
and the explicit context and instance:

```sh
./rmqctl/bin/rmqctl --context local --instance-id my-instance mcp config \
  --command "$PWD/rmqctl/bin/rmqctl"
```

Copy the resulting `mcpServers` entry into your client's MCP configuration. This
command prints configuration only; it does not connect or verify credentials.
The client must launch the binary with the referenced credential variables in its
own environment. A desktop client launched separately does not necessarily inherit
your terminal environment. The snippet contains no secret values.

If you used a custom config file, pass `--config /absolute/path/config.yaml` when
generating the snippet too. `mcp stdio` reserves stdout for protocol messages;
keep it attached to the MCP client rather than using it as an interactive shell.

## Troubleshooting

- `--instance-id is required`: pass the Studio instance name explicitly; changing
  the current context does not choose an instance.
- `no current context is selected`: run `config use-context local` or pass
  `--context local` with the same config file used by `set-context`.
- `environment variable ... is empty`: supply that variable to the process that
  actually runs `rmqctl`, including the external MCP client when applicable.
- MCP authentication failure: check the Studio instance name, its configured key
  pair, and clock synchronization between client and server. Browser login does
  not authenticate these signed requests.

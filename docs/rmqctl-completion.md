# rmqctl shell completion

`rmqctl` can generate completion scripts for Bash, Zsh, Fish and PowerShell.
Install or build `rmqctl` first, and put the executable on your `PATH`.

## Load completion in the current shell

Bash:

```bash
source <(rmqctl completion bash)
```

Zsh (initialize completion before loading the script):

```zsh
autoload -Uz compinit
compinit
source <(rmqctl completion zsh)
```

Fish:

```fish
rmqctl completion fish | source
```

PowerShell:

```powershell
rmqctl completion powershell > rmqctl-completion.ps1
. ./rmqctl-completion.ps1
```

These examples affect the current shell. To load completion automatically, save
its generated script and source it from your shell's startup configuration. After
upgrading `rmqctl`, regenerate saved scripts. `rmqctl completion <shell> --help`
provides installation locations and examples for that shell.

## What can be completed

Press Tab where `<TAB>` appears; do not type the marker itself.

```text
rmqctl explain to<TAB>
rmqctl topic list --type F<TAB>
rmqctl topic update --perm R<TAB>
rmqctl --output j<TAB>
rmqctl --config ./contexts.yaml --context st<TAB>
rmqctl --config ./contexts.yaml config use-context st<TAB>
```

The first four examples suggest `topic`, `FIFO`, `RO`/`RW`, and `json`.
Context suggestions depend on your local configuration. The global `--context`
flag and the first argument of `config use-context`, `config delete-context` and
`config set-context` complete existing names; the `use` and `delete` aliases work
too. `set-context` still accepts a new name that is not among the suggestions.

Context lookup uses the same path precedence as normal commands: `--config`, then
`RMQCTL_CONFIG`, then `~/.rmqctl/config.yaml`. It does not require a selected current
context, credential environment values or `--instance-id`.

Catalog enum suggestions come from the same catalog metadata that validates the
command's arguments. Values and prefixes are case-sensitive. An unmatched prefix
returns no candidates and does not fall back to unrelated files. The `--config`
flag itself still uses ordinary filesystem completion.

## Local-only behavior and troubleshooting

Completion reads local context names and built-in metadata. It never resolves
credentials, calls Studio, modifies config or approves a mutation. Instance IDs,
topic names and consumer-group names are not looked up remotely; type those values
explicitly. Completion does not change the normal requirement to pass
`--instance-id` when executing a tool.

A missing or empty config produces no context candidates. An unreadable or invalid
config produces an error completion directive without printing config contents as
candidate text; `rmqctl config get-contexts --config <path>` can report the config
error explicitly. Context names containing control characters are omitted because
tabs and newlines are separators in the shell completion protocol. Unicode and
ordinary spaces are supported; your shell's generated script handles quoting.

For a shell-independent check of the completion protocol, run:

```text
rmqctl __complete topic list --type F
FIFO
:4
```

`:4` is Cobra's `NoFileComp` directive, not a candidate. `__complete` is the internal
entry point used by generated scripts; users normally just press Tab.

/**
 * Minimal, dependency-free argument parser. Keeps the CLI lightweight while
 * supporting both long (`--flag value`) and short (`-o json`) forms.
 */

export type OutputFormat = "json" | "yaml" | "table";

export interface CliOptions {
  cluster?: string;
  output: OutputFormat;
  server: string;
  user?: string;
  password?: string;
  token?: string;
  dryRun: boolean;
  yes: boolean;
  enableDangerousOps: boolean;
  help: boolean;
  version: boolean;
}

export interface ParsedArgs {
  /** "explain" when the first positional is `explain`, otherwise undefined. */
  subcommand?: string;
  /** Remaining positional tokens after stripping a leading `explain`. */
  positionals: string[];
  options: CliOptions;
}

const NO_VALUE_FLAGS = new Set([
  "--dry-run",
  "--yes",
  "--enable-dangerous-ops",
  "--help",
  "-h",
  "--version",
  "-V",
]);

const ALIASES: Record<string, string> = {
  "-o": "--output",
  "-s": "--server",
  "-u": "--user",
  "-p": "--password",
  "-h": "--help",
  "-V": "--version",
};

function resolveName(raw: string): string {
  return ALIASES[raw] ?? raw;
}

function defaultFor(name: string): string | boolean {
  switch (name) {
    case "--output":
      return "table";
    case "--server":
      return process.env.RMQ_STUDIO_URL || "http://localhost:8080";
    case "--user":
      return process.env.RMQ_USER || "";
    case "--password":
      return process.env.RMQ_PASSWORD || "";
    default:
      return false;
  }
}

function asString(value: string | boolean | undefined, fallback = ""): string | undefined {
  if (typeof value !== "string") return fallback || undefined;
  return value || undefined;
}

export function parseArgs(argv: string[]): ParsedArgs {
  const positionals: string[] = [];
  const rawFlags: Record<string, string | boolean> = {};

  for (let i = 0; i < argv.length; i++) {
    const token = argv[i];
    if (token.startsWith("-")) {
      const name = resolveName(token);
      if (NO_VALUE_FLAGS.has(name)) {
        rawFlags[name] = true;
      } else {
        const next = argv[i + 1];
        if (next !== undefined && !next.startsWith("-")) {
          rawFlags[name] = next;
          i++;
        } else {
          rawFlags[name] = defaultFor(name);
        }
      }
    } else {
      positionals.push(token);
    }
  }

  const subcommand = positionals[0] === "explain" ? "explain" : undefined;
  const rest = subcommand ? positionals.slice(1) : positionals;

  const options: CliOptions = {
    cluster: asString(rawFlags["--cluster"]),
    output: (asString(rawFlags["--output"], "table") as OutputFormat) ?? "table",
    server: asString(rawFlags["--server"]) || (process.env.RMQ_STUDIO_URL || "http://localhost:8080"),
    user: asString(rawFlags["--user"]),
    password: asString(rawFlags["--password"]),
    token: asString(rawFlags["--token"]),
    dryRun: Boolean(rawFlags["--dry-run"]),
    yes: Boolean(rawFlags["--yes"]),
    enableDangerousOps: Boolean(rawFlags["--enable-dangerous-ops"]),
    help: Boolean(rawFlags["--help"]),
    version: Boolean(rawFlags["--version"]),
  };

  return { subcommand, positionals: rest, options };
}

/**
 * Split the positional tokens of a tool invocation into resource/verb words
 * and `key=value` arguments. Words without `=` become resource/verb; tokens
 * containing `=` become input parameters.
 */
export function splitToolArgs(positionals: string[]): {
  resource: string;
  verb?: string;
  kv: Record<string, string>;
} {
  const kv: Record<string, string> = {};
  const words: string[] = [];
  for (const token of positionals) {
    const eq = token.indexOf("=");
    if (eq > 0) {
      kv[token.slice(0, eq)] = token.slice(eq + 1);
    } else {
      words.push(token);
    }
  }
  return { resource: words[0] ?? "", verb: words[1], kv };
}

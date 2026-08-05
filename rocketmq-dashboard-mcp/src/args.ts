/**
 * Minimal argument parser for the MCP server. Keeps the binary dependency-free
 * for flag parsing while supporting long (`--flag value`) and short (`-s url`)
 * forms. Auth/env defaults follow the same convention as the `rmqctl` CLI.
 */

export interface McpOptions {
  server: string;
  user?: string;
  password?: string;
  token?: string;
  cluster?: string;
  help: boolean;
}

export function parseMcpArgs(argv: string[]): McpOptions {
  const opts: McpOptions = {
    server: process.env.RMQ_STUDIO_URL || "http://localhost:8080",
    user: process.env.RMQ_USER,
    password: process.env.RMQ_PASSWORD,
    token: process.env.RMQ_TOKEN,
    help: false,
  };

  const take = (i: number): string | undefined => {
    const v = argv[i + 1];
    return v !== undefined && !v.startsWith("-") ? v : undefined;
  };

  for (let i = 0; i < argv.length; i++) {
    const t = argv[i];
    switch (t) {
      case "--server":
      case "-s": {
        const v = take(i);
        if (v) {
          opts.server = v;
          i++;
        }
        break;
      }
      case "--user":
      case "-u": {
        const v = take(i);
        if (v) {
          opts.user = v;
          i++;
        }
        break;
      }
      case "--password":
      case "-p": {
        const v = take(i);
        if (v) {
          opts.password = v;
          i++;
        }
        break;
      }
      case "--token":
      case "-t": {
        const v = take(i);
        if (v) {
          opts.token = v;
          i++;
        }
        break;
      }
      case "--cluster":
      case "-c": {
        const v = take(i);
        if (v) {
          opts.cluster = v;
          i++;
        }
        break;
      }
      case "--help":
      case "-h":
        opts.help = true;
        break;
      default:
        break;
    }
  }

  return opts;
}

package org.apache.rocketmq.dashboard.cli;

import org.apache.rocketmq.dashboard.cli.mcp.McpServerApplication;
import picocli.CommandLine;

/**
 * Single entry point for the RIP-3 executable jar.
 *
 * <ul>
 *   <li>{@code rmqctl ...} - the data-driven operations CLI (default).</li>
 *   <li>{@code mcp [--transport stdio|sse] [--port N]} - the MCP server.</li>
 * </ul>
 */
public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && "mcp".equals(args[0])) {
            String[] rest = new String[args.length - 1];
            System.arraycopy(args, 1, rest, 0, rest.length);
            McpServerApplication.run(rest);
            return;
        }
        int exit = new CommandLine(new RmqctlCommand()).execute(args);
        System.exit(exit);
    }
}

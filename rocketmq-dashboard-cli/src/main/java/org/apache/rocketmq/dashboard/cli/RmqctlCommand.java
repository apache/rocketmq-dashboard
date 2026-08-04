package org.apache.rocketmq.dashboard.cli;

import org.apache.rocketmq.dashboard.cli.executor.ErrorModel;
import org.apache.rocketmq.dashboard.cli.executor.InvocationContext;
import org.apache.rocketmq.dashboard.cli.executor.ToolException;
import org.apache.rocketmq.dashboard.cli.executor.ToolExecutor;
import org.apache.rocketmq.dashboard.cli.schema.ToolDefinition;
import org.apache.rocketmq.dashboard.cli.schema.ToolRegistry;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * {@code rmqctl} - the CLI front-end of RIP-3. It is fully data-driven: the
 * resource/verb pair maps to a {@link ToolDefinition} in {@link ToolRegistry},
 * so adding a tool there automatically yields a CLI command with identical help
 * text and parameters as the MCP surface (RIP-3 signal 7).
 *
 * <pre>
 *   rmqctl &lt;resource&gt; &lt;verb&gt; [key=value ...] --cluster &lt;name&gt;
 *         [--output json|yaml|table] [--dry-run] [--yes] [--enable-dangerous-ops]
 *   rmqctl explain &lt;resource&gt;
 *   rmqctl mcp [--transport stdio|sse] [--port &lt;n&gt;]
 * </pre>
 */
@Command(name = "rmqctl", mixinStandardHelpOptions = true,
        description = "RocketMQ operations CLI (RIP-3).")
public class RmqctlCommand implements Callable<Integer> {

    @Parameters(index = "0", arity = "0..*",
            description = "resource verb [key=value ...] | explain <resource> | mcp [...]")
    List<String> positionals = List.of();

    @Option(names = {"--cluster"}, description = "Cluster NameServer address or name.")
    String cluster;

    @Option(names = {"--output", "-o"}, defaultValue = "table",
            description = "Output format: json, yaml or table.")
    String output;

    @Option(names = {"--dry-run"}, description = "Preview the change without applying it.")
    boolean dryRun;

    @Option(names = {"--yes"}, description = "Confirm a mutating (L2/L3) operation.")
    boolean yes;

    @Option(names = {"--enable-dangerous-ops"},
            description = "Opt in to destructive (L3) operations.")
    boolean enableDangerousOps;

    @Override
    public Integer call() {
        if (positionals.isEmpty() || positionals.get(0).equals("help")) {
            printGeneralHelp();
            return 0;
        }
        String head = positionals.get(0);
        if ("explain".equals(head)) {
            String resource = positionals.size() > 1 ? positionals.get(1) : null;
            ExplainCommand.run(resource);
            return 0;
        }
        if (positionals.size() < 2) {
            System.err.println("Usage: rmqctl <resource> <verb> [key=value ...] --cluster <name>");
            return 2;
        }
        return runTool(positionals.get(0), positionals.get(1),
                positionals.subList(2, positionals.size()));
    }

    private Integer runTool(String resource, String verb, List<String> kvPairs) {
        String toolName = "rmq." + resource + "." + verb;
        ToolDefinition tool = ToolRegistry.getInstance().getTool(toolName);
        if (tool == null) {
            System.err.println("Unknown tool: " + toolName);
            System.err.println("Run `rmqctl explain` to list available tools.");
            return 2;
        }

        Map<String, Object> args = new LinkedHashMap<>();
        if (cluster != null) {
            args.put("cluster", cluster);
        }
        for (String kv : kvPairs) {
            int eq = kv.indexOf('=');
            if (eq > 0) {
                args.put(kv.substring(0, eq), kv.substring(eq + 1));
            }
        }
        // Safety flags are passed as underscore-prefixed control args.
        args.put("_dryRun", String.valueOf(dryRun));
        args.put("_confirmed", String.valueOf(yes));
        args.put("_dangerous", String.valueOf(enableDangerousOps));

        try {
            InvocationContext ctx = new InvocationContext(dryRun, yes, enableDangerousOps);
            Map<String, Object> result = ToolExecutor.execute(tool, args, ctx);
            OutputFormatter.print(result, output);
            return 0;
        } catch (ToolException e) {
            ErrorModel em = e.getError();
            OutputFormatter.printError(em, output);
            return 1;
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private void printGeneralHelp() {
        System.out.println("rmqctl - RocketMQ operations CLI (RIP-3)");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  rmqctl <resource> <verb> [key=value ...] --cluster <name>");
        System.out.println("        [--output json|yaml|table] [--dry-run] [--yes] [--enable-dangerous-ops]");
        System.out.println("  rmqctl explain <resource>");
        System.out.println("  rmqctl mcp [--transport stdio|sse] [--port <n>]");
        System.out.println();
        System.out.println("Resources: " + String.join(", ",
                ToolRegistry.getInstance().getAllTools().stream()
                        .map(ToolDefinition::getResource).distinct().sorted().toList()));
    }
}

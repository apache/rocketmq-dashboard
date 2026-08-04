package org.apache.rocketmq.dashboard.cli.executor;

/**
 * Flags controlling how a mutating tool may execute (RIP-3 signal 4):
 * <ul>
 *   <li>{@code dryRun} - preview the change without applying it.</li>
 *   <li>{@code confirmed} - the caller explicitly confirmed a mutating (L2/L3) op.</li>
 *   <li>{@code dangerousOpsEnabled} - the session opted into destructive (L3) ops.</li>
 * </ul>
 */
public record InvocationContext(boolean dryRun, boolean confirmed, boolean dangerousOpsEnabled) {
}

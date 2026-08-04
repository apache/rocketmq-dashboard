package org.apache.rocketmq.dashboard.cli.schema;

/**
 * Risk tier of a tool, driving the security gate (RIP-3 signal 4):
 * <ul>
 *   <li>{@code L1} - read-only, always allowed.</li>
 *   <li>{@code L2} - mutating but reversible/safe; defaults to dry-run and
 *       requires an explicit confirmation step before the live apply.</li>
 *   <li>{@code L3} - destructive / irreversible; disabled by default and only
 *       executable when explicitly opted in via {@code --enable-dangerous-ops}
 *       together with {@code --yes --force}.</li>
 * </ul>
 */
public enum RiskLevel {
    L1,
    L2,
    L3
}

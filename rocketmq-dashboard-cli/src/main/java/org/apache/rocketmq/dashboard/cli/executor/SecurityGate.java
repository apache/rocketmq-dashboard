package org.apache.rocketmq.dashboard.cli.executor;

import org.apache.rocketmq.dashboard.cli.schema.RiskLevel;

/**
 * Enforces the RIP-3 safety model (signal 4):
 * <ul>
 *   <li>L1 tools always run live.</li>
 *   <li>L2 tools default to a dry-run; a live apply requires explicit confirmation.</li>
 *   <li>L3 tools are disabled unless {@code --enable-dangerous-ops} is set, and
 *       additionally require confirmation.</li>
 * </ul>
 */
public final class SecurityGate {

    private SecurityGate() {
    }

    public enum Decision {
        /** Tool may run live now. */
        APPLY,
        /** Tool should only report what it would do (dry-run). */
        DRY_RUN,
        /** Tool is blocked; caller must confirm before proceeding. */
        REQUIRES_CONFIRMATION,
        /** Tool is explicitly denied for this session. */
        DENIED
    }

    public static Decision evaluate(RiskLevel level, boolean dryRunRequested,
                                    boolean dangerousOpsEnabled, boolean confirmed) {
        switch (level) {
            case L1:
                return Decision.APPLY;
            case L2:
                if (dryRunRequested) {
                    return Decision.DRY_RUN;
                }
                return confirmed ? Decision.APPLY : Decision.REQUIRES_CONFIRMATION;
            case L3:
                if (!dangerousOpsEnabled) {
                    return Decision.DENIED;
                }
                if (dryRunRequested) {
                    return Decision.DRY_RUN;
                }
                return confirmed ? Decision.APPLY : Decision.REQUIRES_CONFIRMATION;
            default:
                return Decision.DENIED;
        }
    }

    public static String denialHint(RiskLevel level) {
        if (level == RiskLevel.L3) {
            return "This is a destructive (L3) operation. Re-run with --enable-dangerous-ops "
                    + "and --yes --force to opt in.";
        }
        return "Re-run with --yes to confirm this mutating (L2) operation, or --dry-run to "
                + "preview the change without applying it.";
    }
}

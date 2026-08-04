package org.apache.rocketmq.dashboard.cli.context;

import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.apache.rocketmq.tools.admin.MQAdminExt;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thin wrapper around a real {@link MQAdminExt} connection to a RocketMQ cluster,
 * identified by its NameServer address. Used by every resource tool to talk to a
 * live cluster (RIP-3 requirement: real data, no mock).
 *
 * <p>Obtain via {@link #connect(String)} inside a try-with-resources block so the
 * underlying admin client is always shut down.</p>
 */
public final class AdminClientHelper implements AutoCloseable {

    private final MQAdminExt admin;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private AdminClientHelper(MQAdminExt admin) {
        this.admin = admin;
    }

    public static AdminClientHelper connect(String namesrvAddr) throws Exception {
        if (namesrvAddr == null || namesrvAddr.isBlank()) {
            throw new IllegalArgumentException("A non-empty cluster NameServer address is required.");
        }
        DefaultMQAdminExt admin = new DefaultMQAdminExt();
        admin.setNamesrvAddr(namesrvAddr.trim());
        admin.start();
        return new AdminClientHelper(admin);
    }

    public MQAdminExt admin() {
        return admin;
    }

    @Override
    public void close() throws Exception {
        if (closed.compareAndSet(false, true)) {
            admin.shutdown();
        }
    }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.dashboard.cli;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.apache.rocketmq.client.QueryResult;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.dashboard.cli.context.AdminClientHelper;
import org.apache.rocketmq.dashboard.cli.output.OutputFormatter;
import org.apache.rocketmq.dashboard.cli.security.AuditLogger;
import org.apache.rocketmq.dashboard.cli.security.DryRunResult;
import org.apache.rocketmq.remoting.protocol.body.Connection;
import org.apache.rocketmq.remoting.protocol.body.ConsumeMessageDirectlyResult;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

@Command(name = "message", description = "Message management commands",
        subcommands = {MessageCommand.QueryByIdCmd.class, MessageCommand.QueryByTimeCmd.class,
                MessageCommand.ResendCmd.class})

/** CLI commands for message operations: query-by-id, query-by-time, resend (L2). */
public class MessageCommand {

    @ParentCommand
    RmqctlCommand root;

    private static final String DATETIME_FMT = "yyyy-MM-dd HH:mm:ss.SSS";

    private static String formatTimestamp(long ts) {
        return new SimpleDateFormat(DATETIME_FMT).format(new Date(ts));
    }

    private static String formatSocketAddress(InetSocketAddress addr) {
        if (addr == null) {
            return "-";
        }
        return addr.getAddress().getHostAddress() + ":" + addr.getPort();
    }

    private static String bodyPreview(byte[] body, int maxLen) {
        if (body == null || body.length == 0) {
            return "(empty)";
        }
        String text = new String(body, StandardCharsets.UTF_8);
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "...";
    }

    @Command(name = "query-by-id", description = "Query a message by its ID (L1)")
    static class QueryByIdCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Message ID")
        String messageId;

        @Parameters(index = "1", description = "Topic name")
        String topicName;

        @ParentCommand
        MessageCommand parent;

        @Override
        public Integer call() throws Exception {
            MQAdminExt admin = null;
            try {
                admin = AdminClientHelper.connectRaw(null, parent.root);
                MessageExt msg = admin.viewMessage(topicName, messageId);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("Message ID", msg.getMsgId() != null ? msg.getMsgId() : messageId);
                result.put("Topic", msg.getTopic() != null ? msg.getTopic() : topicName);
                result.put("Tags", msg.getTags() != null ? msg.getTags() : "-");
                result.put("Keys", msg.getKeys() != null ? msg.getKeys() : "-");
                result.put("Queue ID", String.valueOf(msg.getQueueId()));
                result.put("Offset", String.valueOf(msg.getQueueOffset()));
                result.put("Born Time", formatTimestamp(msg.getBornTimestamp()));
                result.put("Store Time", formatTimestamp(msg.getStoreTimestamp()));
                result.put("Born Host", msg.getBornHost() != null
                        ? formatSocketAddress((InetSocketAddress) msg.getBornHost()) : "-");
                result.put("Store Host", msg.getStoreHost() != null
                        ? formatSocketAddress((InetSocketAddress) msg.getStoreHost()) : "-");
                result.put("Body Size", (msg.getBody() != null ? msg.getBody().length : 0) + " bytes");
                result.put("Body Preview", bodyPreview(msg.getBody(), 200));

                System.out.println(OutputFormatter.format(result, OutputFormatter.Format.TABLE));
                return 0;
            } catch (Exception e) {
                System.err.println("Error: Failed to query message '" + messageId + "' in topic '"
                        + topicName + "' - " + e.getMessage());
                return 1;
            } finally {
                if (admin != null) {
                    admin.shutdown();
                }
            }
        }
    }

    @Command(name = "query-by-time", description = "Query messages by time range (L1)")
    static class QueryByTimeCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Topic name")
        String topicName;

        @Option(names = {"--begin"}, description = "Begin timestamp (format: yyyy-MM-dd HH:mm:ss)")
        String beginT;

        @Option(names = {"--end"}, description = "End timestamp (format: yyyy-MM-dd HH:mm:ss)")
        String endT;

        @Option(names = {"--max"}, description = "Maximum number of messages", defaultValue = "10")
        int maxNum;

        @ParentCommand
        MessageCommand parent;

        @Override
        public Integer call() throws Exception {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            long endTimestamp;
            long beginTimestamp;

            try {
                endTimestamp = (endT != null) ? sdf.parse(endT).getTime() : System.currentTimeMillis();
                beginTimestamp = (beginT != null) ? sdf.parse(beginT).getTime()
                        : endTimestamp - 30 * 60 * 1000L;
            } catch (Exception e) {
                System.err.println("Error: Invalid timestamp format. Use 'yyyy-MM-dd HH:mm:ss'.");
                return 1;
            }

            MQAdminExt admin = null;
            try {
                admin = AdminClientHelper.connectRaw(null, parent.root);

                System.out.println("Topic: " + topicName);
                System.out.println("Time Range: " + formatTimestamp(beginTimestamp)
                        + " ~ " + formatTimestamp(endTimestamp));
                System.out.println("Max Results: " + maxNum);
                System.out.println();

                QueryResult queryResult = admin.queryMessage(topicName, "*", maxNum,
                        beginTimestamp, endTimestamp);

                List<MessageExt> messages = queryResult.getMessageList();
                if (messages == null || messages.isEmpty()) {
                    System.out.println("No messages found in the specified time range.");
                    return 0;
                }

                List<Map<String, Object>> rows = new ArrayList<>();
                for (MessageExt msg : messages) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("MESSAGE ID", msg.getMsgId() != null ? msg.getMsgId() : "-");
                    row.put("QUEUE", String.valueOf(msg.getQueueId()));
                    row.put("OFFSET", String.valueOf(msg.getQueueOffset()));
                    row.put("BORN TIME", formatTimestamp(msg.getBornTimestamp()));
                    row.put("STORE TIME", formatTimestamp(msg.getStoreTimestamp()));
                    row.put("SIZE", msg.getStoreSize() + " B");
                    rows.add(row);
                }

                System.out.println(OutputFormatter.format(rows, OutputFormatter.Format.TABLE));
                System.out.println();
                System.out.println("Found " + messages.size() + " message(s).");
                return 0;
            } catch (Exception e) {
                System.err.println("Error: Failed to query messages by time - " + e.getMessage());
                return 1;
            } finally {
                if (admin != null) {
                    admin.shutdown();
                }
            }
        }
    }

    @Command(name = "resend", description = "Resend a message to a consumer group (L2 - default dry-run)")
    static class ResendCmd implements Callable<Integer> {
        @Parameters(index = "0", description = "Message ID")
        String messageId;

        @Parameters(index = "1", description = "Target consumer group")
        String groupName;

        @Parameters(index = "2", description = "Topic name")
        String topicName;

        @ParentCommand
        MessageCommand parent;

        @Override
        public Integer call() throws Exception {
            if (parent.root != null && parent.root.isYes()) {
                MQAdminExt admin = null;
                try {
                    admin = AdminClientHelper.connectRaw(null, parent.root);

                    // Find a live consumer clientId from the target group
                    String clientId = null;
                    ConsumerConnection cc = admin.examineConsumerConnectionInfo(groupName);
                    if (cc != null && cc.getConnectionSet() != null) {
                        for (Connection conn : cc.getConnectionSet()) {
                            if (conn.getClientId() != null && !conn.getClientId().isEmpty()) {
                                clientId = conn.getClientId();
                                break;
                            }
                        }
                    }

                    if (clientId == null) {
                        System.err.println("Error: No active consumer found in group '" + groupName
                                + "'. Ensure at least one consumer is online.");
                        return 1;
                    }

                    ConsumeMessageDirectlyResult result = admin.consumeMessageDirectly(
                            groupName, clientId, topicName, messageId);

                    String clusterName = parent.root.getCluster();
                    System.out.println("Message '" + messageId + "' resent to group '" + groupName
                            + "' via consumer '" + clientId + "'.");
                    System.out.println("Result: " + (result.getConsumeResult() != null
                            ? result.getConsumeResult().name() : "UNKNOWN"));
                    if (result.getRemark() != null && !result.getRemark().isEmpty()) {
                        System.out.println("Remark: " + result.getRemark());
                    }
                    System.out.println("Time spent: " + result.getSpentTimeMills() + " ms");

                    AuditLogger.getInstance().log(
                            clusterName != null ? clusterName : "(default)",
                            "message resend " + messageId + " -> " + groupName,
                            "SUCCESS",
                            System.getProperty("user.name", "unknown"));
                    return 0;
                } catch (Exception e) {
                    System.err.println("Error: Failed to resend message '" + messageId
                            + "' to group '" + groupName + "' - " + e.getMessage());
                    AuditLogger.getInstance().log(
                            parent.root.getCluster() != null ? parent.root.getCluster() : "(default)",
                            "message resend " + messageId + " -> " + groupName,
                            "FAILED: " + e.getMessage(),
                            System.getProperty("user.name", "unknown"));
                    return 1;
                } finally {
                    if (admin != null) {
                        admin.shutdown();
                    }
                }
            }

            // Dry-run preview
            Map<String, Object> changeDetails = new LinkedHashMap<>();
            changeDetails.put("messageId", messageId);
            changeDetails.put("targetGroup", groupName);
            changeDetails.put("topic", topicName);

            DryRunResult dryRun = DryRunResult.builder()
                    .operation("resend message " + messageId + " to group " + groupName)
                    .willExecute(true)
                    .affectedResources(Arrays.asList(
                            "Message: " + messageId,
                            "Consumer Group: " + groupName))
                    .changeDetails(changeDetails)
                    .estimatedDuration("< 1 second")
                    .warnings(Arrays.asList(
                            "Resending messages may cause duplicate processing.",
                            "Ensure the target consumer group has idempotent handling."))
                    .build();
            System.out.println(dryRun.toDisplay());
            System.out.println("Run with --yes to confirm and execute.");
            return 0;
        }
    }
}

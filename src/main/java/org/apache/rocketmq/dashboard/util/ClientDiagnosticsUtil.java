package org.apache.rocketmq.dashboard.util;

import org.apache.rocketmq.dashboard.model.ClientInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Client diagnostics utility
 * Provides unified diagnostics for both Remoting and gRPC clients
 */
@Component
public class ClientDiagnosticsUtil {

    private static final Logger log = LoggerFactory.getLogger(ClientDiagnosticsUtil.class);

    /**
     * Diagnose common client issues
     */
    public ClientDiagnosisResult diagnoseClient(ClientInstance client) {
        ClientDiagnosisResult result = new ClientDiagnosisResult();
        result.setClientId(client.getClientId());
        result.setClientAddress(client.getClientAddress());
        result.setProtocolType(client.getProtocolType());

        List<String> issues = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        // Check last heartbeat time
        if (client.getLastHeartbeatTime() != null) {
            long timeSinceLastHeartbeat = System.currentTimeMillis() - client.getLastHeartbeatTime();
            if (timeSinceLastHeartbeat > 300000) { // 5 minutes
                issues.add("Client has not sent heartbeat for " + (timeSinceLastHeartbeat / 1000) + " seconds");
                recommendations.add("Check network connectivity and client status");
            }
        } else {
            issues.add("No heartbeat information available");
            recommendations.add("Verify client is properly configured and running");
        }

        // Check client version compatibility
        if (client.getVersion() != null) {
            if (isOldVersion(client.getVersion())) {
                issues.add("Client is using older version: " + client.getVersion());
                recommendations.add("Consider upgrading client to latest version");
            }
        }

        // Check protocol compatibility
        if (client.getProtocolType() != null) {
            if (!isSupportedProtocol(client.getProtocolType())) {
                issues.add("Unsupported protocol type: " + client.getProtocolType());
                recommendations.add("Use supported protocol (Remoting or gRPC)");
            }
        }

        // Check subscription count for consumers
        if ("CONSUMER".equals(client.getClientType())) {
            if (client.getSubscriptionCount() != null && client.getSubscriptionCount() > 100) {
                issues.add("Consumer has too many subscriptions: " + client.getSubscriptionCount());
                recommendations.add("Consider reducing subscription count for better performance");
            }
        }

        // Check client status
        if (client.getStatus() != null && !"ONLINE".equals(client.getStatus())) {
            issues.add("Client status is " + client.getStatus());
            recommendations.add("Check client logs for status issues");
        }

        // Check connection time
        if (client.getConnectTime() != null) {
            long connectionDuration = System.currentTimeMillis() - client.getConnectTime();
            if (connectionDuration < 60000) { // 1 minute
                recommendations.add("Client connected recently, monitor for stability");
            }
        }

        result.setIssues(issues);
        result.setRecommendations(recommendations);
        result.setOverallHealth(calculateHealthScore(issues, recommendations));

        log.info("Diagnosed client {}: {} issues found", client.getClientId(), issues.size());
        return result;
    }

    /**
     * Get protocol compatibility information
     */
    public ProtocolCompatibilityInfo getProtocolCompatibility() {
        ProtocolCompatibilityInfo info = new ProtocolCompatibilityInfo();

        info.addSupportedProtocol("Remoting", Arrays.asList("4.0", "4.1", "4.2", "4.3", "4.4", "4.5", "4.6", "4.7", "4.8", "4.9"));
        info.addSupportedProtocol("gRPC", Arrays.asList("5.0", "5.1"));

        info.addFeature("Remoting", "Low latency");
        info.addFeature("Remoting", "High throughput");
        info.addFeature("Remoting", "Direct broker connection");
        info.addFeature("Remoting", "Traditional RocketMQ protocol");

        info.addFeature("gRPC", "HTTP/2 support");
        info.addFeature("gRPC", "Cross-language compatibility");
        info.addFeature("gRPC", "Proxy support");
        info.addFeature("gRPC", "Bidirectional streaming");

        return info;
    }

    /**
     * Classify clients by health status
     */
    public Map<String, List<ClientInstance>> classifyClientsByHealth(List<ClientInstance> clients) {
        Map<String, List<ClientInstance>> classification = new HashMap<>();
        classification.put("HEALTHY", new ArrayList<>());
        classification.put("WARNING", new ArrayList<>());
        classification.put("CRITICAL", new ArrayList<>());

        for (ClientInstance client : clients) {
            ClientDiagnosisResult result = diagnoseClient(client);
            String healthStatus = determineHealthStatus(result);
            classification.get(healthStatus).add(client);
        }

        return classification;
    }

    /**
     * Get client summary statistics
     */
    public ClientStatistics getClientStatistics(List<ClientInstance> clients) {
        ClientStatistics stats = new ClientStatistics();

        stats.setTotalCount(clients.size());

        // Group by type
        long producerCount = clients.stream().filter(c -> "PRODUCER".equals(c.getClientType())).count();
        long consumerCount = clients.stream().filter(c -> "CONSUMER".equals(c.getClientType())).count();
        stats.setProducerCount((int) producerCount);
        stats.setConsumerCount((int) consumerCount);

        // Group by protocol
        long remotingCount = clients.stream().filter(c -> "Remoting".equals(c.getProtocolType())).count();
        long grpcCount = clients.stream().filter(c -> "gRPC".equals(c.getProtocolType())).count();
        stats.setRemotingCount((int) remotingCount);
        stats.setGrpcCount((int) grpcCount);

        // Group by status
        long onlineCount = clients.stream().filter(c -> "ONLINE".equals(c.getStatus())).count();
        long offlineCount = clients.stream().filter(c -> "OFFLINE".equals(c.getStatus())).count();
        stats.setOnlineCount((int) onlineCount);
        stats.setOfflineCount((int) offlineCount);

        return stats;
    }

    private boolean isOldVersion(String version) {
        // Simple version check - consider versions before 4.9 as old
        return version != null && version.startsWith("4.") && Integer.parseInt(version.substring(2)) < 9;
    }

    private boolean isSupportedProtocol(String protocol) {
        return "Remoting".equals(protocol) || "gRPC".equals(protocol);
    }

    private String calculateHealthScore(List<String> issues, List<String> recommendations) {
        if (issues.isEmpty()) {
            return "GOOD";
        } else if (issues.size() <= 2) {
            return "FAIR";
        } else {
            return "POOR";
        }
    }

    private String determineHealthStatus(ClientDiagnosisResult result) {
        if (result.getOverallHealth().equals("GOOD")) {
            return "HEALTHY";
        } else if (result.getOverallHealth().equals("FAIR")) {
            return "WARNING";
        } else {
            return "CRITICAL";
        }
    }

    /**
     * Client diagnosis result
     */
    public static class ClientDiagnosisResult {
        private String clientId;
        private String clientAddress;
        private String protocolType;
        private List<String> issues;
        private List<String> recommendations;
        private String overallHealth;

        // Getters and setters
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }

        public String getClientAddress() { return clientAddress; }
        public void setClientAddress(String clientAddress) { this.clientAddress = clientAddress; }

        public String getProtocolType() { return protocolType; }
        public void setProtocolType(String protocolType) { this.protocolType = protocolType; }

        public List<String> getIssues() { return issues; }
        public void setIssues(List<String> issues) { this.issues = issues; }

        public List<String> getRecommendations() { return recommendations; }
        public void setRecommendations(List<String> recommendations) { this.recommendations = recommendations; }

        public String getOverallHealth() { return overallHealth; }
        public void setOverallHealth(String overallHealth) { this.overallHealth = overallHealth; }
    }

    /**
     * Protocol compatibility information
     */
    public static class ProtocolCompatibilityInfo {
        private Map<String, List<String>> supportedProtocols = new HashMap<>();
        private Map<String, List<String>> protocolFeatures = new HashMap<>();

        public void addSupportedProtocol(String protocol, List<String> versions) {
            supportedProtocols.put(protocol, versions);
        }

        public void addFeature(String protocol, String feature) {
            protocolFeatures.computeIfAbsent(protocol, k -> new ArrayList<>()).add(feature);
        }

        public Map<String, List<String>> getSupportedProtocols() { return supportedProtocols; }
        public Map<String, List<String>> getProtocolFeatures() { return protocolFeatures; }
    }

    /**
     * Client statistics
     */
    public static class ClientStatistics {
        private int totalCount;
        private int producerCount;
        private int consumerCount;
        private int remotingCount;
        private int grpcCount;
        private int onlineCount;
        private int offlineCount;

        // Getters and setters
        public int getTotalCount() { return totalCount; }
        public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

        public int getProducerCount() { return producerCount; }
        public void setProducerCount(int producerCount) { this.producerCount = producerCount; }

        public int getConsumerCount() { return consumerCount; }
        public void setConsumerCount(int consumerCount) { this.consumerCount = consumerCount; }

        public int getRemotingCount() { return remotingCount; }
        public void setRemotingCount(int remotingCount) { this.remotingCount = remotingCount; }

        public int getGrpcCount() { return grpcCount; }
        public void setGrpcCount(int grpcCount) { this.grpcCount = grpcCount; }

        public int getOnlineCount() { return onlineCount; }
        public void setOnlineCount(int onlineCount) { this.onlineCount = onlineCount; }

        public int getOfflineCount() { return offlineCount; }
        public void setOfflineCount(int offlineCount) { this.offlineCount = offlineCount; }
    }
}
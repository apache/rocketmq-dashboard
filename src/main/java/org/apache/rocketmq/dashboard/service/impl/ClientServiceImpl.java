package org.apache.rocketmq.dashboard.service.impl;

import org.apache.rocketmq.dashboard.architecture.ClusterProvider;
import org.apache.rocketmq.dashboard.architecture.MetadataProvider;
import org.apache.rocketmq.dashboard.model.ClientInstance;
import org.apache.rocketmq.dashboard.model.ClusterCapability;
import org.apache.rocketmq.dashboard.service.ArchitectureBasedService;
import org.apache.rocketmq.dashboard.service.ClientService;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * Unified client service implementation
 * Provides consistent view of clients across Remoting and gRPC protocols
 */
@Service
public class ClientServiceImpl extends ArchitectureBasedService implements ClientService {

    @Resource
    private MetadataProvider metadataProvider;

    @Resource
    private ClusterProvider clusterProvider;

    @Override
    public List<ClientInstance> listClients() {
        try {
            if (supports("CLIENT_DISCOVERY")) {
                return metadataProvider.listClients();
            }
            handleUnsupportedOperation("Client discovery - not supported in current cluster");
            return List.of();
        } catch (Exception e) {
            handleUnsupportedOperation("List clients");
            return List.of();
        }
    }

    @Override
    public List<ClientInstance> listClientsByProtocol(String protocol) {
        try {
            if (supports("CLIENT_PROTOCOL_FILTER")) {
                return metadataProvider.listClientsByProtocol(protocol);
            }
            // Fallback: filter from all clients
            List<ClientInstance> allClients = metadataProvider.listClients();
            return allClients.stream()
                .filter(client -> protocol.equals(client.getProtocolType()))
                .toList();
        } catch (Exception e) {
            handleUnsupportedOperation("List clients by protocol");
            return List.of();
        }
    }

    @Override
    public List<ClientInstance> listClientsByType(String clientType) {
        try {
            return metadataProvider.listClientsByType(clientType);
        } catch (Exception e) {
            handleUnsupportedOperation("List clients by type");
            return List.of();
        }
    }

    @Override
    public List<ClientInstance> listClientsByCluster(String clusterName) {
        try {
            if (supports("CLIENT_CLUSTER_FILTER")) {
                return metadataProvider.listClientsByCluster(clusterName);
            }
            // Fallback: return all clients
            return metadataProvider.listClients();
        } catch (Exception e) {
            handleUnsupportedOperation("List clients by cluster");
            return List.of();
        }
    }

    @Override
    public ClientInstance getClient(String clientId) {
        try {
            if (clientId == null || clientId.trim().isEmpty()) {
                throw new IllegalArgumentException("Client ID cannot be empty");
            }
            return metadataProvider.getClient(clientId).orElse(null);
        } catch (Exception e) {
            handleUnsupportedOperation("Get client");
            return null;
        }
    }

    @Override
    public boolean killClient(String clientId, String reason) {
        try {
            if (clientId == null || clientId.trim().isEmpty()) {
                throw new IllegalArgumentException("Client ID cannot be empty");
            }
            if (supports("CLIENT_KILL")) {
                metadataProvider.killClient(clientId, reason);
                return true;
            }
            handleUnsupportedOperation("Kill client - not supported in current cluster");
            return false;
        } catch (Exception e) {
            handleUnsupportedOperation("Kill client");
            return false;
        }
    }

    @Override
    public boolean updateClientConfig(String clientId, String configKey, String configValue) {
        try {
            if (clientId == null || clientId.trim().isEmpty()) {
                throw new IllegalArgumentException("Client ID cannot be empty");
            }
            if (configKey == null || configKey.trim().isEmpty()) {
                throw new IllegalArgumentException("Config key cannot be empty");
            }
            if (supports("CLIENT_CONFIG_UPDATE")) {
                metadataProvider.updateClientConfig(clientId, configKey, configValue);
                return true;
            }
            handleUnsupportedOperation("Update client config - not supported in current cluster");
            return false;
        } catch (Exception e) {
            handleUnsupportedOperation("Update client config");
            return false;
        }
    }

    @Override
    public List<ClientInstance> getConnectedClients(String brokerAddress) {
        try {
            if (brokerAddress == null || brokerAddress.trim().isEmpty()) {
                throw new IllegalArgumentException("Broker address cannot be empty");
            }
            if (supports("CLIENT_BROKER_CONNECTION")) {
                return metadataProvider.getConnectedClients(brokerAddress);
            }
            handleUnsupportedOperation("Get connected clients - not supported in current cluster");
            return List.of();
        } catch (Exception e) {
            handleUnsupportedOperation("Get connected clients");
            return List.of();
        }
    }

    @Override
    public List<ClientInstance> getIdleClients(long idleTimeThreshold) {
        try {
            if (supports("CLIENT_IDLE_DETECTION")) {
                return metadataProvider.getIdleClients(idleTimeThreshold);
            }
            // Fallback: calculate from all clients
            List<ClientInstance> allClients = metadataProvider.listClients();
            return allClients.stream()
                .filter(client -> client.getLastHeartbeatTime() != null &&
                    (System.currentTimeMillis() - client.getLastHeartbeatTime().getTime()) > idleTimeThreshold)
                .toList();
        } catch (Exception e) {
            handleUnsupportedOperation("Get idle clients");
            return List.of();
        }
    }

    @Override
    public List<ClientInstance> getClientsWithIssue(String issueType) {
        try {
            if (supports("CLIENT_ISSUE_DETECTION")) {
                return metadataProvider.getClientsWithIssue(issueType);
            }
            handleUnsupportedOperation("Get clients with issue - not supported in current cluster");
            return List.of();
        } catch (Exception e) {
            handleUnsupportedOperation("Get clients with issue");
            return List.of();
        }
    }

    @Override
    public boolean diagnoseClient(String clientId) {
        try {
            if (clientId == null || clientId.trim().isEmpty()) {
                throw new IllegalArgumentException("Client ID cannot be empty");
            }
            if (supports("CLIENT_DIAGNOSIS")) {
                metadataProvider.diagnoseClient(clientId);
                return true;
            }
            handleUnsupportedOperation("Diagnose client - not supported in current cluster");
            return false;
        } catch (Exception e) {
            handleUnsupportedOperation("Diagnose client");
            return false;
        }
    }

    /**
     * Validate client ID format
     */
    private void validateClientId(String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Client ID cannot be empty");
        }
        if (clientId.length() > 255) {
            throw new IllegalArgumentException("Client ID too long");
        }
    }

    /**
     * Validate client configuration parameters
     */
    private void validateClientConfig(String configKey, String configValue) {
        if (configKey == null || configKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Config key cannot be empty");
        }
        if (configValue == null) {
            throw new IllegalArgumentException("Config value cannot be null");
        }
    }
}
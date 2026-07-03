package org.apache.rocketmq.dashboard.service;

import org.apache.rocketmq.dashboard.model.ClientInstance;

import java.util.List;

/**
 * Unified client management service interface
 * Provides consistent view across Remoting and gRPC protocol clients
 */
public interface ClientService {

    /**
     * List all clients
     */
    List<ClientInstance> listClients();

    /**
     * List clients by protocol type (Remoting, gRPC)
     */
    List<ClientInstance> listClientsByProtocol(String protocol);

    /**
     * List clients by client type (Producer, Consumer)
     */
    List<ClientInstance> listClientsByType(String clientType);

    /**
     * List clients by cluster
     */
    List<ClientInstance> listClientsByCluster(String clusterName);

    /**
     * Get client by ID
     */
    ClientInstance getClient(String clientId);

    /**
     * Kill client connection
     */
    boolean killClient(String clientId, String reason);

    /**
     * Update client configuration
     */
    boolean updateClientConfig(String clientId, String configKey, String configValue);

    /**
     * Get connected clients for a broker
     */
    List<ClientInstance> getConnectedClients(String brokerAddress);

    /**
     * Get idle clients based on threshold
     */
    List<ClientInstance> getIdleClients(long idleTimeThreshold);

    /**
     * Get clients with specific issues
     */
    List<ClientInstance> getClientsWithIssue(String issueType);

    /**
     * Diagnose client issues
     */
    boolean diagnoseClient(String clientId);
}
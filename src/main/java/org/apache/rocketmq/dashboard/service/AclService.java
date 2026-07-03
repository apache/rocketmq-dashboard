package org.apache.rocketmq.dashboard.service;

import org.apache.rocketmq.dashboard.model.ACLPolicy;
import org.apache.rocketmq.dashboard.model.ACLUser;

import java.util.List;

/**
 * Access Control List service interface
 * Supports both ACL 1.0 and ACL 2.0 with automatic detection and adaptation
 */
public interface AclService {

    /**
     * List all ACL users
     */
    List<ACLUser> listUsers();

    /**
     * List ACL policies for a specific user
     */
    List<ACLPolicy> listPolicies(String username);

    /**
     * Create new ACL user
     */
    boolean createUser(ACLUser user);

    /**
     * Update existing ACL user
     */
    boolean updateUser(ACLUser user);

    /**
     * Delete ACL user
     */
    boolean deleteUser(String username);

    /**
     * Add ACL policy
     */
    boolean addPolicy(ACLPolicy policy);

    /**
     * Remove ACL policy
     */
    boolean removePolicy(String username, String policyId);

    /**
     * Get ACL user by username
     */
    ACLUser getUser(String username);

    /**
     * Check if user has permission for resource and action
     */
    boolean checkPermission(String username, String resource, String action);
}
package org.apache.rocketmq.dashboard.service.impl;

import org.apache.rocketmq.dashboard.architecture.MetadataProvider;
import org.apache.rocketmq.dashboard.model.ACLPolicy;
import org.apache.rocketmq.dashboard.model.ACLUser;
import org.apache.rocketmq.dashboard.model.ClusterCapability;
import org.apache.rocketmq.dashboard.service.ArchitectureBasedService;
import org.apache.rocketmq.dashboard.service.AclService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * ACL service implementation supporting both ACL 1.0 and 2.0
 * Provides unified access control management interface
 */
@Service
public class AclServiceImpl extends ArchitectureBasedService implements AclService {

    @Resource
    private MetadataProvider metadataProvider;

    @Override
    public List<ACLUser> listUsers() {
        try {
            return metadataProvider.listACLUsers();
        } catch (Exception e) {
            handleUnsupportedOperation("List ACL users");
            return List.of();
        }
    }

    @Override
    public List<ACLPolicy> listPolicies(String username) {
        try {
            return metadataProvider.listACLPolicies(username);
        } catch (Exception e) {
            handleUnsupportedOperation("List ACL policies");
            return List.of();
        }
    }

    @Override
    public boolean createUser(ACLUser user) {
        try {
            validateACLUser(user);
            metadataProvider.createACLUser(user);
            return true;
        } catch (Exception e) {
            handleUnsupportedOperation("Create ACL user");
            return false;
        }
    }

    @Override
    public boolean updateUser(ACLUser user) {
        try {
            validateACLUser(user);
            metadataProvider.updateACLUser(user);
            return true;
        } catch (Exception e) {
            handleUnsupportedOperation("Update ACL user");
            return false;
        }
    }

    @Override
    public boolean deleteUser(String username) {
        try {
            if (username == null || username.trim().isEmpty()) {
                throw new IllegalArgumentException("Username cannot be empty");
            }
            metadataProvider.deleteACLUser(username);
            return true;
        } catch (Exception e) {
            handleUnsupportedOperation("Delete ACL user");
            return false;
        }
    }

    @Override
    public boolean addPolicy(ACLPolicy policy) {
        try {
            validateACLPolicy(policy);
            metadataProvider.addACLPolicy(policy);
            return true;
        } catch (Exception e) {
            handleUnsupportedOperation("Add ACL policy");
            return false;
        }
    }

    @Override
    public boolean removePolicy(String username, String policyId) {
        try {
            if (username == null || username.trim().isEmpty()) {
                throw new IllegalArgumentException("Username cannot be empty");
            }
            if (policyId == null || policyId.trim().isEmpty()) {
                throw new IllegalArgumentException("Policy ID cannot be empty");
            }
            metadataProvider.removeACLPolicy(username, policyId);
            return true;
        } catch (Exception e) {
            handleUnsupportedOperation("Remove ACL policy");
            return false;
        }
    }

    @Override
    public ACLUser getUser(String username) {
        try {
            if (username == null || username.trim().isEmpty()) {
                throw new IllegalArgumentException("Username cannot be empty");
            }
            return metadataProvider.getACLUser(username).orElse(null);
        } catch (Exception e) {
            handleUnsupportedOperation("Get ACL user");
            return null;
        }
    }

    @Override
    public boolean checkPermission(String username, String resource, String action) {
        try {
            if (username == null || username.trim().isEmpty() ||
                resource == null || resource.trim().isEmpty() ||
                action == null || action.trim().isEmpty()) {
                throw new IllegalArgumentException("Username, resource and action cannot be empty");
            }
            return metadataProvider.checkACLPermission(username, resource, action);
        } catch (Exception e) {
            handleUnsupportedOperation("Check ACL permission");
            return false;
        }
    }

    /**
     * Validate ACL user object
     */
    private void validateACLUser(ACLUser user) {
        if (user == null) {
            throw new IllegalArgumentException("ACL user cannot be null");
        }
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }
        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be empty");
        }
    }

    /**
     * Validate ACL policy object
     */
    private void validateACLPolicy(ACLPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("ACL policy cannot be null");
        }
        if (policy.getUsers() == null || policy.getUsers().isEmpty()) {
            throw new IllegalArgumentException("Policy users cannot be empty");
        }
        if (policy.getResources() == null || policy.getResources().isEmpty()) {
            throw new IllegalArgumentException("Policy resources cannot be empty");
        }
        if (policy.getActions() == null || policy.getActions().isEmpty()) {
            throw new IllegalArgumentException("Policy actions cannot be empty");
        }
    }
}
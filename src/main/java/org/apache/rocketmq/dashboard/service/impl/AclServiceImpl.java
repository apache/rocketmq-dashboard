package org.apache.rocketmq.dashboard.service.impl;

import org.apache.rocketmq.dashboard.architecture.ClusterProvider;
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

    @Resource
    private ClusterProvider clusterProvider;

    @Override
    public List<ACLUser> listUsers() {
        try {
            if (supports("ACL_2_0")) {
                return metadataProvider.listACLUsers();
            } else if (supports("ACL_1_0")) {
                return metadataProvider.listACLUsersV1();
            }
            handleUnsupportedOperation("ACL user listing - ACL not supported in current cluster");
            return List.of();
        } catch (Exception e) {
            handleUnsupportedOperation("List ACL users");
            return List.of();
        }
    }

    @Override
    public List<ACLPolicy> listPolicies(String username) {
        try {
            if (supports("ACL_2_0")) {
                return metadataProvider.listACLPolicies(username);
            } else if (supports("ACL_1_0")) {
                return metadataProvider.listACLPoliciesV1(username);
            }
            handleUnsupportedOperation("ACL policy listing - ACL not supported in current cluster");
            return List.of();
        } catch (Exception e) {
            handleUnsupportedOperation("List ACL policies");
            return List.of();
        }
    }

    @Override
    public boolean createUser(ACLUser user) {
        try {
            validateACLUser(user);

            if (supports("ACL_2_0")) {
                metadataProvider.createACLUser(user);
                return true;
            } else if (supports("ACL_1_0")) {
                metadataProvider.createACLUserV1(user);
                return true;
            }
            handleUnsupportedOperation("ACL user creation - ACL not supported in current cluster");
            return false;
        } catch (Exception e) {
            handleUnsupportedOperation("Create ACL user");
            return false;
        }
    }

    @Override
    public boolean updateUser(ACLUser user) {
        try {
            validateACLUser(user);

            if (supports("ACL_2_0")) {
                metadataProvider.updateACLUser(user);
                return true;
            } else if (supports("ACL_1_0")) {
                metadataProvider.updateACLUserV1(user);
                return true;
            }
            handleUnsupportedOperation("ACL user update - ACL not supported in current cluster");
            return false;
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

            if (supports("ACL_2_0")) {
                metadataProvider.deleteACLUser(username);
                return true;
            } else if (supports("ACL_1_0")) {
                metadataProvider.deleteACLUserV1(username);
                return true;
            }
            handleUnsupportedOperation("ACL user deletion - ACL not supported in current cluster");
            return false;
        } catch (Exception e) {
            handleUnsupportedOperation("Delete ACL user");
            return false;
        }
    }

    @Override
    public boolean addPolicy(ACLPolicy policy) {
        try {
            validateACLPolicy(policy);

            if (supports("ACL_2_0")) {
                metadataProvider.addACLPolicy(policy);
                return true;
            } else if (supports("ACL_1_0")) {
                metadataProvider.addACLPolicyV1(policy);
                return true;
            }
            handleUnsupportedOperation("ACL policy addition - ACL not supported in current cluster");
            return false;
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

            if (supports("ACL_2_0")) {
                metadataProvider.removeACLPolicy(username, policyId);
                return true;
            } else if (supports("ACL_1_0")) {
                metadataProvider.removeACLPolicyV1(username, policyId);
                return true;
            }
            handleUnsupportedOperation("ACL policy removal - ACL not supported in current cluster");
            return false;
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

            if (supports("ACL_2_0")) {
                return metadataProvider.getACLUser(username).orElse(null);
            } else if (supports("ACL_1_0")) {
                return metadataProvider.getACLUserV1(username).orElse(null);
            }
            handleUnsupportedOperation("ACL user retrieval - ACL not supported in current cluster");
            return null;
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

            if (supports("ACL_2_0")) {
                return metadataProvider.checkACLPermission(username, resource, action);
            } else if (supports("ACL_1_0")) {
                return metadataProvider.checkACLPermissionV1(username, resource, action);
            }
            // If ACL is not supported, default to allowed (backward compatibility)
            return true;
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
        if (policy.getUsername() == null || policy.getUsername().trim().isEmpty()) {
            throw new IllegalArgumentException("Policy username cannot be empty");
        }
        if (policy.getResource() == null || policy.getResource().trim().isEmpty()) {
            throw new IllegalArgumentException("Policy resource cannot be empty");
        }
        if (policy.getAction() == null || policy.getAction().trim().isEmpty()) {
            throw new IllegalArgumentException("Policy action cannot be empty");
        }
    }
}
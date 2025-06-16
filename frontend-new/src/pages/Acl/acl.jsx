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

import React, { useState, useEffect } from 'react';
import {
    Table,
    Button,
    Input,
    Tabs,
    Modal,
    Form,
    message,
    Space,
    Tag,
    Popconfirm,
    Select
} from 'antd';
import {
    EditOutlined,
    DeleteOutlined,
    EyeOutlined,
    EyeInvisibleOutlined
} from '@ant-design/icons';
import {remoteApi} from "../../api/remoteApi/remoteApi";
import ResourceInput from '../../components/acl/ResourceInput';
import SubjectInput from "../../components/acl/SubjectInput";
import {useLanguage} from "../../i18n/LanguageContext";
const { TabPane } = Tabs;
const { Search } = Input;

const Acl = () => {
    const [activeTab, setActiveTab] = useState('users');
    const [userListData, setUserListData] = useState([]);
    const [aclListData, setAclListData] = useState([]);
    const [loading, setLoading] = useState(false);
    const [searchText, setSearchText] = useState('');

    const [isUserModalVisible, setIsUserModalVisible] = useState(false);
    const [currentUser, setCurrentUser] = useState(null);
    const [userForm] = Form.useForm();
    const [showPassword, setShowPassword] = useState(false);

    const [isAclModalVisible, setIsAclModalVisible] = useState(false);
    const [currentAcl, setCurrentAcl] = useState(null);
    const [aclForm] = Form.useForm();
    const [messageApi, msgContextHolder] = message.useMessage();
    const [isUpdate, setIsUpdate] = useState(false);
    const [ips, setIps] = useState([]);
    const {t} = useLanguage();
    // 校验IP地址的正则表达式
    const ipRegex =
        /^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$|^((?:[0-9A-Fa-f]{1,4}:){7}[0-9A-Fa-f]{1,4}|(?:[0-9A-Fa-f]{1,4}:){6}[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4}){0,1}|(?:[0-9A-Fa-f]{1,4}:){5}[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4}){0,2}|(?:[0-9A-Fa-f]{1,4}:){4}[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4}){0,3}|(?:[0-9A-Fa-f]{1,4}:){3}[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4}){0,4}|(?:[0-9A-Fa-f]{1,4}:){2}[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4}){0,5}|(?:[0-9A-Fa-f]{1,4}:){1}[0-9A-Fa-f]{1,4}(?::[0-9A-Fa-f]{1,4}){0,6}|(?::(?::[0-9A-Fa-f]{1,4}){1,7}|::))(\/(?:12[0-7]|1[0-1][0-9]|[1-9]?[0-9]))?$/;
    // 支持 IPv4 和 IPv6，包括 CIDR 表示法

    const handleIpChange = value => {
        // 过滤掉重复的IP地址
        const uniqueIps = Array.from(new Set(value));
        setIps(uniqueIps);
    };

    const handleIpDeselect = value => {
        // 移除被取消选择的IP
        setIps(ips.filter(ip => ip !== value));
    };

    const validateIp = (rule, value) => {
        if (!value || value.length === 0) {
            return Promise.resolve(); // Allow empty
        }
        const invalidIps = value.filter(ip => !ipRegex.test(ip));
        if (invalidIps.length > 0) {
            return Promise.reject(t.INVALID_IP_ADDRESSES +"ips:" + invalidIps.join(', '));
        }
        return Promise.resolve();
    };

// --- Data Loading Functions ---
    const fetchUsers = async () => {
        setLoading(true);
        try {
            const result = await remoteApi.listUsers();
            if (result && result.status === 0 && result.data) {
                const formattedUsers = result.data.map(user => ({
                    ...user,
                    key: user.username, // Table needs key
                    userStatus: user.userStatus === 'enable' ? t.ENABLED : t.DISABLED // Format status
                }));
                setUserListData(formattedUsers);
            } else {
                messageApi.error(t.GET_USERS_FAILED+result?.errMsg);
            }
        } catch (error) {
            console.error("Failed to fetch users:", error);
            messageApi.error(t.GET_USERS_EXCEPTION);
        } finally {
            setLoading(false);
        }
    };

    const fetchAcls = async (value) => {
        setLoading(true);
        try {
            const result = await remoteApi.listAcls(null, value);
            if (result && result.status === 0) {
                const formattedAcls = [];

                if (result && result.data && Array.isArray(result.data)) {
                    result.data.forEach((acl, aclIndex) => {
                        const subject = acl.subject;

                        if (acl.policies && Array.isArray(acl.policies)) {
                            acl.policies.forEach((policy, policyIndex) => {
                                const policyType = policy.policyType;

                                if (policy.entries && Array.isArray(policy.entries)) {
                                    policy.entries.forEach((entry, entryIndex) => {
                                        const resources = Array.isArray(entry.resource) ? entry.resource : (entry.resource ? [entry.resource] : []);

                                        resources.forEach((singleResource, resourceIndex) => {
                                            console.log(singleResource)
                                            formattedAcls.push({
                                                key: `acl-${aclIndex}-policy-${policyIndex}-entry-${entryIndex}-resource-${singleResource}`,
                                                subject: subject,
                                                policyType: policyType,
                                                resource: singleResource || t.N_A,
                                                actions: (entry.actions && Array.isArray(entry.actions)) ? entry.actions.join(', ') : '',
                                                sourceIps: (entry.sourceIps && Array.isArray(entry.sourceIps)) ? entry.sourceIps.join(', ') : t.N_A,
                                                decision: entry.decision || t.N_A
                                            });
                                        });
                                    });
                                }
                            });
                        }
                    });
                } else {
                    console.warn(t.INVALID_OR_EMPTY_ACL_DATA);
                }
                setAclListData(formattedAcls);
            } else {
                messageApi.error(t.GET_ACLS_FAILED + result?.errMsg);
            }
        } catch (error) {
            console.error("Failed to fetch ACLs:", error);
            messageApi.error(t.GET_ACLS_EXCEPTION);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        if (activeTab === 'users') {
            fetchUsers();
        } else {
            fetchAcls();
        }
    }, [activeTab]);

// --- User Management Logic ---

    const handleAddUser = () => {
        setCurrentUser(null);
        userForm.resetFields();
        setShowPassword(false);
        setIsUserModalVisible(true);
    };

    const handleEditUser = (record) => {
        setCurrentUser(record);
        userForm.setFieldsValue({
            username: record.username,
            password: record.password,
            userType: record.userType,
            userStatus: record.userStatus === t.ENABLED ? 'enable' : 'disable'
        });
        setShowPassword(false);
        setIsUserModalVisible(true);
    };

    const handleDeleteUser = async (username) => {
        setLoading(true);
        try {
            const result = await remoteApi.deleteUser(null, username);
            if (result.status === 0) {
                messageApi.success(t.USER_DELETE_SUCCESS);
                fetchUsers();
            } else {
                messageApi.error(t.USER_DELETE_FAILED + result.errMsg);
            }
        } catch (error) {
            console.error("Failed to delete user:", error);
            messageApi.error(t.USER_DELETE_EXCEPTION);
        } finally {
            setLoading(false);
        }
    };

    const handleUserModalOk = async () => {
        try {
            const values = await userForm.validateFields();
            setLoading(true);
            let result;

            const userInfoParam = {
                username: values.username,
                password: values.password,
                userType: values.userType,
                userStatus: values.userStatus,
            };

            if (currentUser) {
                result = await remoteApi.updateUser(null, userInfoParam);
                if (result.status === 0) {
                    messageApi.success(t.USER_UPDATE_SUCCESS);
                } else {
                    messageApi.error(result.errMsg);
                }
            } else {
                result = await remoteApi.createUser(null, userInfoParam);
                if (result.status === 0) {
                    messageApi.success(t.USER_CREATE_SUCCESS);
                } else {
                    messageApi.error(result.errMsg);
                }
            }
            setIsUserModalVisible(false);
            fetchUsers();
        } catch (error) {
            console.error("Failed to save user:", error);
            messageApi.error(t.SAVE_USER_FAILED);
        } finally {
            setLoading(false);
        }
    };

// --- ACL Permission Management Logic ---
    const handleAddAcl = () => {
        setCurrentAcl(null);
        setIsUpdate(false)
        aclForm.resetFields();
        setIsAclModalVisible(true);
    };

    const handleEditAcl = (record) => {
        setCurrentAcl(record);
        setIsUpdate(true);
        aclForm.setFieldsValue({
            subject: record.subject,
            policyType: record.policyType,
            resource: record.resource,
            actions: record.actions ? record.actions.split(', ') : [],
            sourceIps: record.sourceIps ? record.sourceIps.split(', ') : [],
            decision: record.decision
        });
        setIsAclModalVisible(true);
    };

    const handleDeleteAcl = async (subject, resource) => {
        setLoading(true);
        try {
            const result = await remoteApi.deleteAcl(null, subject, resource);
            if (result.status === 0) {
                messageApi.success(t.ACL_DELETE_SUCCESS);
                fetchAcls();
            } else {
                messageApi.error(t.ACL_DELETE_FAILED+result.errMsg);
            }
        } catch (error) {
            console.error("Failed to delete ACL:", error);
            messageApi.error(t.ACL_DELETE_EXCEPTION);
        } finally {
            setLoading(false);
        }
    };

    const handleAclModalOk = async () => {
        try {
            const values = await aclForm.validateFields();
            setLoading(true);
            let result;

            const policiesParam = [
                {
                    policyType: values.policyType,
                    entries: [
                        {
                            resource: isUpdate ? [values.resource] : values.resource,
                            actions: values.actions,
                            sourceIps: values.sourceIps,
                            decision: values.decision
                        }
                    ]
                }
            ];

            if (isUpdate) { // This condition seems reversed for update/create based on the current logic.
                result = await remoteApi.updateAcl(null, values.subject, policiesParam);
                if (result.status === 0) {
                    messageApi.success(t.ACL_UPDATE_SUCCESS);
                    setIsAclModalVisible(false);
                    fetchAcls();
                } else {
                    messageApi.error(t.ACL_UPDATE_FAILED+result.errMsg);
                }
                setIsUpdate(false)
            } else {
                result = await remoteApi.createAcl(null, values.subject, policiesParam);
                console.log(result)
                if (result.status === 0) {
                    messageApi.success(t.ACL_CREATE_SUCCESS);
                    setIsAclModalVisible(false);
                    fetchAcls();
                } else {
                    messageApi.error(t.ACL_CREATE_FAILED+result.errMsg);
                }
            }

        } catch (error) {
            console.error("Failed to save ACL:", error);
            messageApi.error(t.SAVE_ACL_FAILED);
        } finally {
            setLoading(false);
        }
    };

// --- Search Functionality ---

    const handleSearch = (value) => {
        if (activeTab === 'users') {
            const filteredData = userListData.filter(item =>
                Object.values(item).some(val =>
                    String(val).toLowerCase().includes(value.toLowerCase())
                )
            );
            if (value === '') {
                fetchUsers();
            } else {
                setUserListData(filteredData);
            }
        } else {
            fetchAcls(value);
        }
    };


    // --- User Table Column Definitions ---
    const userColumns = [
        {
            title: t.USERNAME,
            dataIndex: 'username',
            key: 'username',
        },
        {
            title: t.PASSWORD,
            dataIndex: 'password',
            key: 'password',
            render: (text) => (
                <span>
                {showPassword ? text : '********'}
                    <Button
                        type="link"
                        icon={showPassword ? <EyeInvisibleOutlined /> : <EyeOutlined />}
                        onClick={() => setShowPassword(!showPassword)}
                        style={{ marginLeft: 8 }}
                    >
                    {showPassword ? t.HIDE : t.VIEW}
                </Button>
            </span>
            ),
        },
        {
            title: t.USER_TYPE,
            dataIndex: 'userType',
            key: 'userType',
        },
        {
            title: t.USER_STATUS,
            dataIndex: 'userStatus',
            key: 'userStatus',
            render: (status) => (
                <Tag color={status=== 'enable' ? 'red' : 'green'}>{status}</Tag>
            ),
        },
        {
            title: t.OPERATION,
            key: 'action',
            render: (_, record) => (
                <Space size="middle">
                    <Button icon={<EditOutlined />} onClick={() => handleEditUser(record)}>{t.MODIFY}</Button>
                    <Popconfirm
                        title={t.CONFIRM_DELETE_USER}
                        onConfirm={() => handleDeleteUser(record.username)}
                        okText={t.YES}
                        cancelText={t.NO}
                    >
                        <Button icon={<DeleteOutlined />} danger>{t.DELETE}</Button>
                    </Popconfirm>
                </Space>
            ),
        },
    ];

// --- ACL Permission Table Column Definitions ---
    const aclColumns = [
        {
            title: t.USERNAME_SUBJECT,
            dataIndex: 'subject',
            key: 'subject',
        },
        {
            title: t.POLICY_TYPE,
            dataIndex: 'policyType',
            key: 'policyType',
        },
        {
            title: t.RESOURCE_NAME,
            dataIndex: 'resource',
            key: 'resource',
        },
        {
            title: t.OPERATION_TYPE,
            dataIndex: 'actions',
            key: 'actions',
            render: (text) => text ? text.split(', ').map((action, index) => (
                <Tag key={index} color="blue">{action}</Tag>
            )) : null,
        },
        {
            title: t.SOURCE_IP,
            dataIndex: 'sourceIps',
            key: 'sourceIps',
        },
        {
            title: t.DECISION,
            dataIndex: 'decision',
            key: 'decision',
            render: (text) => (
                <Tag color={text === 'Allow' ? 'green' : 'red'}>{text}</Tag>
            ),
        },
        {
            title: t.OPERATION,
            key: 'action',
            render: (_, record) => (
                <Space size="middle">
                    <Button icon={<EditOutlined />} onClick={() => handleEditAcl(record)}>{t.MODIFY}</Button>
                    <Popconfirm
                        title={t.CONFIRM_DELETE_ACL}
                        onConfirm={() => handleDeleteAcl(record.subject, record.resource)}
                        okText={t.YES}
                        cancelText={t.NO}
                    >
                        <Button icon={<DeleteOutlined />} danger>{t.DELETE}</Button>
                    </Popconfirm>
                </Space>
            ),
        },
    ];

return (
    <>
        {msgContextHolder}
        <div style={{ padding: 24 }}>
            <h2>{t.ACL_MANAGEMENT}</h2>
            <Tabs activeKey={activeTab} onChange={setActiveTab}>
                <TabPane tab={t.ACL_USERS} key="users" />
                <TabPane tab={t.ACL_PERMISSIONS} key="acls" />
            </Tabs>

            <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
                <Button type="primary" onClick={activeTab === 'users' ? handleAddUser : handleAddAcl}>
                    {activeTab === 'users' ? t.ADD_USER : t.ADD_ACL_PERMISSION}
                </Button>
                <Search
                    placeholder={t.SEARCH_PLACEHOLDER}
                    allowClear
                    onSearch={handleSearch}
                    style={{ width: 300 }}
                />
            </div>

            {activeTab === 'users' && (
                <Table
                    columns={userColumns}
                    dataSource={userListData}
                    loading={loading}
                    pagination={{ pageSize: 10 }}
                    rowKey="username"
                />
            )}

            {activeTab === 'acls' && (
                <Table
                    columns={aclColumns}
                    dataSource={aclListData}
                    loading={loading}
                    pagination={{ pageSize: 10 }}
                    rowKey="key"
                />
            )}

            {/* User Management Modal */}
            <Modal
                title={currentUser ? t.EDIT_USER : t.ADD_USER}
                visible={isUserModalVisible}
                onOk={handleUserModalOk}
                onCancel={() => setIsUserModalVisible(false)}
                confirmLoading={loading}
                footer={[
                    <Button key="cancel" onClick={() => setIsUserModalVisible(false)}>
                        {t.CANCEL}
                    </Button>,
                    <Button key="submit" type="primary" onClick={handleUserModalOk} loading={loading}>
                        {t.CONFIRM}
                    </Button>,
                ]}
            >
                <Form
                    form={userForm}
                    layout="vertical"
                    name="user_form"
                    initialValues={{ userStatus: 'enable' }}
                >
                    <Form.Item
                        name="username"
                        label={t.USERNAME}
                        rules={[{ required: true, message: t.PLEASE_ENTER_USERNAME }]}
                    >
                        <Input disabled={!!currentUser} />
                    </Form.Item>
                    <Form.Item
                        name="password"
                        label={t.PASSWORD}
                        rules={[{ required: !currentUser, message: t.PLEASE_ENTER_PASSWORD }]}
                    >
                        <Input.Password
                            placeholder={t.PASSWORD}
                            iconRender={visible => (visible ? <EyeOutlined /> : <EyeInvisibleOutlined />)}
                        />
                    </Form.Item>
                    <Form.Item
                        name="userType"
                        label={t.USER_TYPE}
                        rules={[{ required: true, message: t.PLEASE_SELECT_USER_TYPE }]}
                    >
                        <Select mode="single" placeholder="Super, Normal" style={{ width: '100%' }}>
                            <Select.Option value="Super">Super</Select.Option>
                            <Select.Option value="Normal">Normal</Select.Option>
                        </Select>
                    </Form.Item>
                    <Form.Item
                        name="userStatus"
                        label={t.USER_STATUS}
                        rules={[{ required: true, message: t.PLEASE_SELECT_USER_STATUS }]}
                    >
                        <Select mode="single" placeholder="enable, disable" style={{ width: '100%' }}>
                            <Select.Option value="enable">enable</Select.Option>
                            <Select.Option value="disable">disable</Select.Option>
                        </Select>
                    </Form.Item>
                </Form>
            </Modal>

            {/* ACL Permission Management Modal */}
            <Modal
                title={currentAcl ? t.EDIT_ACL_PERMISSION : t.ADD_ACL_PERMISSION}
                visible={isAclModalVisible}
                onOk={handleAclModalOk}
                onCancel={() => setIsAclModalVisible(false)}
                confirmLoading={loading}
            >
                <Form
                    form={aclForm}
                    layout="vertical"
                    name="acl_form"
                >
                    <Form.Item
                        name="subject"
                        label={t.SUBJECT_LABEL}
                        rules={[{ required: true, message: t.PLEASE_ENTER_SUBJECT }]}
                    >
                        <SubjectInput disabled={!!currentAcl} />
                    </Form.Item>

                    <Form.Item
                        name="policyType"
                        label={t.POLICY_TYPE}
                        rules={[{ required: true, message: t.PLEASE_ENTER_POLICY_TYPE }]}
                    >
                        <Select mode="single" disabled={isUpdate} placeholder="policyType" style={{ width: '100%' }}>
                            <Select.Option value="Custom">Custom</Select.Option>
                            <Select.Option value="Default">Default</Select.Option>
                        </Select>
                    </Form.Item>

                    <Form.Item
                        name="resource"
                        label={t.RESOURCE}
                        rules={[{ required: true, message: t.PLEASE_ADD_RESOURCE }]}
                    >
                        {isUpdate ? (
                            <Input disabled={isUpdate} />
                        ) : (
                            <ResourceInput />
                        )}
                    </Form.Item>

                    <Form.Item
                        name="actions"
                        label={t.OPERATION_TYPE}
                    >
                        <Select mode="multiple" placeholder="action" style={{ width: '100%' }}>
                            <Select.Option value="All">All</Select.Option>
                            <Select.Option value="Pub">Pub</Select.Option>
                            <Select.Option value="Sub">Sub</Select.Option>
                            <Select.Option value="Create">Create</Select.Option>
                            <Select.Option value="Update">Update</Select.Option>
                            <Select.Option value="Delete">Delete</Select.Option>
                            <Select.Option value="Get">Get</Select.Option>
                            <Select.Option value="List">List</Select.Option>
                        </Select>
                    </Form.Item>
                    <Form.Item
                        name="sourceIps"
                        label={t.SOURCE_IP}
                        rules={[
                            {
                                validator: validateIp,
                            },
                        ]}
                    >
                        <Select
                            mode="tags"
                            style={{ width: '100%' }}
                            placeholder={t.ENTER_IP_HINT}
                            onChange={handleIpChange}
                            onDeselect={handleIpDeselect}
                            value={ips}
                            tokenSeparators={[',', ' ']}
                        >
                            <Select.Option value="192.168.1.1">192.168.1.1</Select.Option>
                            <Select.Option value="0.0.0.0">0.0.0.0</Select.Option>
                            <Select.Option value="127.0.0.1">127.0.0.1</Select.Option>
                        </Select>
                    </Form.Item>
                    <Form.Item
                        name="decision"
                        label={t.DECISION}
                        rules={[{ required: true, message: t.PLEASE_ENTER_DECISION }]}
                    >
                        <Select mode="single" placeholder="Allow, Deny" style={{ width: '100%' }}>
                            <Select.Option value="Allow">Allow</Select.Option>
                            <Select.Option value="Deny">Deny</Select.Option>
                        </Select>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    </>
);}

export default Acl;

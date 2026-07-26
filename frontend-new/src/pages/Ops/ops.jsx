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

import React, {useEffect, useState} from 'react';
import {Button, Input, message, Select, Space, Switch, Typography, Tag, Alert, Divider} from 'antd';
import {SwapOutlined, CheckCircleOutlined, ExclamationCircleOutlined} from '@ant-design/icons';
import {remoteApi} from '../../api/remoteApi/remoteApi';
import {useClusterCapabilities} from '../../store/context/ClusterCapabilitiesContext';
import {isWriteOperationEnabled} from '../../constants/roles';

const {Title, Text} = Typography;
const {Option} = Select;

// Architecture type display labels
const ARCH_LABELS = {
    'V4_NAMESRV': 'V4 — NameServer 直连',
    'V5_PROXY_LOCAL': 'V5 — Proxy 本地模式',
    'V5_PROXY_CLUSTER': 'V5 — Proxy 集群模式'
};

const Ops = () => {
    const [namesrvAddrList, setNamesrvAddrList] = useState([]);
    const [selectedNamesrv, setSelectedNamesrv] = useState('');
    const [newNamesrvAddr, setNewNamesrvAddr] = useState('');
    const [useVIPChannel, setUseVIPChannel] = useState(false);
    const [useTLS, setUseTLS] = useState(false);
    const [writeOperationEnabled, setWriteOperationEnabled] = useState(true); // Default to true
    const [messageApi, msgContextHolder] = message.useMessage();

    // Architecture switch state
    const {capabilities, switchArchitecture, refreshCapabilities} = useClusterCapabilities();
    const [archTypes, setArchTypes] = useState(null);
    const [selectedArchType, setSelectedArchType] = useState('');
    const [proxyAddr, setProxyAddr] = useState('');
    const [switching, setSwitching] = useState(false);
    useEffect(() => {
        const fetchOpsData = async () => {
            setWriteOperationEnabled(isWriteOperationEnabled());

            const resp = await remoteApi.queryOpsHomePage();
            if (resp.status === 0) {
                setNamesrvAddrList(resp.data.namesvrAddrList);
                setUseVIPChannel(resp.data.useVIPChannel);
                setUseTLS(resp.data.useTLS);
                setSelectedNamesrv(resp.data.currentNamesrv);
            } else {
                messageApi.error(resp.errMsg);
            }
        };
        fetchOpsData();

        // Fetch architecture types for the switcher
        const fetchArchTypes = async () => {
            const types = await remoteApi.getArchitectureTypes();
            if (types) {
                setArchTypes(types);
            }
        };
        fetchArchTypes();
    }, []);

    // Sync selected arch type with current capabilities
    useEffect(() => {
        if (capabilities.accessType) {
            setSelectedArchType(capabilities.accessType);
        }
    }, [capabilities.accessType]);

    // Handle architecture switch
    const handleSwitchArchitecture = async () => {
        if (!selectedArchType) {
            messageApi.warning('请选择目标架构类型');
            return;
        }
        if (selectedArchType === capabilities.accessType) {
            messageApi.info('当前已是该架构类型');
            return;
        }
        // V5 requires proxy addresses
        const isV5 = selectedArchType.startsWith('V5');
        if (isV5 && !proxyAddr.trim()) {
            messageApi.warning('V5 架构需要填写 Proxy 地址');
            return;
        }

        setSwitching(true);
        try {
            const request = { accessType: selectedArchType };
            if (isV5) {
                request.proxyAddresses = proxyAddr.split(',').map(s => s.trim()).filter(Boolean);
                request.nameSrvAddress = selectedNamesrv || '';
            }
            const result = await switchArchitecture(request);
            if (result && result.success) {
                messageApi.success(`架构切换成功: ${ARCH_LABELS[selectedArchType] || selectedArchType}`);
                await refreshCapabilities();
            } else {
                messageApi.error(`架构切换失败: ${result?.error || '未知错误'}`);
            }
        } catch (err) {
            messageApi.error(`架构切换异常: ${err.message}`);
        } finally {
            setSwitching(false);
        }
    };

    useEffect(() => {
        setWriteOperationEnabled(isWriteOperationEnabled());
    }, []);

    const handleUpdateNameSvrAddr = async () => {
        if (!selectedNamesrv) {
            messageApi.warning('请选择一个 NameServer 地址');
            return;
        }
        const resp = await remoteApi.updateNameSvrAddr(selectedNamesrv);
        if (resp.status === 0) {
            messageApi.info('UPDATE SUCCESS');
        } else {
            messageApi.error(resp.errMsg);
        }
    };

    const handleAddNameSvrAddr = async () => {
        if (!newNamesrvAddr.trim()) {
            messageApi.warning('请输入新的 NameServer 地址');
            return;
        }
        const resp = await remoteApi.addNameSvrAddr(newNamesrvAddr.trim());
        if (resp.status === 0) {
            if (!namesrvAddrList.includes(newNamesrvAddr.trim())) {
                setNamesrvAddrList([...namesrvAddrList, newNamesrvAddr.trim()]);
            }
            setNewNamesrvAddr('');
            messageApi.info('ADD SUCCESS');
        } else {
            messageApi.error(resp.errMsg);
        }
    };

    const handleUpdateIsVIPChannel = async (checked) => {
        setUseVIPChannel(checked); // Optimistic update
        const resp = await remoteApi.updateIsVIPChannel(checked);
        if (resp.status === 0) {
            messageApi.info('UPDATE SUCCESS');
        } else {
            messageApi.error(resp.errMsg);
            setUseVIPChannel(!checked); // Revert on error
        }
    };

    const handleUpdateUseTLS = async (checked) => {
        setUseTLS(checked); // Optimistic update
        const resp = await remoteApi.updateUseTLS(checked);
        if (resp.status === 0) {
            messageApi.info('UPDATE SUCCESS');
        } else {
            messageApi.error(resp.errMsg);
            setUseTLS(!checked); // Revert on error
        }
    };

    return (
        <>
            {msgContextHolder}
            <div style={{padding: 24}}>
                {/* Architecture Switch Section */}
                <div style={{marginBottom: 24, padding: 20, border: '1px solid #d9d9d9', borderRadius: 8, background: '#fafafa'}}>
                    <Title level={4} style={{marginBottom: 12}}>
                        <SwapOutlined style={{marginRight: 8}} />
                        架构切换
                    </Title>
                    <div style={{marginBottom: 12}}>
                        <Text type="secondary">当前架构: </Text>
                        <Tag color={capabilities.isV5Architecture ? 'green' : 'blue'} style={{fontSize: 13}}>
                            {ARCH_LABELS[capabilities.accessType] || capabilities.accessType}
                        </Tag>
                        {capabilities.isV5Architecture && (
                            <Tag color="cyan" style={{marginLeft: 4}}>V5</Tag>
                        )}
                    </div>
                    <Space wrap align="start" style={{marginBottom: 12}}>
                        <Select
                            style={{minWidth: 280}}
                            value={selectedArchType}
                            onChange={setSelectedArchType}
                            placeholder="选择目标架构类型"
                        >
                            {archTypes && Object.entries(archTypes).map(([key, info]) => (
                                <Option key={key} value={key}>
                                    {ARCH_LABELS[key] || key}
                                    {info.isV5 ? ' (V5)' : ' (V4)'}
                                </Option>
                            ))}
                            {!archTypes && (
                                <>
                                    <Option value="V4_NAMESRV">V4 — NameServer 直连</Option>
                                    <Option value="V5_PROXY_LOCAL">V5 — Proxy 本地模式</Option>
                                    <Option value="V5_PROXY_CLUSTER">V5 — Proxy 集群模式</Option>
                                </>
                            )}
                        </Select>
                        {selectedArchType && selectedArchType.startsWith('V5') && (
                            <Input
                                style={{minWidth: 300}}
                                placeholder="Proxy 地址 (多个用逗号分隔, 如: 127.0.0.1:8080,127.0.0.1:8081)"
                                value={proxyAddr}
                                onChange={(e) => setProxyAddr(e.target.value)}
                            />
                        )}
                        {writeOperationEnabled && (
                            <Button
                                type="primary"
                                icon={<SwapOutlined />}
                                loading={switching}
                                onClick={handleSwitchArchitecture}
                                disabled={switching || selectedArchType === capabilities.accessType}
                            >
                                切换架构
                            </Button>
                        )}
                    </Space>
                    {capabilities.isV5Architecture && (
                        <Alert
                            type="success"
                            showIcon
                            icon={<CheckCircleOutlined />}
                            message="V5 架构已激活"
                            description="LiteTopic、Proxy 管理、路由事件等 V5 功能已可用。"
                            style={{marginTop: 8}}
                        />
                    )}
                    {!capabilities.isV5Architecture && (
                        <Alert
                            type="info"
                            showIcon
                            icon={<ExclamationCircleOutlined />}
                            message="当前为 V4 架构"
                            description="如需使用 LiteTopic、Proxy 等功能，请切换到 V5 架构并配置 Proxy 地址。"
                            style={{marginTop: 8}}
                        />
                    )}
                </div>

                <Divider style={{margin: '16px 0'}} />

                <div style={{marginBottom: 24}}>
                    <Title level={4}>NameServerAddressList</Title>
                    <Space wrap align="start">
                        <Select
                            style={{minWidth: 400, maxWidth: 500}}
                            value={selectedNamesrv}
                            onChange={setSelectedNamesrv}
                            disabled={!writeOperationEnabled}
                            placeholder="请选择 NameServer 地址"
                        >
                            {namesrvAddrList.map((addr) => (
                                <Option key={addr} value={addr}>
                                    {addr}
                                </Option>
                            ))}
                        </Select>

                        {writeOperationEnabled && (
                            <Button type="primary" onClick={handleUpdateNameSvrAddr}>
                                UPDATE
                            </Button>
                        )}

                        {writeOperationEnabled && (
                            <Input.Group compact style={{minWidth: 400}}>
                                <Input
                                    style={{width: 300}}
                                    placeholder="NamesrvAddr"
                                    value={newNamesrvAddr}
                                    onChange={(e) => setNewNamesrvAddr(e.target.value)}
                                />
                                <Button type="primary" onClick={handleAddNameSvrAddr}>
                                    ADD
                                </Button>
                            </Input.Group>
                        )}
                    </Space>
                </div>

                <div style={{marginBottom: 24}}>
                    <Title level={4}>IsUseVIPChannel</Title>
                    <Space align="center">
                        <Switch
                            checked={useVIPChannel}
                            onChange={handleUpdateIsVIPChannel}
                            disabled={!writeOperationEnabled}
                        />
                        {writeOperationEnabled && (
                            <Button type="primary" onClick={() => handleUpdateIsVIPChannel(useVIPChannel)}>
                                UPDATE
                            </Button>
                        )}
                    </Space>
                </div>

                <div style={{marginBottom: 24}}>
                    <Title level={4}>useTLS</Title>
                    <Space align="center">
                        <Switch
                            checked={useTLS}
                            onChange={handleUpdateUseTLS}
                            disabled={!writeOperationEnabled}
                        />
                        {writeOperationEnabled && (
                            <Button type="primary" onClick={() => handleUpdateUseTLS(useTLS)}>
                                UPDATE
                            </Button>
                        )}
                    </Space>
                </div>
            </div>
        </>

    );
};

export default Ops;

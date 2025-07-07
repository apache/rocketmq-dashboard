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
import {Button, Input, message, Select, Space, Switch, Typography} from 'antd';
import {remoteApi} from '../../api/remoteApi/remoteApi';

const {Title} = Typography;
const {Option} = Select;

const Ops = () => {
    const [namesrvAddrList, setNamesrvAddrList] = useState([]);
    const [selectedNamesrv, setSelectedNamesrv] = useState('');
    const [newNamesrvAddr, setNewNamesrvAddr] = useState('');
    const [useVIPChannel, setUseVIPChannel] = useState(false);
    const [useTLS, setUseTLS] = useState(false);
    const [writeOperationEnabled, setWriteOperationEnabled] = useState(true); // Default to true
    const [messageApi, msgContextHolder] = message.useMessage();
    useEffect(() => {
        const fetchOpsData = async () => {
            const userRole = sessionStorage.getItem("userrole");
            setWriteOperationEnabled(userRole === null || userRole === "1"); // Assuming "1" means write access

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
    }, []);

    useEffect(() => {
        const userPermission = localStorage.getItem('userrole');
        console.log(userPermission);
        if (userPermission == 2) {
            setWriteOperationEnabled(false);
        } else {
            setWriteOperationEnabled(true);
        }
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

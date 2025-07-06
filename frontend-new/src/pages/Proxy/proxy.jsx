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
import {Button, Card, Col, Input, Modal, notification, Row, Select, Spin} from 'antd';
import {useLanguage} from '../../i18n/LanguageContext';
import {remoteApi} from "../../api/remoteApi/remoteApi";


const {Option} = Select;

const ProxyManager = () => {
    const {t} = useLanguage();

    const [loading, setLoading] = useState(false);
    const [proxyAddrList, setProxyAddrList] = useState([]);
    const [selectedProxy, setSelectedProxy] = useState('');
    const [newProxyAddr, setNewProxyAddr] = useState('');
    const [allProxyConfig, setAllProxyConfig] = useState({});

    const [showModal, setShowModal] = useState(false); // 控制 Modal 弹窗显示
    const [writeOperationEnabled, setWriteOperationEnabled] = useState(true); // 写操作权限，默认 true
    const [notificationApi, notificationContextHolder] = notification.useNotification();

    useEffect(() => {
        const userPermission = localStorage.getItem('userrole');
        console.log(userPermission);
        if (userPermission == 2) {
            setWriteOperationEnabled(false);
        } else {
            setWriteOperationEnabled(true);
        }
    }, []);

    useEffect(() => {
        setLoading(true);
        remoteApi.queryProxyHomePage((resp) => {
            setLoading(false);
            if (resp.status === 0) {
                const {proxyAddrList, currentProxyAddr} = resp.data;
                setProxyAddrList(proxyAddrList || []);
                setSelectedProxy(currentProxyAddr || (proxyAddrList && proxyAddrList.length > 0 ? proxyAddrList[0] : ''));

                if (currentProxyAddr) {
                    localStorage.setItem('proxyAddr', currentProxyAddr);
                } else if (proxyAddrList && proxyAddrList.length > 0) {
                    localStorage.setItem('proxyAddr', proxyAddrList[0]);
                }

            } else {
                notificationApi.error({message: resp.errMsg || t.FETCH_PROXY_LIST_FAILED, duration: 2});
            }
        });
    }, [t]);

    const handleSelectChange = (value) => {
        setSelectedProxy(value);
        localStorage.setItem('proxyAddr', value);
    };


    const handleAddProxyAddr = () => {
        if (!newProxyAddr.trim()) {
            notificationApi.warning({
                message: t.INPUT_PROXY_ADDR_REQUIRED || "Please input a new proxy address.",
                duration: 2
            });
            return;
        }
        setLoading(true);
        remoteApi.addProxyAddr(newProxyAddr.trim(), (resp) => {
            setLoading(false);
            if (resp.status === 0) {
                if (!proxyAddrList.includes(newProxyAddr.trim())) {
                    setProxyAddrList(prevList => [...prevList, newProxyAddr.trim()]);
                }
                setNewProxyAddr('');
                notificationApi.info({message: t.SUCCESS || "SUCCESS", duration: 2});
            } else {
                notificationApi.error({message: resp.errMsg || t.ADD_PROXY_FAILED, duration: 2});
            }
        });
    };

    return (
        <Spin spinning={loading} tip={t.LOADING}>
            <div className="container-fluid" style={{padding: '24px'}} id="deployHistoryList">
                <Card
                    title={
                        <div style={{fontSize: '20px', fontWeight: 'bold'}}>
                            ProxyServerAddressList
                        </div>
                    }
                    bordered={false}
                >
                    <Row gutter={[16, 16]} align="middle">
                        <Col flex="auto" style={{minWidth: 300, maxWidth: 500}}>
                            <Select
                                style={{width: '100%'}}
                                value={selectedProxy}
                                onChange={handleSelectChange}
                                placeholder={t.SELECT}
                                showSearch
                                filterOption={(input, option) =>
                                    option.children.toLowerCase().indexOf(input.toLowerCase()) >= 0
                                }
                            >
                                {proxyAddrList.map(addr => (
                                    <Option key={addr} value={addr}>
                                        {addr}
                                    </Option>
                                ))}
                            </Select>
                        </Col>
                    </Row>

                    {writeOperationEnabled && (
                        <Row gutter={[16, 16]} align="middle" style={{marginTop: 16}}>
                            <Col>
                                <label htmlFor="newProxyAddrInput">ProxyAddr:</label>
                            </Col>
                            <Col>
                                <Input
                                    id="newProxyAddrInput"
                                    style={{width: 300}}
                                    value={newProxyAddr}
                                    onChange={(e) => setNewProxyAddr(e.target.value)}
                                    placeholder={t.INPUT_PROXY_ADDR}
                                />
                            </Col>
                            <Col>
                                <Button type="primary" onClick={handleAddProxyAddr}>
                                    {t.ADD}
                                </Button>
                            </Col>
                        </Row>
                    )}
                </Card>

                <Modal
                    open={showModal}
                    onCancel={() => setShowModal(false)}
                    title={`${t.PROXY_CONFIG} [${selectedProxy}]`}
                    footer={
                        <div style={{textAlign: 'center'}}>
                            <Button onClick={() => setShowModal(false)}>{t.CLOSE}</Button>
                        </div>
                    }
                    width={800}
                    bodyStyle={{maxHeight: '60vh', overflowY: 'auto'}}
                >
                    <table className="table table-bordered" style={{width: '100%'}}>
                        <tbody>
                        {Object.entries(allProxyConfig).length > 0 ? (
                            Object.entries(allProxyConfig).map(([key, value]) => (
                                <tr key={key}>
                                    <td style={{fontWeight: 500, width: '30%'}}>{key}</td>
                                    <td>{value}</td>
                                </tr>
                            ))
                        ) : (
                            <tr>
                                <td colSpan="2"
                                    style={{textAlign: 'center'}}>{t.NO_CONFIG_DATA || "No configuration data available."}</td>
                            </tr>
                        )}
                        </tbody>
                    </table>
                </Modal>
            </div>
        </Spin>
    );
};

export default ProxyManager;

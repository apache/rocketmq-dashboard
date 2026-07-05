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
import { Tag, Dropdown } from 'antd';
import { DownOutlined } from '@ant-design/icons';
import { useClusterCapabilities } from '../../store/context/ClusterCapabilitiesContext';
import { remoteApi } from '../../api/remoteApi/remoteApi';

function ClusterChip() {
    const { selectedCluster, selectCluster } = useClusterCapabilities();
    const [clusterList, setClusterList] = useState([]);

    useEffect(() => {
        remoteApi.getClusterList().then((res) => {
            if (res && res.data) {
                const clusterNames = res.data.clusterInfo
                    ? Object.keys(res.data.clusterInfo)
                    : (Array.isArray(res.data) ? res.data : []);
                setClusterList(clusterNames);
            }
        }).catch(() => {
            setClusterList([]);
        });
    }, []);

    const menuItems = clusterList.map((name) => ({
        key: name,
        label: name,
        onClick: () => selectCluster(name),
    }));

    return (
        <Dropdown menu={{ items: menuItems }} trigger={['click']}>
            <Tag
                color="blue"
                style={{ cursor: 'pointer', userSelect: 'none' }}
            >
                {selectedCluster || 'Select Cluster'} <DownOutlined style={{ fontSize: '10px' }} />
            </Tag>
        </Dropdown>
    );
}

export default ClusterChip;

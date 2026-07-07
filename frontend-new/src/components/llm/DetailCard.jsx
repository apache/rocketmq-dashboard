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

import React from 'react';

/**
 * DetailCard — 通用 key-value 详情卡片
 *
 * 支持两种数据格式:
 * 1. 对象格式: { key1: value1, key2: value2, ... }
 * 2. 数组格式: [{ key: "name", value: "xxx" }, ...]
 *
 * 自动识别常用字段名做中文标签映射
 */
const LABEL_MAP = {
    name: '名称',
    topicName: '主题名称',
    topic: '主题',
    groupName: '消费组名称',
    group: '消费组',
    type: '类型',
    topicType: '主题类型',
    status: '状态',
    state: '状态',
    perm: '权限',
    readQueueNums: '读队列数',
    writeQueueNums: '写队列数',
    brokerName: 'Broker 名称',
    brokerAddr: 'Broker 地址',
    cluster: '集群',
    count: '数量',
    total: '总计',
    createTime: '创建时间',
    createTimestamp: '创建时间',
    updateTime: '更新时间',
    consumeMode: '消费模式',
    consumeType: '消费类型',
    onlineClientCount: '在线客户端数',
    clientCount: '客户端数',
    subscribedTopics: '订阅主题',
    diff: '积压量',
    delay: '延迟',
    description: '描述',
    version: '版本',
    address: '地址',
    role: '角色',
};

function DetailCard({ data, title }) {
    if (!data) {
        return <div className="detail-card-empty">暂无详情数据</div>;
    }

    // Normalize to entries array
    let entries;
    if (Array.isArray(data)) {
        entries = data.map((item, i) => ({
            key: item.key || item.label || item.name || `字段${i + 1}`,
            value: item.value ?? item.val ?? '-',
        }));
    } else if (typeof data === 'object') {
        entries = Object.entries(data).map(([k, v]) => ({ key: k, value: v }));
    } else {
        return <div className="detail-card-raw">{String(data)}</div>;
    }

    // Filter out internal/empty fields
    entries = entries.filter(e => {
        const k = e.key.toLowerCase();
        return !k.startsWith('_') && e.value !== undefined && e.value !== null;
    });

    if (entries.length === 0) {
        return <div className="detail-card-empty">暂无详情数据</div>;
    }

    return (
        <div className="detail-card">
            {title && <div className="detail-card-title">{title}</div>}
            <div className="detail-card-list">
                {entries.map((entry, i) => (
                    <div className="detail-card-row" key={i}>
                        <span className="detail-card-key">
                            {LABEL_MAP[entry.key] || entry.key}
                        </span>
                        <span className="detail-card-value">
                            {renderValue(entry.value)}
                        </span>
                    </div>
                ))}
            </div>
        </div>
    );
}

function renderValue(val) {
    if (typeof val === 'boolean') return val ? '是' : '否';
    if (Array.isArray(val)) {
        if (val.length === 0) return '-';
        if (val.every(v => typeof v === 'string')) return val.join(', ');
        return JSON.stringify(val);
    }
    if (typeof val === 'object' && val !== null) return JSON.stringify(val);
    if (val === '') return '-';
    return String(val);
}

export default DetailCard;
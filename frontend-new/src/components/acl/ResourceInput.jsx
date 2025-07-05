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

import {Input, Select, Space, Tag} from 'antd';
import {PlusOutlined} from '@ant-design/icons';
import React, {useState} from 'react';

const {Option} = Select;

// 资源类型枚举
const resourceTypes = [
    {value: 0, label: 'Unknown', prefix: 'UNKNOWN'},
    {value: 1, label: 'Any', prefix: 'ANY'},
    {value: 2, label: 'Cluster', prefix: 'CLUSTER'},
    {value: 3, label: 'Namespace', prefix: 'NAMESPACE'},
    {value: 4, label: 'Topic', prefix: 'TOPIC'},
    {value: 5, label: 'Group', prefix: 'GROUP'},
];

const ResourceInput = ({value = [], onChange}) => {
    // 确保 value 始终是数组
    const safeValue = Array.isArray(value) ? value : [];

    const [selectedType, setSelectedType] = useState(resourceTypes[0].prefix); // 默认选中第一个
    const [resourceName, setResourceName] = useState('');
    const [inputVisible, setInputVisible] = useState(false);
    const inputRef = React.useRef(null);

    // 处理删除已添加的资源
    const handleClose = removedResource => {
        const newResources = safeValue.filter(resource => resource !== removedResource);
        onChange(newResources);
    };

    // 显示输入框
    const showInput = () => {
        setInputVisible(true);
        setTimeout(() => {
            inputRef.current?.focus();
        }, 0);
    };

    // 处理资源类型选择
    const handleTypeChange = type => {
        setSelectedType(type);
    };

    // 处理资源名称输入
    const handleNameChange = e => {
        setResourceName(e.target.value);
    };

    // 添加资源到列表
    const handleAddResource = () => {
        if (resourceName) {
            const fullResource = `${selectedType}:${resourceName}`;
            // 避免重复添加
            if (!safeValue.includes(fullResource)) {
                onChange([...safeValue, fullResource]);
            }
            setResourceName(''); // 清空输入
            setInputVisible(false); // 隐藏输入框
        }
    };

    return (
        <Space size={[0, 8]} wrap>
            {/* 显示已添加的资源标签 */}
            {safeValue.map(resource => ( // 使用 safeValue
                <Tag
                    key={resource}
                    closable
                    onClose={() => handleClose(resource)}
                    color="blue"
                >
                    {resource}
                </Tag>
            ))}

            {/* 新增资源输入区域 */}
            {inputVisible ? (
                <Space>
                    <Select
                        value={selectedType}
                        style={{width: 120}}
                        onChange={handleTypeChange}
                    >
                        {resourceTypes.map(type => (
                            <Option key={type.value} value={type.prefix}>
                                {type.label}
                            </Option>
                        ))}
                    </Select>
                    <Input
                        ref={inputRef}
                        style={{width: 180}}
                        value={resourceName}
                        onChange={handleNameChange}
                        onPressEnter={handleAddResource}
                        onBlur={handleAddResource} // 失去焦点也自动添加
                        placeholder="请输入资源名称"
                    />
                </Space>
            ) : (
                <Tag onClick={showInput} style={{background: '#fff', borderStyle: 'dashed'}}>
                    <PlusOutlined/> 添加资源
                </Tag>
            )}
        </Space>
    );
};

export default ResourceInput;

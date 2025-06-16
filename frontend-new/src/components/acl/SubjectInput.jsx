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

import { Input, Select } from 'antd';
import React, { useState, useEffect } from 'react';

const { Option } = Select;

// Subject 类型枚举
const subjectTypes = [
    { value: 'User', label: 'User' },
];

const SubjectInput = ({ value, onChange, disabled }) => {
    // 解析传入的 value，将其拆分为 type 和 name
    const parseValue = (val) => {
        if (!val || typeof val !== 'string') {
            return { type: subjectTypes[0].value, name: '' }; // 默认值
        }
        const parts = val.split(':');
        if (parts.length === 2 && subjectTypes.some(t => t.value === parts[0])) {
            return { type: parts[0], name: parts[1] };
        }
        return { type: subjectTypes[0].value, name: val }; // 如果格式不匹配，将整个值作为 name，类型设为默认
    };

    const [currentType, setCurrentType] = useState(() => parseValue(value).type);
    const [currentName, setCurrentName] = useState(() => parseValue(value).name);

    // 当外部 value 变化时，更新内部状态
    useEffect(() => {
        const parsed = parseValue(value);
        setCurrentType(parsed.type);
        setCurrentName(parsed.name);
    }, [value]);

    // 当类型或名称变化时，通知 Form.Item
    const triggerChange = (changedType, changedName) => {
        if (onChange) {
            // 只有当名称不为空时才组合，否则只返回类型或空字符串
            if (changedName) {
                onChange(`${changedType}:${changedName}`);
            } else if (changedType) { // 如果只选择了类型，但名称为空，则不组合
                onChange(''); // 或者根据需求返回 'User:' 等，但通常这种情况下不应该有值
            } else {
                onChange('');
            }
        }
    };

    const onTypeChange = (newType) => {
        setCurrentType(newType);
        triggerChange(newType, currentName);
    };

    const onNameChange = (e) => {
        const newName = e.target.value;
        setCurrentName(newName);
        triggerChange(currentType, newName);
    };

    return (
        <Input.Group compact>
            <Select
                style={{ width: '30%' }}
                value={currentType}
                onChange={onTypeChange}
                disabled={disabled}
            >
                {subjectTypes.map(type => (
                    <Option key={type.value} value={type.value}>
                        {type.label}
                    </Option>
                ))}
            </Select>
            <Input
                style={{ width: '70%' }}
                value={currentName}
                onChange={onNameChange}
                placeholder="请输入名称 (例如: yourUsername)"
                disabled={disabled}
            />
        </Input.Group>
    );
};

export default SubjectInput;

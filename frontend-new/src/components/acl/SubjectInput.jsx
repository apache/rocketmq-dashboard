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

import {Input, Select} from 'antd';
import React, {useEffect, useState} from 'react';

const {Option} = Select;


const subjectTypes = [
    {value: 'User', label: 'User'},
];

const SubjectInput = ({value, onChange, disabled, t}) => {

    const parseValue = (val) => {
        if (!val || typeof val !== 'string') {
            return {type: subjectTypes[0].value, name: ''}; // 默认值
        }
        const parts = val.split(':');
        if (parts.length === 2 && subjectTypes.some(t => t.value === parts[0])) {
            return {type: parts[0], name: parts[1]};
        }
        return {type: subjectTypes[0].value, name: val};
    };

    const [currentType, setCurrentType] = useState(() => parseValue(value).type);
    const [currentName, setCurrentName] = useState(() => parseValue(value).name);

    useEffect(() => {
        const parsed = parseValue(value);
        setCurrentType(parsed.type);
        setCurrentName(parsed.name);
    }, [value]);

    const triggerChange = (changedType, changedName) => {
        if (onChange) {

            if (changedName) {
                onChange(`${changedType}:${changedName}`);
            } else if (changedType) {
                onChange('');
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
                style={{width: '30%'}}
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
                style={{width: '70%'}}
                value={currentName}
                onChange={onNameChange}
                placeholder={t.PLEASE_INPUT_NAME}
                disabled={disabled}
            />
        </Input.Group>
    );
};

export default SubjectInput;

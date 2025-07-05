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
import {Form, Input, Typography} from 'antd';
import moment from 'moment';
import {useLanguage} from '../i18n/LanguageContext'; // 根据实际路径调整

const {Text} = Typography;

const DlqMessageDetailViewDialog = ({ngDialogData}) => {
    const {t} = useLanguage();

    const messageView = ngDialogData?.messageView || {};

    return (
        <div style={{padding: '20px'}}>
            <Form layout="horizontal" labelCol={{span: 4}} wrapperCol={{span: 20}}>
                <Form.Item label="Message ID:">
                    <Text strong>{messageView.msgId}</Text>
                </Form.Item>
                <Form.Item label="Topic:">
                    <Text strong>{messageView.topic}</Text>
                </Form.Item>
                <Form.Item label="Properties:">
                    <Input.TextArea
                        value={typeof messageView.properties === 'object' ? JSON.stringify(messageView.properties, null, 2) : messageView.properties}
                        style={{minHeight: 100, resize: 'none'}}
                        readOnly
                    />
                </Form.Item>
                <Form.Item label="ReconsumeTimes:">
                    <Text strong>{messageView.reconsumeTimes}</Text>
                </Form.Item>
                <Form.Item label="Tag:">
                    <Text strong>{messageView.properties?.TAGS}</Text>
                </Form.Item>
                <Form.Item label="Key:">
                    <Text strong>{messageView.properties?.KEYS}</Text>
                </Form.Item>
                <Form.Item label="Storetime:">
                    <Text strong>{moment(messageView.storeTimestamp).format('YYYY-MM-DD HH:mm:ss')}</Text>
                </Form.Item>
                <Form.Item label="StoreHost:">
                    <Text strong>{messageView.storeHost}</Text>
                </Form.Item>
                <Form.Item label="Message body:">
                    <Input.TextArea
                        value={messageView.messageBody}
                        style={{minHeight: 100, resize: 'none'}}
                        readOnly
                    />
                </Form.Item>
            </Form>
        </div>
    );
};

export default DlqMessageDetailViewDialog;

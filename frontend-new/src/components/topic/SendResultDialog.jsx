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

import {Button, Form, Modal, Table} from "antd";
import React from "react";

const SendResultDialog = ({visible, onClose, result, t}) => {
    return (
        <Modal
            title="SendResult"
            open={visible}
            onCancel={onClose}
            footer={[
                <Button key="close" onClick={onClose}>
                    {t.CLOSE}
                </Button>,
            ]}
        >
            <Form layout="horizontal">
                <Table
                    bordered
                    dataSource={
                        result
                            ? Object.entries(result).map(([key, value], index) => ({
                                key: index,
                                label: key,
                                value: typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value),
                            }))
                            : []
                    }
                    columns={[
                        {dataIndex: 'label', key: 'label'},
                        {
                            dataIndex: 'value',
                            key: 'value',
                            render: (text) => <pre style={{whiteSpace: 'pre-wrap', margin: 0}}>{text}</pre>,
                        },
                    ]}
                    pagination={false}
                    showHeader={false}
                    rowKey="key"
                    size="small"
                />
            </Form>
        </Modal>
    );
};


export default SendResultDialog;

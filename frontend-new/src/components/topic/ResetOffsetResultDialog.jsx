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

import {Button, Modal, Table} from "antd";
import React from "react";

const ResetOffsetResultDialog = ({visible, onClose, result, t}) => {
    return (
        <Modal
            title="ResetResult"
            open={visible}
            onCancel={onClose}
            footer={[
                <Button key="close" onClick={onClose}>
                    {t.CLOSE}
                </Button>,
            ]}
        >
            {result && Object.entries(result).map(([groupName, groupData]) => (
                <div key={groupName} style={{marginBottom: '16px', border: '1px solid #f0f0f0', padding: '10px'}}>
                    <Table
                        dataSource={[{groupName, status: groupData.status}]}
                        columns={[
                            {title: 'GroupName', dataIndex: 'groupName', key: 'groupName'},
                            {title: 'State', dataIndex: 'status', key: 'status'},
                        ]}
                        pagination={false}
                        rowKey="groupName"
                        size="small"
                        bordered
                    />
                    {groupData.rollbackStatsList === null ? (
                        <div>You Should Check It Yourself</div>
                    ) : (
                        <Table
                            dataSource={groupData.rollbackStatsList.map((item, index) => ({key: index, item}))}
                            columns={[{dataIndex: 'item', key: 'item'}]}
                            pagination={false}
                            rowKey="key"
                            size="small"
                            bordered
                            showHeader={false}
                        />
                    )}
                </div>
            ))}
        </Modal>
    );
};

export default ResetOffsetResultDialog;

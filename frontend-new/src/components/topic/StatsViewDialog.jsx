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

import moment from "moment/moment";
import {Button, Modal, Table} from "antd";
import React from "react";

const StatsViewDialog = ({visible, onClose, topic, statsData, t}) => {
    const columns = [
        {title: t.QUEUE, dataIndex: 'queue', key: 'queue', align: 'center'},
        {title: t.MIN_OFFSET, dataIndex: 'minOffset', key: 'minOffset', align: 'center'},
        {title: t.MAX_OFFSET, dataIndex: 'maxOffset', key: 'maxOffset', align: 'center'},
        {
            title: t.LAST_UPDATE_TIME_STAMP,
            dataIndex: 'lastUpdateTimestamp',
            key: 'lastUpdateTimestamp',
            align: 'center',
            render: (text) => moment(text).format('YYYY-MM-DD HH:mm:ss'),
        },
    ];

    const dataSource = statsData?.offsetTable ? Object.entries(statsData.offsetTable).map(([queue, info]) => ({
        key: queue,
        queue: queue,
        ...info,
    })) : [];

    return (
        <Modal
            title={`[${topic}]${t.STATUS}`}
            open={visible}
            onCancel={onClose}
            width={800}
            footer={[
                <Button key="close" onClick={onClose}>
                    {t.CLOSE}
                </Button>,
            ]}
        >
            <Table
                bordered
                dataSource={dataSource}
                columns={columns}
                pagination={false}
                rowKey="key"
                size="small"
            />
        </Modal>
    );
};

export default StatsViewDialog;

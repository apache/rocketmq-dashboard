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

// TopicModifyDialog.js
import {Button, Modal} from "antd";
import React from "react";
import TopicSingleModifyForm from './TopicSingleModifyForm';

const TopicModifyDialog = ({
                               visible,
                               onClose,
                               initialData,
                               bIsUpdate,
                               writeOperationEnabled,
                               allClusterNameList,
                               allBrokerNameList,
                               onSubmit,
                               t,
                           }) => {

    return (
        <Modal
            title={bIsUpdate ? t.TOPIC_CHANGE : t.TOPIC_ADD}
            open={visible}
            onCancel={onClose}
            width={700}
            footer={[
                <Button key="close" onClick={onClose}>
                    {t.CLOSE}
                </Button>,
            ]}
            Style={{maxHeight: '70vh', overflowY: 'auto'}}
        >
            {initialData.map((data, index) => (
                <TopicSingleModifyForm
                    key={index}
                    initialData={data}
                    bIsUpdate={bIsUpdate}
                    writeOperationEnabled={writeOperationEnabled}
                    allClusterNameList={allClusterNameList}
                    allBrokerNameList={allBrokerNameList}
                    onSubmit={onSubmit}
                    formIndex={index}
                    t={t}
                />
            ))}
        </Modal>
    );
};

export default TopicModifyDialog;

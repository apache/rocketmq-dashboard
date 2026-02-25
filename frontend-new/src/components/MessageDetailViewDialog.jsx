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
import {Button, Descriptions, Modal, notification, Spin, Tag, Typography} from 'antd';
import moment from 'moment';
import {SyncOutlined} from '@ant-design/icons';
import {useLanguage} from '../i18n/LanguageContext';
import {remoteApi} from '../api/remoteApi/remoteApi'; // 确保这个路径正确

const {Text, Paragraph} = Typography;

const MessageDetailViewDialog = ({visible, onCancel, messageId, topic, onResendMessage}) => {
    const {t} = useLanguage();
    const [loading, setLoading] = React.useState(true);
    const [messageDetail, setMessageDetail] = React.useState(null);
    const [error, setError] = React.useState(null);

    React.useEffect(() => {
        const fetchMessageDetail = async () => {
            // 只有当 visible 为 true 且 messageId 和 topic 存在时才进行数据请求
            if (!visible || !messageId || !topic) {
                // 如果 Modal 不可见或者必要参数缺失，则不加载数据
                setMessageDetail(null); // 清空旧数据
                setError(null);      // 清空错误信息
                setLoading(false);   // 停止加载状态
                return;
            }

            setLoading(true);
            setError(null); // 在每次新的请求前清除之前的错误
            try {
                const resp = await remoteApi.viewMessage(messageId, topic);
                if (resp.status === 0) {
                    setMessageDetail(resp.data);
                } else {
                    const errorMessage = resp.errMsg || t.FETCH_MESSAGE_DETAIL_FAILED;
                    setError(errorMessage);
                    notification.error({
                        message: t.ERROR,
                        description: errorMessage,
                    });
                }
            } catch (err) {
                const errorMessage = t.FETCH_MESSAGE_DETAIL_FAILED;
                setError(errorMessage);
                notification.error({
                    message: t.ERROR,
                    description: errorMessage,
                });
                console.error("Error fetching message detail:", err);
            } finally {
                setLoading(false);
            }
        };

        fetchMessageDetail();
    }, [visible, messageId, topic, t]); // 依赖项中添加 visible，确保在 Modal 显示时触发


    // handleShowExceptionDesc 方法不再需要，因为我们直接使用 Paragraph 的 ellipsis

    return (
        <Modal
            title={t.MESSAGE_DETAIL}
            open={visible} // Ant Design 5.x 版本中，visible 属性已更名为 open
            onCancel={onCancel}
            footer={[
                <Button key="close" onClick={onCancel}>
                    {t.CLOSE}
                </Button>,
            ]}
            width={900}
            destroyOnHidden={true} // 使用新的 destroyOnHidden 替代 destroyOnClose
        >
            <Spin spinning={loading} tip={t.LOADING}>
                {error && (
                    <Paragraph type="danger" style={{textAlign: 'center'}}>
                        {error}
                    </Paragraph>
                )}
                {messageDetail ? ( // 确保 messageDetail 存在时才渲染内容
                    <>
                        {/* 消息信息部分 */}
                        <Descriptions title={<Text strong>{t.MESSAGE_INFO}</Text>} bordered column={2} size="small"
                                      style={{marginBottom: 20}}>
                            <Descriptions.Item label="Topic" span={2}><Text
                                copyable>{messageDetail.messageView.topic}</Text></Descriptions.Item>
                            <Descriptions.Item label="Message ID" span={2}><Text
                                copyable>{messageDetail.messageView.msgId}</Text></Descriptions.Item>
                            <Descriptions.Item
                                label="StoreHost">{messageDetail.messageView.storeHost}</Descriptions.Item>
                            <Descriptions.Item label="BornHost">{messageDetail.messageView.bornHost}</Descriptions.Item>
                            <Descriptions.Item label="StoreTime">
                                {moment(messageDetail.messageView.storeTimestamp).format("YYYY-MM-DD HH:mm:ss")}
                            </Descriptions.Item>
                            <Descriptions.Item label="BornTime">
                                {moment(messageDetail.messageView.bornTimestamp).format("YYYY-MM-DD HH:mm:ss")}
                            </Descriptions.Item>
                            <Descriptions.Item label="Queue ID">{messageDetail.messageView.queueId}</Descriptions.Item>
                            <Descriptions.Item
                                label="Queue Offset">{messageDetail.messageView.queueOffset}</Descriptions.Item>
                            <Descriptions.Item
                                label="StoreSize">{messageDetail.messageView.storeSize} bytes</Descriptions.Item>
                            <Descriptions.Item
                                label="ReconsumeTimes">{messageDetail.messageView.reconsumeTimes}</Descriptions.Item>
                            <Descriptions.Item label="BodyCRC">{messageDetail.messageView.bodyCRC}</Descriptions.Item>
                            <Descriptions.Item label="SysFlag">{messageDetail.messageView.sysFlag}</Descriptions.Item>
                            <Descriptions.Item label="Flag">{messageDetail.messageView.flag}</Descriptions.Item>
                            <Descriptions.Item
                                label="PreparedTransactionOffset">{messageDetail.messageView.preparedTransactionOffset}</Descriptions.Item>
                        </Descriptions>

                        {/* 消息属性部分 */}
                        {Object.keys(messageDetail.messageView.properties).length > 0 && (
                            <Descriptions title={<Text strong>{t.MESSAGE_PROPERTIES}</Text>} bordered column={1}
                                          size="small" style={{marginBottom: 20}}>
                                {Object.entries(messageDetail.messageView.properties).map(([key, value]) => (
                                    <Descriptions.Item label={key} key={key}><Text
                                        copyable>{value}</Text></Descriptions.Item>
                                ))}
                            </Descriptions>
                        )}

                        {/* 消息体部分 */}
                        <Descriptions title={<Text strong>{t.MESSAGE_BODY}</Text>} bordered column={1} size="small"
                                      style={{marginBottom: 20}}>
                            <Descriptions.Item>
                                <Paragraph
                                    copyable
                                    ellipsis={{
                                        rows: 5,
                                        expandable: true,
                                        symbol: t.SHOW_ALL_CONTENT,
                                    }}
                                >
                                    {messageDetail.messageView.messageBody}
                                </Paragraph>
                            </Descriptions.Item>
                        </Descriptions>

                        {/* 消息轨迹列表部分 */}
                        {messageDetail.messageTrackList && messageDetail.messageTrackList.length > 0 ? (
                            <>
                                <Text strong>{t.MESSAGE_TRACKING}</Text>
                                <div style={{marginTop: 10}}>
                                    {messageDetail.messageTrackList.map((track, index) => (
                                        <Descriptions bordered column={1} size="small" key={index}
                                                      style={{marginBottom: 15}}>
                                            <Descriptions.Item label={t.CONSUMER_GROUP}>
                                                {track.consumerGroup}
                                            </Descriptions.Item>
                                            <Descriptions.Item label={t.TRACK_TYPE}>
                                                <Tag color={
                                                    track.trackType === 'CONSUMED_SOME_TIME_OK' ? 'success' :
                                                        track.trackType === 'NOT_ONLINE' ? 'default' :
                                                            track.trackType === 'PULL_SUCCESS' ? 'processing' :
                                                                track.trackType === 'NO_MATCHED_CONSUMER' ? 'warning' :
                                                                    'error'
                                                }>
                                                    {track.trackType}
                                                </Tag>
                                            </Descriptions.Item>
                                            <Descriptions.Item label={t.OPERATION}>
                                                <Button
                                                    icon={<SyncOutlined/>}
                                                    onClick={() => onResendMessage(messageDetail.messageView, track.consumerGroup)}
                                                    size="small"
                                                    style={{marginRight: 8}}
                                                >
                                                    {t.RESEND_MESSAGE}
                                                </Button>
                                                {/* 移除“查看异常”按钮，因为现在直接在下方展示可展开内容 */}
                                            </Descriptions.Item>
                                            {track.exceptionDesc && (
                                                <Descriptions.Item label={t.EXCEPTION_SUMMARY}>
                                                    {/* 异常信息截断显示，点击“查看更多”可展开 */}
                                                    <Paragraph
                                                        ellipsis={{
                                                            rows: 2, // 默认显示2行
                                                            expandable: true,
                                                            symbol: <Text style={{
                                                                color: '#1890ff',
                                                                cursor: 'pointer'
                                                            }}>{t.READ_MORE}</Text>, // 蓝色展开文本
                                                        }}
                                                    >
                                                        {track.exceptionDesc}
                                                    </Paragraph>
                                                </Descriptions.Item>
                                            )}
                                        </Descriptions>
                                    ))}
                                </div>
                            </>
                        ) : (
                            <Paragraph>{t.NO_TRACKING_INFO}</Paragraph>
                        )}
                    </>
                ) : (
                    // 当 messageDetail 为 null 时，可以显示一个占位符或者不显示内容
                    !loading && !error &&
                    <Paragraph style={{textAlign: 'center'}}>{t.NO_MESSAGE_DETAIL_AVAILABLE}</Paragraph>
                )}
            </Spin>
        </Modal>
    );
};

export default MessageDetailViewDialog;

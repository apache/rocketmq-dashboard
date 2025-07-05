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

import React, {useEffect, useRef} from 'react';
import {Collapse, Form, Input, Table, Tag, Typography} from 'antd';
import moment from 'moment';
import {useLanguage} from '../i18n/LanguageContext';
import Paragraph from "antd/es/skeleton/Paragraph";
import * as echarts from 'echarts'; // Import ECharts

const {Text} = Typography;
const {Panel} = Collapse;

// Constants for styling and formatting, derived from the example
const SUCCESS_COLOR = '#75d874';
const ERROR_COLOR = 'red';
const UNKNOWN_COLOR = 'yellow';
const TRANSACTION_COMMIT_COLOR = SUCCESS_COLOR;
const TRANSACTION_ROLLBACK_COLOR = ERROR_COLOR;
const TRANSACTION_UNKNOWN_COLOR = 'grey';
const TIME_FORMAT_PATTERN = "YYYY-MM-DD HH:mm:ss.SSS";
const DEFAULT_DISPLAY_DURATION = 10 * 1000;
const TRANSACTION_CHECK_COST_TIME = 50; // transactionTraceNode do not have costTime, assume it cost 50ms

const MessageTraceDetailViewDialog = ({ngDialogData}) => {
    const {t} = useLanguage();
    const messageTraceGraphRef = useRef(null);

    const producerNode = ngDialogData?.producerNode;
    const subscriptionNodeList = ngDialogData?.subscriptionNodeList || [];
    const messageTraceViews = ngDialogData?.messageTraceViews || []; // This data structure seems redundant for the Gantt chart but can be used for extra tooltip details.

    useEffect(() => {
        if (messageTraceGraphRef.current && ngDialogData) {
            const chart = echarts.init(messageTraceGraphRef.current);

            let data = [];
            let dataZoomEnd = 100;
            let startTime = Number.MAX_VALUE;
            let endTime = 0;
            let messageGroups = []; // This will be our Y-axis categories

            if (producerNode) {
                startTime = +producerNode.traceNode.beginTimestamp;
                endTime = +producerNode.traceNode.endTimestamp;
            }

            // Helper functions from the provided example
            function buildNodeColor(traceNode) {
                if (traceNode.transactionState != null) {
                    switch (traceNode.transactionState) {
                        case 'COMMIT_MESSAGE':
                            return TRANSACTION_COMMIT_COLOR;
                        case 'ROLLBACK_MESSAGE':
                            return TRANSACTION_ROLLBACK_COLOR;
                        case 'UNKNOW':
                            return TRANSACTION_UNKNOWN_COLOR;
                        default:
                            return ERROR_COLOR;
                    }
                }
                switch (traceNode.status) {
                    case 'FAILED': // Changed 'failed' to 'FAILED' to match backend typically
                        return ERROR_COLOR;
                    case 'UNKNOWN': // Changed 'unknown' to 'UNKNOWN'
                        return UNKNOWN_COLOR;
                    default:
                        return SUCCESS_COLOR;
                }
            }

            function formatXAxisTime(value) {
                let duration = Math.max(0, value - startTime);
                if (duration < 1000)
                    return timeFormat(duration, 'ms');
                duration /= 1000;
                if (duration < 60)
                    return timeFormat(duration, 's');
                duration /= 60;
                if (duration < 60)
                    return timeFormat(duration, 'min');
                duration /= 60;
                return timeFormat(duration, 'h');
            }

            function timeFormat(duration, unit) {
                return duration.toFixed(2) + unit;
            }

            function buildTraceInfo(itemName, itemValue) {
                if (itemValue) {
                    return `${itemName}: ${itemValue}<br />`
                }
                return "";
            }

            function formatCostTimeStr(costTime) {
                if (costTime < 0) {
                    return "";
                }
                let costTimeStr = costTime;
                if (costTime === 0) {
                    costTimeStr = '<1'
                }
                return `${costTimeStr}ms`;
            }

            function buildCostTimeInfo(costTime) {
                if (costTime < 0) {
                    return "";
                }
                return `Cost Time: ${formatCostTimeStr(costTime)}<br/>`
            }

            function buildTimeStamp(timestamp) {
                if (timestamp < 0) {
                    return 'N/A';
                }
                return moment(timestamp).format(TIME_FORMAT_PATTERN);
            }

            function formatNodeToolTip(params) {
                let traceNode = params.data.traceData.traceNode;
                return `
                        ${buildCostTimeInfo(traceNode.costTime)}
                        Status: ${traceNode.status}<br />
                        ${buildTraceInfo('Begin Timestamp', buildTimeStamp(traceNode.beginTimestamp))}
                        ${buildTraceInfo('End Timestamp', buildTimeStamp(traceNode.endTimestamp))}
                        Client Host: ${traceNode.clientHost}<br />
                        Store Host: ${traceNode.storeHost}<br />
                        Retry Times: ${traceNode.retryTimes < 0 ? 'N/A' : traceNode.retryTimes}<br />
                        ${buildTraceInfo('Message Type', traceNode.msgType)}
                        ${buildTraceInfo('Transaction ID', traceNode.transactionId)}
                        ${buildTraceInfo('Transaction State', traceNode.transactionState)}
                        ${buildTraceInfo('From Transaction Check', traceNode.fromTransactionCheck)}
                    `;
            }

            function calcGraphTimestamp(timestamp, relativeTimeStamp, duration, addDuration) {
                if (timestamp > 0) {
                    return timestamp;
                }
                if (duration < 0) {
                    return relativeTimeStamp;
                }
                return addDuration ? relativeTimeStamp + duration : relativeTimeStamp - duration;
            }

            function addTraceData(traceNode, index, groupName) {
                if (traceNode.beginTimestamp < 0 && traceNode.endTimestamp < 0) {
                    return;
                }
                let beginTimestamp = calcGraphTimestamp(traceNode.beginTimestamp, traceNode.endTimestamp, traceNode.costTime, false);
                let endTimestamp = calcGraphTimestamp(traceNode.endTimestamp, traceNode.beginTimestamp, traceNode.costTime, true);
                if (endTimestamp === beginTimestamp) {
                    endTimestamp = beginTimestamp + 1; // Ensure a minimum duration for visualization
                }
                data.push({
                    name: groupName, // To display group name in tooltip or for internal reference
                    value: [
                        index,
                        beginTimestamp,
                        endTimestamp,
                        traceNode.costTime
                    ],
                    itemStyle: {
                        normal: {
                            color: buildNodeColor(traceNode),
                            opacity: 1
                        }
                    },
                    traceData: {
                        traceNode: traceNode
                    }
                });
                startTime = Math.min(startTime, beginTimestamp);
                endTime = Math.max(endTime, endTimestamp);
            }

            // Populate data for the Gantt chart
            subscriptionNodeList.forEach(item => {
                messageGroups.push(item.subscriptionGroup);
            });

            subscriptionNodeList.forEach((subscriptionNode, index) => {
                subscriptionNode.consumeNodeList.forEach(traceNode => addTraceData(traceNode, index, subscriptionNode.subscriptionGroup));
            });

            if (producerNode) {
                messageGroups.push(producerNode.groupName);
                let producerNodeIndex = messageGroups.length - 1;
                addTraceData(producerNode.traceNode, producerNodeIndex, producerNode.groupName);
                producerNode.transactionNodeList.forEach(transactionNode => {
                    transactionNode.beginTimestamp = Math.max(producerNode.traceNode.endTimestamp,
                        transactionNode.endTimestamp - TRANSACTION_CHECK_COST_TIME);
                    addTraceData(transactionNode, producerNodeIndex, producerNode.groupName);
                    endTime = Math.max(endTime, transactionNode.endTimestamp);
                });
            }

            let totalDuration = endTime - startTime;
            if (totalDuration > DEFAULT_DISPLAY_DURATION) {
                dataZoomEnd = (DEFAULT_DISPLAY_DURATION / totalDuration) * 100;
            }

            function renderItem(params, api) {
                let messageGroupIndex = api.value(0); // Y-axis index
                let start = api.coord([api.value(1), messageGroupIndex]); // X-axis start time, Y-axis group index
                let end = api.coord([api.value(2), messageGroupIndex]); // X-axis end time, Y-axis group index
                let height = api.size([0, 1])[1] * 0.6; // Height of the bar

                let rectShape = echarts.graphic.clipRectByRect({
                    x: start[0],
                    y: start[1] - height / 2,
                    width: Math.max(end[0] - start[0], 1), // Ensure minimum width
                    height: height
                }, {
                    x: params.coordSys.x,
                    y: params.coordSys.y,
                    width: params.coordSys.width,
                    height: params.coordSys.height
                });

                return rectShape && {
                    type: 'rect',
                    transition: ['shape'],
                    shape: rectShape,
                    style: api.style({
                        text: formatCostTimeStr(api.value(3)), // Display cost time on the bar
                        textFill: '#000',
                        textAlign: 'right'
                    })
                };
            }

            const option = {
                tooltip: {
                    formatter: function (params) {
                        return formatNodeToolTip(params);
                    }
                },
                title: {
                    text: producerNode ? `Message Trace: ${producerNode.topic}` : "Message Trace",
                    left: 'center'
                },
                dataZoom: [
                    {
                        type: 'slider',
                        filterMode: 'weakFilter',
                        showDataShadow: false,
                        top: 'bottom', // Position at the bottom
                        start: 0,
                        end: dataZoomEnd,
                        labelFormatter: function (value) {
                            return formatXAxisTime(value + startTime); // Adjust label to show relative time from start
                        }
                    },
                    {
                        type: 'inside',
                        filterMode: 'weakFilter'
                    }
                ],
                grid: {
                    height: 300,
                    left: '10%', // Adjust left margin for Y-axis labels
                    right: '10%'
                },
                xAxis: {
                    min: startTime,
                    scale: true,
                    axisLabel: {
                        formatter: function (value) {
                            return formatXAxisTime(value);
                        }
                    }
                },
                yAxis: {
                    data: messageGroups, // Use group names as Y-axis categories
                    axisLabel: {
                        formatter: function (value, index) {
                            // Display the group name on the Y-axis
                            return value;
                        }
                    }
                },
                series: [
                    {
                        type: 'custom',
                        renderItem: renderItem,
                        encode: {
                            x: [1, 2], // Use beginTimestamp and endTimestamp for X-axis
                            y: 0 // Use the index for Y-axis category
                        },
                        data: data
                    }
                ]
            };

            chart.setOption(option);

            const resizeChart = () => chart.resize();
            window.addEventListener('resize', resizeChart);

            return () => {
                window.removeEventListener('resize', resizeChart);
                chart.dispose();
            };
        }
    }, [ngDialogData, t]); // Add t as a dependency for the useEffect hook

    // ... (rest of your existing component code)
    const transactionColumns = [
        {
            title: t.TIMESTAMP,
            dataIndex: 'beginTimestamp',
            key: 'beginTimestamp',
            align: 'center',
            render: (text) => moment(text).format('YYYY-MM-DD HH:mm:ss.SSS')
        },
        {
            title: t.TRANSACTION_STATE,
            dataIndex: 'transactionState',
            key: 'transactionState',
            align: 'center',
            render: (text) => <Tag
                color={text === 'COMMIT_MESSAGE' ? 'green' : (text === 'ROLLBACK_MESSAGE' ? 'red' : 'default')}>{text}</Tag>
        },
        {
            title: t.FROM_TRANSACTION_CHECK,
            dataIndex: 'fromTransactionCheck',
            key: 'fromTransactionCheck',
            align: 'center',
            render: (text) => (text ? <Tag color="blue">{t.YES}</Tag> : <Tag color="purple">{t.NO}</Tag>)
        },
        {title: t.CLIENT_HOST, dataIndex: 'clientHost', key: 'clientHost', align: 'center'},
        {title: t.STORE_HOST, dataIndex: 'storeHost', key: 'storeHost', align: 'center'},
    ];

    const consumeColumns = [
        {
            title: t.BEGIN_TIMESTAMP,
            dataIndex: 'beginTimestamp',
            key: 'beginTimestamp',
            align: 'center',
            render: (text) => text < 0 ? 'N/A' : moment(text).format('YYYY-MM-DD HH:mm:ss.SSS')
        },
        {
            title: t.END_TIMESTAMP,
            dataIndex: 'endTimestamp',
            key: 'endTimestamp',
            align: 'center',
            render: (text) => text < 0 ? 'N/A' : moment(text).format('YYYY-MM-DD HH:mm:ss.SSS')
        },
        {
            title: t.COST_TIME,
            dataIndex: 'costTime',
            key: 'costTime',
            align: 'center',
            render: (text) => text < 0 ? 'N/A' : `${text === 0 ? '<1' : text}ms`
        },
        {
            title: t.STATUS,
            dataIndex: 'status',
            key: 'status',
            align: 'center',
            render: (text) => <Tag
                color={text === 'SUCCESS' ? 'green' : (text === 'FAILED' ? 'red' : 'default')}>{text}</Tag>
        },
        {
            title: t.RETRY_TIMES,
            dataIndex: 'retryTimes',
            key: 'retryTimes',
            align: 'center',
            render: (text) => text < 0 ? 'N/A' : text
        },
        {title: t.CLIENT_HOST, dataIndex: 'clientHost', key: 'clientHost', align: 'center'},
        {title: t.STORE_HOST, dataIndex: 'storeHost', key: 'storeHost', align: 'center'},
    ];

    return (
        <div style={{padding: '20px', backgroundColor: '#f0f2f5'}}>
            <div style={{
                marginBottom: '20px',
                borderRadius: '8px',
                overflow: 'hidden',
                boxShadow: '0 4px 12px rgba(0, 0, 0, 0.08)'
            }}>
                <Collapse defaultActiveKey={['messageTraceGraph']} expandIconPosition="right">
                    <Panel header={<Typography.Title level={3} style={{
                        margin: 0,
                        color: '#333'
                    }}>{t.MESSAGE_TRACE_GRAPH}</Typography.Title>} key="messageTraceGraph">
                        <div ref={messageTraceGraphRef}
                             style={{height: 500, width: '100%', backgroundColor: '#fff', padding: '10px'}}>
                            {/* ECharts message trace graph will be rendered here */}
                            {(!producerNode && subscriptionNodeList.length === 0) && (
                                <Text type="secondary" style={{
                                    display: 'block',
                                    textAlign: 'center',
                                    marginTop: '150px'
                                }}>{t.TRACE_GRAPH_PLACEHOLDER}</Text>
                            )}
                        </div>
                    </Panel>
                </Collapse>
            </div>

            <div style={{
                marginBottom: '20px',
                borderRadius: '8px',
                overflow: 'hidden',
                boxShadow: '0 4px 12px rgba(0, 0, 0, 0.08)'
            }}>
                <Collapse defaultActiveKey={['sendMessageTrace']} expandIconPosition="right">
                    <Panel header={<Typography.Title level={3} style={{
                        margin: 0,
                        color: '#333'
                    }}>{t.SEND_MESSAGE_TRACE}</Typography.Title>} key="sendMessageTrace">
                        {!producerNode ? (
                            <Paragraph style={{
                                padding: '16px',
                                textAlign: 'center',
                                color: '#666'
                            }}>{t.NO_PRODUCER_TRACE_DATA}</Paragraph>
                        ) : (
                            <div style={{padding: '16px', backgroundColor: '#fff'}}>
                                <Typography.Title level={4} style={{marginBottom: '20px'}}>
                                    {t.SEND_MESSAGE_INFO} : ( {t.MESSAGE_ID} <Text strong
                                                                                   copyable>{producerNode.msgId}</Text> )
                                </Typography.Title>
                                <Form layout="vertical" colon={false}>
                                    <div style={{
                                        display: 'grid',
                                        gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
                                        gap: '16px'
                                    }}>
                                        <Form.Item label={<Text strong>{t.TOPIC}</Text>}>
                                            <Input value={producerNode.topic} readOnly/>
                                        </Form.Item>
                                        <Form.Item label={<Text strong>{t.PRODUCER_GROUP}</Text>}>
                                            <Input value={producerNode.groupName} readOnly/>
                                        </Form.Item>
                                        <Form.Item label={<Text strong>{t.MESSAGE_KEY}</Text>}>
                                            <Input value={producerNode.keys} readOnly/>
                                        </Form.Item>
                                        <Form.Item label={<Text strong>{t.TAG}</Text>}>
                                            <Input value={producerNode.tags} readOnly/>
                                        </Form.Item>

                                        <Form.Item label={<Text strong>{t.BEGIN_TIMESTAMP}</Text>}>
                                            <Input
                                                value={moment(producerNode.traceNode.beginTimestamp).format('YYYY-MM-DD HH:mm:ss.SSS')}
                                                readOnly/>
                                        </Form.Item>
                                        <Form.Item label={<Text strong>{t.END_TIMESTAMP}</Text>}>
                                            <Input
                                                value={moment(producerNode.traceNode.endTimestamp).format('YYYY-MM-DD HH:mm:ss.SSS')}
                                                readOnly/>
                                        </Form.Item>
                                        <Form.Item label={<Text strong>{t.COST_TIME}</Text>}>
                                            <Input
                                                value={`${producerNode.traceNode.costTime === 0 ? '<1' : producerNode.traceNode.costTime}ms`}
                                                readOnly/>
                                        </Form.Item>
                                        <Form.Item label={<Text strong>{t.MSG_TYPE}</Text>}>
                                            <Input value={producerNode.traceNode.msgType} readOnly/>
                                        </Form.Item>

                                        <Form.Item label={<Text strong>{t.CLIENT_HOST}</Text>}>
                                            <Input value={producerNode.traceNode.clientHost} readOnly/>
                                        </Form.Item>
                                        <Form.Item label={<Text strong>{t.STORE_HOST}</Text>}>
                                            <Input value={producerNode.traceNode.storeHost} readOnly/>
                                        </Form.Item>
                                        <Form.Item label={<Text strong>{t.RETRY_TIMES}</Text>}>
                                            <Input value={producerNode.traceNode.retryTimes} readOnly/>
                                        </Form.Item>
                                        <Form.Item label={<Text strong>{t.OFFSET_MSG_ID}</Text>}>
                                            <Input value={producerNode.offSetMsgId} readOnly/>
                                        </Form.Item>
                                    </div>
                                </Form>

                                {producerNode.transactionNodeList && producerNode.transactionNodeList.length > 0 && (
                                    <div style={{marginTop: '30px'}}>
                                        <Typography.Title level={4}
                                                          style={{marginBottom: '15px'}}>{t.CHECK_TRANSACTION_INFO}:</Typography.Title>
                                        <Table
                                            columns={transactionColumns}
                                            dataSource={producerNode.transactionNodeList}
                                            rowKey={(record, index) => `transaction_${index}`}
                                            bordered
                                            pagination={false}
                                            size="middle"
                                            scroll={{x: 'max-content'}}
                                        />
                                    </div>
                                )}
                            </div>
                        )}
                    </Panel>
                </Collapse>
            </div>

            <div style={{borderRadius: '8px', overflow: 'hidden', boxShadow: '0 4px 12px rgba(0, 0, 0, 0.08)'}}>
                <Collapse defaultActiveKey={['consumeMessageTrace']} expandIconPosition="right">
                    <Panel header={<Typography.Title level={3} style={{
                        margin: 0,
                        color: '#333'
                    }}>{t.CONSUME_MESSAGE_TRACE}</Typography.Title>} key="consumeMessageTrace">
                        {subscriptionNodeList.length === 0 ? (
                            <Paragraph style={{
                                padding: '16px',
                                textAlign: 'center',
                                color: '#666'
                            }}>{t.NO_CONSUMER_TRACE_DATA}</Paragraph>
                        ) : (
                            <div style={{padding: '16px', backgroundColor: '#fff'}}>
                                {subscriptionNodeList.map(subscriptionNode => (
                                    <Collapse
                                        key={subscriptionNode.subscriptionGroup}
                                        style={{marginBottom: '10px', border: '1px solid #e0e0e0', borderRadius: '4px'}}
                                        defaultActiveKey={[subscriptionNode.subscriptionGroup]}
                                        ghost
                                    >
                                        <Panel
                                            header={<Typography.Title level={4}
                                                                      style={{margin: 0}}>{t.SUBSCRIPTION_GROUP}: <Text
                                                strong>{subscriptionNode.subscriptionGroup}</Text></Typography.Title>}
                                            key={subscriptionNode.subscriptionGroup}
                                        >
                                            <Table
                                                columns={consumeColumns}
                                                dataSource={subscriptionNode.consumeNodeList}
                                                rowKey={(record, index) => `${subscriptionNode.subscriptionGroup}_${index}`}
                                                bordered
                                                pagination={false}
                                                size="middle"
                                                scroll={{x: 'max-content'}}
                                            />
                                        </Panel>
                                    </Collapse>
                                ))}
                            </div>
                        )}
                    </Panel>
                </Collapse>
            </div>
        </div>
    );
};

export default MessageTraceDetailViewDialog;

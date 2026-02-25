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

import React, {useCallback, useEffect, useRef, useState} from 'react';
import {Card, Col, DatePicker, message, notification, Row, Select, Spin, Table} from 'antd';
import * as echarts from 'echarts';
import moment from 'moment';
import {useLanguage} from '../../i18n/LanguageContext';
import {remoteApi, tools} from '../../api/remoteApi/remoteApi';

const {Option} = Select;

const DashboardPage = () => {
    const {t} = useLanguage();
    const barChartRef = useRef(null);
    const lineChartRef = useRef(null);
    const topicBarChartRef = useRef(null);
    const topicLineChartRef = useRef(null);

    const [loading, setLoading] = useState(false);
    const [date, setDate] = useState(moment());
    const [topicNames, setTopicNames] = useState([]);
    const [selectedTopic, setSelectedTopic] = useState(null);
    const [brokerTableData, setBrokerTableData] = useState([]);


    const barChartInstance = useRef(null);
    const lineChartInstance = useRef(null);
    const topicBarChartInstance = useRef(null);
    const topicLineChartInstance = useRef(null);

    const [messageApi, msgContextHolder] = message.useMessage();
    const [notificationApi, notificationContextHolder] = notification.useNotification();

    const initChart = useCallback((chartRef, titleText, isLine = false) => {
        if (chartRef.current) {
            const chart = echarts.init(chartRef.current);
            let option = {
                title: {text: titleText},
                tooltip: {},
                legend: {data: ['TotalMsg']},
                axisPointer: {type: 'shadow'},
                xAxis: {
                    type: 'category',
                    data: [],
                    axisLabel: {
                        inside: false,
                        color: '#000000',
                        rotate: 0,
                        interval: 0
                    },
                    axisTick: {show: true},
                    axisLine: {show: true},
                    z: 10
                },
                yAxis: {
                    type: 'value',
                    boundaryGap: [0, '100%'],
                    axisLabel: {formatter: (value) => value.toFixed(2)},
                    splitLine: {show: true}
                },
                series: [{name: 'TotalMsg', type: 'bar', data: []}]
            };

            if (isLine) {
                option = {
                    title: {text: titleText},
                    toolbox: {
                        feature: {
                            dataZoom: {yAxisIndex: 'none'},
                            restore: {},
                            saveAsImage: {}
                        }
                    },
                    tooltip: {trigger: 'axis', axisPointer: {animation: false}},
                    yAxis: {
                        type: 'value',
                        boundaryGap: [0, '80%'],
                        axisLabel: {formatter: (value) => value.toFixed(2)},
                        splitLine: {show: true}
                    },
                    dataZoom: [{
                        type: 'inside', start: 90, end: 100
                    }, {
                        start: 0,
                        end: 10,
                        handleIcon: 'M10.7,11.9v-1.3H9.3v1.3c-4.9,0.3-8.8,4.4-8.8,9.4c0,5,3.9,9.1,8.8,9.4v1.3h1.3v-1.3c4.9-0.3,8.8-4.4,8.8-9.4C19.5,16.3,15.6,12.2,10.7,11.9z M13.3,24.4H6.7V23h6.6V24.4z M13.3,19.6H6.7v-1.4h6.6V19.6z',
                        handleSize: '80%',
                        handleStyle: {
                            color: '#fff',
                            shadowBlur: 3,
                            shadowColor: 'rgba(0, 0, 0, 0.6)',
                            shadowOffsetX: 2,
                            shadowOffsetY: 2
                        }
                    }],
                    legend: {data: [], top: 30},
                    xAxis: {type: 'time', boundaryGap: false, data: []},
                    series: []
                };
            }
            chart.setOption(option);
            return chart;
        }
        return null;
    }, []);

    useEffect(() => {
        barChartInstance.current = initChart(barChartRef, t.BROKER + ' TOP 10');
        lineChartInstance.current = initChart(lineChartRef, t.BROKER + ' 5min trend', true);
        topicBarChartInstance.current = initChart(topicBarChartRef, t.TOPIC + ' TOP 10');
        topicLineChartInstance.current = initChart(topicLineChartRef, t.TOPIC + ' 5min trend', true);

        return () => {
            barChartInstance.current?.dispose();
            lineChartInstance.current?.dispose();
            topicBarChartInstance.current?.dispose();
            topicLineChartInstance.current?.dispose();
        };
    }, [t, initChart]);

    const getBrokerBarChartOp = useCallback((xAxisData, data) => {
        return {
            xAxis: {data: xAxisData},
            series: [{name: 'TotalMsg', data: data}]
        };
    }, []);

    const getBrokerLineChartOp = useCallback((legend, data) => {
        const series = [];
        let xAxisData = [];
        let isFirstSeries = true;

        Object.entries(data).forEach(([key, values]) => {
            const tpsValues = [];
            values.forEach(tpsValue => {
                const tpsArray = tpsValue.split(",");
                if (isFirstSeries) {
                    xAxisData.push(moment(parseInt(tpsArray[0])).format("HH:mm:ss"));
                }
                tpsValues.push(parseFloat(tpsArray[1]));
            });
            isFirstSeries = false;
            series.push({
                name: key,
                type: 'line',
                smooth: true,
                symbol: 'none',
                sampling: 'average',
                data: tpsValues
            });
        });

        return {
            legend: {data: legend},
            color: ["#FF0000", "#00BFFF", "#FF00FF", "#1ce322", "#000000", '#EE7942'],
            xAxis: {type: 'category', boundaryGap: false, data: xAxisData},
            series: series
        };
    }, []);

    const getTopicLineChartOp = useCallback((legend, data) => {
        const series = [];
        let xAxisData = [];
        let isFirstSeries = true;

        Object.entries(data).forEach(([key, values]) => {
            const tpsValues = [];
            values.forEach(tpsValue => {
                const tpsArray = tpsValue.split(",");
                if (isFirstSeries) {
                    xAxisData.push(moment(parseInt(tpsArray[0])).format("HH:mm:ss"));
                }
                tpsValues.push(parseFloat(tpsArray[2]));
            });
            isFirstSeries = false;
            series.push({
                name: key,
                type: 'line',
                smooth: true,
                symbol: 'none',
                sampling: 'average',
                data: tpsValues
            });
        });

        return {
            legend: {data: legend},
            xAxis: {type: 'category', boundaryGap: false, data: xAxisData},
            series: series
        };
    }, []);

    const queryLineData = useCallback(async () => {
        const _date = date ? date.format("YYYY-MM-DD") : moment().format("YYYY-MM-DD");

        lineChartInstance.current?.showLoading();
        await remoteApi.queryBrokerHisData(_date, (resp) => {
            lineChartInstance.current?.hideLoading();
            if (resp.status === 0) {
                const _data = {};
                const _xAxisData = [];
                Object.entries(resp.data).forEach(([address, values]) => {
                    _data[address] = values;
                    _xAxisData.push(address);
                });
                lineChartInstance.current?.setOption(getBrokerLineChartOp(_xAxisData, _data));
            } else {
                notificationApi.error({message: resp.errMsg || t.QUERY_BROKER_HISTORY_FAILED, duration: 2});
            }
        });

        if (selectedTopic) {
            topicLineChartInstance.current?.showLoading();
            await remoteApi.queryTopicHisData(_date, selectedTopic, (resp) => {
                topicLineChartInstance.current?.hideLoading();
                if (resp.status === 0) {
                    const _data = {};
                    _data[selectedTopic] = resp.data;
                    topicLineChartInstance.current?.setOption(getTopicLineChartOp([selectedTopic], _data));
                } else {
                    notificationApi.error({message: resp.errMsg || t.QUERY_TOPIC_HISTORY_FAILED, duration: 2});
                }
            });
        }
    }, [date, selectedTopic, getBrokerLineChartOp, getTopicLineChartOp, t]);

    useEffect(() => {
        setLoading(true);
        barChartInstance.current?.showLoading();
        remoteApi.queryClusterList((resp) => {
            setLoading(false);
            barChartInstance.current?.hideLoading();
            if (resp.status === 0) {
                const clusterAddrTable = resp.data.clusterInfo.clusterAddrTable;
                const brokerAddrTable = resp.data.clusterInfo.brokerAddrTable; // Corrected to brokerAddrTable
                const brokerDetail = resp.data.brokerServer;
                const clusterMap = tools.generateBrokerMap(brokerDetail, clusterAddrTable, brokerAddrTable);
                let brokerArray = [];
                Object.values(clusterMap).forEach(brokersInCluster => {
                    brokerArray = brokerArray.concat(brokersInCluster);
                });

                const newData = brokerArray.map(broker => ({
                    ...broker,
                    key: broker.brokerName,
                }));
                setBrokerTableData(newData);

                brokerArray.sort((firstBroker, lastBroker) => {
                    const firstTotalMsg = parseFloat(firstBroker.msgGetTotalTodayNow || 0);
                    const lastTotalMsg = parseFloat(lastBroker.msgGetTotalTodayNow || 0);
                    return lastTotalMsg - firstTotalMsg;
                });

                const xAxisData = [];
                const data = [];
                brokerArray.slice(0, 10).forEach(broker => {
                    xAxisData.push(`${broker.brokerName}:${broker.index}`);
                    data.push(parseFloat(broker.msgGetTotalTodayNow || 0));
                });
                barChartInstance.current?.setOption(getBrokerBarChartOp(xAxisData, data));
            } else {
                notificationApi.error({message: resp.errMsg || t.QUERY_CLUSTER_LIST_FAILED, duration: 2});
            }
        });
    }, [getBrokerBarChartOp, t]);

    useEffect(() => {
        topicBarChartInstance.current?.showLoading();
        remoteApi.queryTopicCurrentData((resp) => {
            topicBarChartInstance.current?.hideLoading();
            if (resp.status === 0) {
                const topicList = resp.data;
                topicList.sort((first, last) => {
                    const firstTotalMsg = parseFloat(first.split(",")[1] || 0);
                    const lastTotalMsg = parseFloat(last.split(",")[1] || 0);
                    return lastTotalMsg - firstTotalMsg;
                });

                const xAxisData = [];
                const data = [];
                const names = [];

                topicList.forEach((currentData) => {
                    const currentArray = currentData.split(",");
                    names.push(currentArray[0]);
                });
                setTopicNames(names);

                if (names.length > 0 && selectedTopic === null) {
                    setSelectedTopic(names[0]);
                }

                topicList.slice(0, 10).forEach((currentData) => {
                    const currentArray = currentData.split(",");
                    xAxisData.push(currentArray[0]);
                    data.push(parseFloat(currentArray[1] || 0));
                });

                const option = {
                    xAxis: {
                        data: xAxisData,
                        axisLabel: {
                            inside: false,
                            color: '#000000',
                            rotate: 60,
                            interval: 0
                        },
                    },
                    series: [{name: 'TotalMsg', data: data}]
                };
                topicBarChartInstance.current?.setOption(option);
            } else {
                notificationApi.error({message: resp.errMsg || t.QUERY_TOPIC_CURRENT_FAILED, duration: 2});
            }
        });
    }, [selectedTopic, t]);

    useEffect(() => {
        if (barChartInstance.current && lineChartInstance.current && topicBarChartInstance.current && topicLineChartInstance.current) {
            queryLineData();
        }
    }, [date, selectedTopic, queryLineData]);

    useEffect(() => {
        const intervalId = setInterval(queryLineData, tools.dashboardRefreshTime);
        return () => {
            clearInterval(intervalId);
        };
    }, [queryLineData]);

    const brokerColumns = [
        {title: t.BROKER_NAME, dataIndex: 'brokerName', key: 'brokerName'},
        {title: t.BROKER_ADDR, dataIndex: 'address', key: 'address'},
        {
            title: t.TOTAL_MSG_RECEIVED_TODAY,
            dataIndex: 'msgGetTotalTodayNow',
            key: 'msgGetTotalTodayNow',
            render: (text) => parseFloat(text || 0).toLocaleString(),
            sorter: (a, b) => parseFloat(a.msgGetTotalTodayNow || 0) - parseFloat(b.msgGetTotalTodayNow || 0),
        },
        {
            title: t.TODAY_PRO_COUNT,
            key: 'todayProCount',
            render: (_, record) => parseFloat(record.msgPutTotalTodayMorning || 0).toLocaleString(), // Assuming msgPutTotalTodayMorning is 'today pro count'
        },
        {
            title: t.YESTERDAY_PRO_COUNT,
            key: 'yesterdayProCount',
            // This calculation (today morning - yesterday morning) might not be correct for 'yesterday pro count'.
            // It depends on what msgPutTotalTodayMorning and msgPutTotalYesterdayMorning truly represent.
            // If they are cumulative totals up to morning, then the difference is not accurate for yesterday's count.
            // You might need a specific 'msgPutTotalYesterdayNow' from the backend.
            render: (_, record) => (parseFloat(record.msgPutTotalTodayMorning || 0) - parseFloat(record.msgPutTotalYesterdayMorning || 0)).toLocaleString(),
        },
    ];

    return (
        <>
            {msgContextHolder}
            {notificationContextHolder}
            <div style={{padding: '20px'}}>
                <Spin spinning={loading} tip={t.LOADING}>
                    <Row gutter={[16, 16]} style={{marginBottom: '20px'}}>
                        <Col span={12}>
                            <Card title={t.BROKER_OVERVIEW} bordered>
                                <Table
                                    columns={brokerColumns}
                                    dataSource={brokerTableData}
                                    rowKey="key"
                                    pagination={false}
                                    size="small"
                                    bordered
                                    scroll={{y: 240}}
                                />
                            </Card>
                        </Col>
                        <Col span={12}>
                            <Card title={t.DASHBOARD_DATE_SELECTION} bordered>
                                <DatePicker
                                    format="YYYY-MM-DD"
                                    value={date}
                                    onChange={setDate}
                                    allowClear
                                    style={{width: '100%'}}
                                />
                            </Card>
                        </Col>
                    </Row>

                    <Row gutter={[16, 16]} style={{marginBottom: '20px'}}>
                        <Col span={12}>
                            <Card title={`${t.BROKER} TOP 10`} bordered>
                                <div ref={barChartRef} style={{height: 300}}/>
                            </Card>
                        </Col>
                        <Col span={12}>
                            <Card title={`${t.BROKER} 5min ${t.TREND}`} bordered>
                                <div ref={lineChartRef} style={{height: 300}}/>
                            </Card>
                        </Col>
                    </Row>

                    <Row gutter={[16, 16]}>
                        <Col span={12}>
                            <Card title={`${t.TOPIC} TOP 10`} bordered>
                                <div ref={topicBarChartRef} style={{height: 300}}/>
                            </Card>
                        </Col>
                        <Col span={12}>
                            <Card title={`${t.TOPIC} 5min ${t.TREND}`} bordered>
                                <div style={{marginBottom: '10px'}}>
                                    <Select
                                        showSearch
                                        style={{width: '100%'}}
                                        placeholder={t.SELECT_TOPIC_PLACEHOLDER}
                                        value={selectedTopic}
                                        onChange={setSelectedTopic}
                                        filterOption={(input, option) =>
                                            option.children.toLowerCase().indexOf(input.toLowerCase()) >= 0
                                        }
                                    >
                                        {topicNames.map(topic => (
                                            <Option key={topic} value={topic}>{topic}</Option>
                                        ))}
                                    </Select>
                                </div>
                                <div ref={topicLineChartRef} style={{height: 300}}/>
                            </Card>
                        </Col>
                    </Row>
                </Spin>
            </div>
        </>

    );
};

export default DashboardPage;

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
import CommandBar from '../../components/llm/CommandBar';

const {Option} = Select;

const DashboardPage = () => {
    const {t} = useLanguage();
    const barChartRef = useRef(null);
    const lineChartRef = useRef(null);
    const topicBarChartRef = useRef(null);
    const topicLineChartRef = useRef(null);
    const accumulationChartRef = useRef(null);
    const transactionChartRef = useRef(null);
    const storageLatencyChartRef = useRef(null);
    const networkThroughputChartRef = useRef(null);
    const replicaSyncChartRef = useRef(null);
    const hotTopicChartRef = useRef(null);
    const consumerConcurrencyChartRef = useRef(null);
    const jvmGcStatsChartRef = useRef(null);

    const [loading, setLoading] = useState(false);
    const [date, setDate] = useState(moment());
    const [topicNames, setTopicNames] = useState([]);
    const [selectedTopic, setSelectedTopic] = useState(null);
    const [brokerTableData, setBrokerTableData] = useState([]);


    const barChartInstance = useRef(null);
    const lineChartInstance = useRef(null);
    const topicBarChartInstance = useRef(null);
    const topicLineChartInstance = useRef(null);
    const accumulationChartInstance = useRef(null);
    const transactionChartInstance = useRef(null);
    const storageLatencyChartInstance = useRef(null);
    const networkThroughputChartInstance = useRef(null);
    const replicaSyncChartInstance = useRef(null);
    const hotTopicChartInstance = useRef(null);
    const consumerConcurrencyChartInstance = useRef(null);
    const jvmGcStatsChartInstance = useRef(null);

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
        accumulationChartInstance.current = initChart(accumulationChartRef, t.ACCUMULATION_DEPTH_TREND, true);
        transactionChartInstance.current = initChart(transactionChartRef, t.TRANSACTION_MSG_METRICS, true);
        storageLatencyChartInstance.current = initChart(storageLatencyChartRef, t.STORAGE_WRITE_LATENCY, true);
        networkThroughputChartInstance.current = initChart(networkThroughputChartRef, t.BROKER_NETWORK_THROUGHPUT, true);
        replicaSyncChartInstance.current = initChart(replicaSyncChartRef, t.REPLICA_SYNC_LATENCY, true);
        hotTopicChartInstance.current = initChart(hotTopicChartRef, t.HOT_TOPIC_TOP10, false);

        // Initialize consumer concurrency chart (grouped bar chart)
        if (consumerConcurrencyChartRef.current) {
            const chart = echarts.init(consumerConcurrencyChartRef.current);
            chart.setOption({
                title: { text: t.CONSUMER_CONCURRENCY },
                tooltip: {
                    trigger: 'axis',
                    axisPointer: { type: 'shadow' },
                    formatter: function (params) {
                        let result = params[0].name + '<br/>';
                        params.forEach(p => {
                            result += p.marker + ' ' + p.seriesName + ': ' + p.value + '<br/>';
                        });
                        if (params.length >= 2 && params[1].value > 0) {
                            const utilization = ((params[0].value / params[1].value) * 100).toFixed(1);
                            result += '<b>' + t.THREAD_UTILIZATION + ': ' + utilization + '%</b>';
                        }
                        return result;
                    }
                },
                legend: { data: [t.CONFIGURED_THREAD_MAX, t.ACTUAL_CONSUMER_CLIENTS], top: 30 },
                grid: { left: '3%', right: '4%', bottom: '15%', containLabel: true },
                xAxis: {
                    type: 'category',
                    data: [],
                    axisLabel: { rotate: 30, interval: 0, fontSize: 10 },
                },
                yAxis: { type: 'value', name: t.THREAD_COUNT },
                series: [
                    {
                        name: t.CONFIGURED_THREAD_MAX,
                        type: 'bar',
                        data: [],
                        itemStyle: { color: '#5470C6' },
                        barGap: '10%',
                    },
                    {
                        name: t.ACTUAL_CONSUMER_CLIENTS,
                        type: 'bar',
                        data: [],
                        itemStyle: { color: '#91CC75' },
                    },
                ],
            });
            consumerConcurrencyChartInstance.current = chart;
        }

        // Initialize JVM GC stats chart (grouped bar chart with dual y-axes)
        if (jvmGcStatsChartRef.current) {
            const chart = echarts.init(jvmGcStatsChartRef.current);
            chart.setOption({
                title: { text: t.JVM_GC_STATS },
                tooltip: {
                    trigger: 'axis',
                    axisPointer: { type: 'shadow' },
                    formatter: function (params) {
                        let result = params[0].name + '<br/>';
                        params.forEach(p => {
                            result += p.marker + ' ' + p.seriesName + ': ' + p.value + '<br/>';
                        });
                        return result;
                    }
                },
                legend: { data: [t.GC_COUNT, t.GC_TIME_MS, t.HEAP_USED, t.HEAP_MAX], top: 30 },
                grid: { left: '3%', right: '6%', bottom: '15%', containLabel: true },
                xAxis: {
                    type: 'category',
                    data: [],
                    axisLabel: { rotate: 30, interval: 0, fontSize: 10 },
                },
                yAxis: [
                    { type: 'value', name: t.GC_COUNT + ' / ' + t.GC_TIME_MS, position: 'left' },
                    { type: 'value', name: t.HEAP_USAGE + ' (MB)', position: 'right' },
                ],
                series: [
                    {
                        name: t.GC_COUNT,
                        type: 'bar',
                        data: [],
                        itemStyle: { color: '#EE6666' },
                    },
                    {
                        name: t.GC_TIME_MS,
                        type: 'bar',
                        data: [],
                        itemStyle: { color: '#FC8452' },
                    },
                    {
                        name: t.HEAP_USED,
                        type: 'bar',
                        yAxisIndex: 1,
                        data: [],
                        itemStyle: { color: '#5470C6' },
                    },
                    {
                        name: t.HEAP_MAX,
                        type: 'bar',
                        yAxisIndex: 1,
                        data: [],
                        itemStyle: { color: '#73C0DE', opacity: 0.5 },
                    },
                ],
            });
            jvmGcStatsChartInstance.current = chart;
        }

        return () => {
            barChartInstance.current?.dispose();
            lineChartInstance.current?.dispose();
            topicBarChartInstance.current?.dispose();
            topicLineChartInstance.current?.dispose();
            accumulationChartInstance.current?.dispose();
            transactionChartInstance.current?.dispose();
            storageLatencyChartInstance.current?.dispose();
            networkThroughputChartInstance.current?.dispose();
            replicaSyncChartInstance.current?.dispose();
            hotTopicChartInstance.current?.dispose();
            consumerConcurrencyChartInstance.current?.dispose();
            jvmGcStatsChartInstance.current?.dispose();
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

    const getAccumulationLineChartOp = useCallback((legend, data) => {
        const series = [];
        let xAxisData = [];
        let isFirstSeries = true;

        Object.entries(data).forEach(([key, values]) => {
            const diffValues = [];
            values.forEach(val => {
                const arr = val.split(",");
                if (isFirstSeries) {
                    xAxisData.push(moment(parseInt(arr[0])).format("HH:mm:ss"));
                }
                diffValues.push(parseFloat(arr[1]));
            });
            isFirstSeries = false;
            series.push({
                name: key,
                type: 'line',
                smooth: true,
                symbol: 'none',
                sampling: 'average',
                areaStyle: {opacity: 0.3},
                data: diffValues
            });
        });

        return {
            legend: {data: legend},
            color: ["#EE6666", "#5470C6", "#91CC75", "#FAC858", "#73C0DE"],
            xAxis: {type: 'category', boundaryGap: false, data: xAxisData},
            series: series
        };
    }, []);

    const getTransactionLineChartOp = useCallback((legend, data) => {
        const series = [];
        let xAxisData = [];
        let isFirstSeries = true;

        Object.entries(data).forEach(([key, values]) => {
            const sndbckTpsValues = [];
            const sndbckNumsValues = [];
            const ckTpsValues = [];
            const ckNumsValues = [];
            values.forEach(val => {
                const arr = val.split(",");
                if (isFirstSeries) {
                    xAxisData.push(moment(parseInt(arr[0])).format("HH:mm:ss"));
                }
                sndbckTpsValues.push(parseFloat(arr[1]));
                sndbckNumsValues.push(parseFloat(arr[2]));
                ckTpsValues.push(parseFloat(arr[3]));
                ckNumsValues.push(parseFloat(arr[4]));
            });
            isFirstSeries = false;
            series.push({
                name: key + ' - ' + t.SNDBCK_PUT_TPS,
                type: 'line', smooth: true, symbol: 'none', sampling: 'average', data: sndbckTpsValues
            });
            series.push({
                name: key + ' - ' + t.SNDBCK_PUT_NUMS_TODAY,
                type: 'line', smooth: true, symbol: 'none', sampling: 'average', data: sndbckNumsValues
            });
            series.push({
                name: key + ' - ' + t.GROUP_CK_TPS,
                type: 'line', smooth: true, symbol: 'none', sampling: 'average', data: ckTpsValues
            });
            series.push({
                name: key + ' - ' + t.GROUP_CK_NUMS_TODAY,
                type: 'line', smooth: true, symbol: 'none', sampling: 'average', data: ckNumsValues
            });
        });

        return {
            legend: {data: series.map(s => s.name), top: 30},
            color: ["#EE6666", "#FC8452", "#5470C6", "#91CC75"],
            xAxis: {type: 'category', boundaryGap: false, data: xAxisData},
            series: series
        };
    }, [t]);

    const getStorageLatencyLineChartOp = useCallback((legend, data) => {
        const series = [];
        let xAxisData = [];
        let isFirstSeries = true;

        Object.entries(data).forEach(([key, values]) => {
            const putLatencyValues = [];
            const getLatencyValues = [];
            const fallSizeValues = [];
            const fallTimeValues = [];
            values.forEach(val => {
                const arr = val.split(",");
                if (isFirstSeries) {
                    xAxisData.push(moment(parseInt(arr[0])).format("HH:mm:ss"));
                }
                putLatencyValues.push(parseFloat(arr[1]));
                getLatencyValues.push(parseFloat(arr[2]));
                fallSizeValues.push(parseFloat(arr[3]));
                fallTimeValues.push(parseFloat(arr[4]));
            });
            isFirstSeries = false;
            series.push({
                name: key + ' - ' + t.TOPIC_PUT_LATENCY,
                type: 'line', smooth: true, symbol: 'none', sampling: 'average', data: putLatencyValues
            });
            series.push({
                name: key + ' - ' + t.GROUP_GET_LATENCY,
                type: 'line', smooth: true, symbol: 'none', sampling: 'average', data: getLatencyValues
            });
            series.push({
                name: key + ' - ' + t.GROUP_GET_FALL_SIZE,
                type: 'line', smooth: true, symbol: 'none', sampling: 'average', data: fallSizeValues
            });
            series.push({
                name: key + ' - ' + t.GROUP_GET_FALL_TIME,
                type: 'line', smooth: true, symbol: 'none', sampling: 'average', data: fallTimeValues
            });
        });

        return {
            legend: {data: series.map(s => s.name), top: 30},
            color: ["#EE6666", "#5470C6", "#91CC75", "#FAC858"],
            xAxis: {type: 'category', boundaryGap: false, data: xAxisData},
            series: series
        };
    }, [t]);

    const getNetworkThroughputLineChartOp = useCallback((legend, data) => {
        const series = [];
        let xAxisData = [];
        let isFirstSeries = true;

        Object.entries(data).forEach(([key, values]) => {
            const putTpsValues = [];
            const putNumsValues = [];
            const getTpsValues = [];
            const getNumsValues = [];
            values.forEach(val => {
                const arr = val.split(",");
                if (isFirstSeries) {
                    xAxisData.push(moment(parseInt(arr[0])).format("HH:mm:ss"));
                }
                putTpsValues.push(parseFloat(arr[1]));
                putNumsValues.push(parseFloat(arr[2]));
                getTpsValues.push(parseFloat(arr[3]));
                getNumsValues.push(parseFloat(arr[4]));
            });
            isFirstSeries = false;
            series.push({
                name: key + ' - ' + t.BROKER_PUT_TPS,
                type: 'line', smooth: true, symbol: 'none', sampling: 'average', data: putTpsValues
            });
            series.push({
                name: key + ' - ' + t.BROKER_PUT_NUMS_TODAY,
                type: 'line', smooth: true, symbol: 'none', sampling: 'average', data: putNumsValues
            });
            series.push({
                name: key + ' - ' + t.BROKER_GET_TPS,
                type: 'line', smooth: true, symbol: 'none', sampling: 'average', data: getTpsValues
            });
            series.push({
                name: key + ' - ' + t.BROKER_GET_NUMS_TODAY,
                type: 'line', smooth: true, symbol: 'none', sampling: 'average', data: getNumsValues
            });
        });

        return {
            legend: {data: series.map(s => s.name), top: 30},
            color: ["#EE6666", "#FC8452", "#5470C6", "#91CC75"],
            xAxis: {type: 'category', boundaryGap: false, data: xAxisData},
            series: series
        };
    }, [t]);

    const getReplicaSyncLineChartOp = useCallback((legend, data) => {
        const series = [];
        let xAxisData = [];
        let isFirstSeries = true;

        Object.entries(data).forEach(([key, values]) => {
            const maxDiffValues = [];
            const transferredValues = [];
            const inSyncValues = [];
            const slaveCountValues = [];
            values.forEach(val => {
                const arr = val.split(",");
                if (isFirstSeries) {
                    xAxisData.push(moment(parseInt(arr[0])).format("HH:mm:ss"));
                }
                maxDiffValues.push(parseFloat(arr[1]));
                transferredValues.push(parseFloat(arr[2]));
                inSyncValues.push(parseFloat(arr[3]));
                slaveCountValues.push(parseFloat(arr[4]));
            });
            isFirstSeries = false;
            series.push({
                name: key + ' - ' + t.REPLICA_SYNC_MAX_DIFF,
                type: 'line', smooth: true, symbol: 'none', sampling: 'average', data: maxDiffValues
            });
            series.push({
                name: key + ' - ' + t.REPLICA_SYNC_TRANSFER_RATE,
                type: 'line', smooth: true, symbol: 'none', sampling: 'average', data: transferredValues
            });
            series.push({
                name: key + ' - ' + t.REPLICA_SYNC_INSYNC_SLAVES,
                type: 'line', smooth: true, symbol: 'none', sampling: 'average', data: inSyncValues
            });
            series.push({
                name: key + ' - ' + t.REPLICA_SYNC_SLAVE_COUNT,
                type: 'line', smooth: true, symbol: 'none', sampling: 'average', data: slaveCountValues
            });
        });

        return {
            legend: {data: series.map(s => s.name), top: 30},
            color: ["#EE6666", "#5470C6", "#91CC75", "#FAC858"],
            xAxis: {type: 'category', boundaryGap: false, data: xAxisData},
            series: series
        };
    }, [t]);

    const getHotTopicBarChartOp = useCallback((legend, data) => {
        const series = [];
        let xAxisData = [];
        let isFirstSeries = true;

        Object.entries(data).forEach(([key, values]) => {
            const putTpsValues = [];
            const putSizeTpsValues = [];
            values.forEach(val => {
                const arr = val.split(",");
                if (isFirstSeries) {
                    xAxisData.push(moment(parseInt(arr[0])).format("HH:mm:ss"));
                }
                putTpsValues.push(parseFloat(arr[1]));
                putSizeTpsValues.push(parseFloat(arr[3]));
            });
            isFirstSeries = false;
            series.push({
                name: key + ' - ' + t.HOT_TOPIC_PUT_TPS,
                type: 'line', smooth: true, symbol: 'none', sampling: 'average', data: putTpsValues
            });
            series.push({
                name: key + ' - ' + t.HOT_TOPIC_PUT_SIZE_TPS,
                type: 'line', smooth: true, symbol: 'none', sampling: 'average', data: putSizeTpsValues
            });
        });

        return {
            legend: {data: series.map(s => s.name), top: 30},
            color: ["#EE6666", "#5470C6"],
            xAxis: {type: 'category', boundaryGap: false, data: xAxisData},
            series: series
        };
    }, [t]);

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
                    console.warn("Topic history data query failed:", resp.errMsg || t.QUERY_TOPIC_HISTORY_FAILED)
                }
            });
        }
    }, [date, selectedTopic, getBrokerLineChartOp, getTopicLineChartOp, t]);

    const queryAccumulationData = useCallback(async () => {
        if (!selectedTopic) return;
        const _date = date ? date.format("YYYY-MM-DD") : moment().format("YYYY-MM-DD");
        accumulationChartInstance.current?.showLoading();
        await remoteApi.queryAccumulationData(_date, selectedTopic, (resp) => {
            accumulationChartInstance.current?.hideLoading();
            if (resp.status === 0) {
                const _data = {};
                _data[selectedTopic] = resp.data;
                accumulationChartInstance.current?.setOption(getAccumulationLineChartOp([selectedTopic], _data));
            } else {
                console.warn("Accumulation data query failed:", resp.errMsg || t.QUERY_ACCUMULATION_FAILED)
            }
        });
    }, [date, selectedTopic, getAccumulationLineChartOp, t]);

    const queryTransactionData = useCallback(async () => {
        if (!selectedTopic) return;
        const _date = date ? date.format("YYYY-MM-DD") : moment().format("YYYY-MM-DD");
        transactionChartInstance.current?.showLoading();
        await remoteApi.queryTransactionData(_date, selectedTopic, (resp) => {
            transactionChartInstance.current?.hideLoading();
            if (resp.status === 0) {
                const _data = {};
                _data[selectedTopic] = resp.data;
                transactionChartInstance.current?.setOption(getTransactionLineChartOp([selectedTopic], _data));
            } else {
                console.warn("Transaction data query failed:", resp.errMsg || t.QUERY_TRANSACTION_FAILED)
            }
        });
    }, [date, selectedTopic, getTransactionLineChartOp, t]);

    const queryStorageLatencyData = useCallback(async () => {
        if (!selectedTopic) return;
        const _date = date ? date.format("YYYY-MM-DD") : moment().format("YYYY-MM-DD");
        storageLatencyChartInstance.current?.showLoading();
        await remoteApi.queryStorageLatencyData(_date, selectedTopic, (resp) => {
            storageLatencyChartInstance.current?.hideLoading();
            if (resp.status === 0) {
                const _data = {};
                _data[selectedTopic] = resp.data;
                storageLatencyChartInstance.current?.setOption(getStorageLatencyLineChartOp([selectedTopic], _data));
            } else {
                console.warn("Storage latency data query failed:", resp.errMsg || t.QUERY_STORAGE_LATENCY_FAILED)
            }
        });
    }, [date, selectedTopic, getStorageLatencyLineChartOp, t]);

    const queryNetworkThroughputData = useCallback(async () => {
        const _date = date ? date.format("YYYY-MM-DD") : moment().format("YYYY-MM-DD");
        networkThroughputChartInstance.current?.showLoading();
        await remoteApi.queryNetworkThroughputData(_date, null, (resp) => {
            networkThroughputChartInstance.current?.hideLoading();
            if (resp.status === 0) {
                const _data = {};
                const _legend = [];
                Object.entries(resp.data).forEach(([brokerName, values]) => {
                    _data[brokerName] = values;
                    _legend.push(brokerName);
                });
                networkThroughputChartInstance.current?.setOption(getNetworkThroughputLineChartOp(_legend, _data));
            } else {
                notificationApi.error({message: resp.errMsg || t.QUERY_NETWORK_THROUGHPUT_FAILED, duration: 2});
            }
        });
    }, [date, getNetworkThroughputLineChartOp, t]);

    const queryReplicaSyncData = useCallback(async () => {
        const _date = date ? date.format("YYYY-MM-DD") : moment().format("YYYY-MM-DD");
        replicaSyncChartInstance.current?.showLoading();
        await remoteApi.queryReplicaSyncData(_date, null, (resp) => {
            replicaSyncChartInstance.current?.hideLoading();
            if (resp.status === 0) {
                const _data = {};
                const _legend = [];
                Object.entries(resp.data).forEach(([brokerName, values]) => {
                    _data[brokerName] = values;
                    _legend.push(brokerName);
                });
                replicaSyncChartInstance.current?.setOption(getReplicaSyncLineChartOp(_legend, _data));
            } else {
                notificationApi.error({message: resp.errMsg || t.QUERY_REPLICA_SYNC_FAILED, duration: 2});
            }
        });
    }, [date, getReplicaSyncLineChartOp, t]);

    const queryHotTopicData = useCallback(async () => {
        const _date = date ? date.format("YYYY-MM-DD") : moment().format("YYYY-MM-DD");
        hotTopicChartInstance.current?.showLoading();
        await remoteApi.queryHotTopicData(_date, null, (resp) => {
            hotTopicChartInstance.current?.hideLoading();
            if (resp.status === 0) {
                const _data = {};
                const _legend = [];
                Object.entries(resp.data).forEach(([topicName, values]) => {
                    _data[topicName] = values;
                    _legend.push(topicName);
                });
                hotTopicChartInstance.current?.setOption(getHotTopicBarChartOp(_legend, _data));
            } else {
                notificationApi.error({message: resp.errMsg || t.QUERY_HOT_TOPIC_FAILED, duration: 2});
            }
        });
    }, [date, getHotTopicBarChartOp, t]);

    const queryConsumerConcurrencyData = useCallback(async () => {
        consumerConcurrencyChartInstance.current?.showLoading();
        await remoteApi.queryConsumerConcurrency((resp) => {
            consumerConcurrencyChartInstance.current?.hideLoading();
            if (resp.status === 0 && Array.isArray(resp.data)) {
                const data = resp.data;
                // Sort by thread utilization (clientCount / consumeThreadMax) descending
                data.sort((a, b) => {
                    const utilA = a.consumeThreadMax > 0 ? a.clientCount / a.consumeThreadMax : 0;
                    const utilB = b.consumeThreadMax > 0 ? b.clientCount / b.consumeThreadMax : 0;
                    return utilB - utilA;
                });

                // Take top 20 for readability
                const top = data.slice(0, 20);
                const xAxisData = top.map(d => d.groupName);
                const threadMaxData = top.map(d => d.consumeThreadMax);
                const clientCountData = top.map(d => d.clientCount);

                consumerConcurrencyChartInstance.current?.setOption({
                    xAxis: { data: xAxisData },
                    series: [
                        { name: t.CONFIGURED_THREAD_MAX, data: threadMaxData },
                        { name: t.ACTUAL_CONSUMER_CLIENTS, data: clientCountData },
                    ],
                });
            } else if (resp.status !== 0) {
                notificationApi.error({message: resp.errMsg || t.QUERY_CONSUMER_CONCURRENCY_FAILED, duration: 2});
            }
        });
    }, [t, notificationApi]);

    const queryBrokerJvmStatsData = useCallback(async () => {
        jvmGcStatsChartInstance.current?.showLoading();
        await remoteApi.queryBrokerJvmStats((resp) => {
            jvmGcStatsChartInstance.current?.hideLoading();
            if (resp.status === 0 && Array.isArray(resp.data)) {
                const data = resp.data;
                const xAxisData = data.map(d => `${d.brokerName}:${d.brokerId}`);
                const gcCountData = data.map(d => d.gcCount);
                const gcTimeData = data.map(d => d.gcTimeMillis);
                const heapUsedData = data.map(d => Math.round(d.heapUsed / (1024 * 1024)));
                const heapMaxData = data.map(d => Math.round(d.heapMax / (1024 * 1024)));

                jvmGcStatsChartInstance.current?.setOption({
                    xAxis: { data: xAxisData },
                    series: [
                        { name: t.GC_COUNT, data: gcCountData },
                        { name: t.GC_TIME_MS, data: gcTimeData },
                        { name: t.HEAP_USED, data: heapUsedData },
                        { name: t.HEAP_MAX, data: heapMaxData },
                    ],
                });
            } else if (resp.status !== 0) {
                notificationApi.error({message: resp.errMsg || t.QUERY_JVM_STATS_FAILED, duration: 2});
            }
        });
    }, [t, notificationApi]);

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
            queryAccumulationData();
            queryTransactionData();
            queryStorageLatencyData();
            queryNetworkThroughputData();
            queryReplicaSyncData();
            queryHotTopicData();
            queryConsumerConcurrencyData();
            queryBrokerJvmStatsData();
        }
    }, [date, selectedTopic, queryLineData, queryAccumulationData, queryTransactionData, queryStorageLatencyData, queryNetworkThroughputData, queryReplicaSyncData, queryHotTopicData, queryConsumerConcurrencyData, queryBrokerJvmStatsData]);

    useEffect(() => {
        const intervalId = setInterval(() => {
            queryLineData();
            queryAccumulationData();
            queryTransactionData();
            queryStorageLatencyData();
            queryNetworkThroughputData();
            queryReplicaSyncData();
            queryHotTopicData();
            queryConsumerConcurrencyData();
            queryBrokerJvmStatsData();
        }, tools.dashboardRefreshTime);
        return () => {
            clearInterval(intervalId);
        };
    }, [queryLineData, queryAccumulationData, queryTransactionData, queryStorageLatencyData, queryNetworkThroughputData, queryReplicaSyncData, queryHotTopicData, queryConsumerConcurrencyData, queryBrokerJvmStatsData]);

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
                <CommandBar />
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

                    <Row gutter={[16, 16]} style={{marginTop: '20px'}}>
                        <Col span={12}>
                            <Card title={t.ACCUMULATION_DEPTH_TREND} bordered>
                                <div ref={accumulationChartRef} style={{height: 300}}/>
                            </Card>
                        </Col>
                        <Col span={12}>
                            <Card title={t.TRANSACTION_MSG_METRICS} bordered>
                                <div ref={transactionChartRef} style={{height: 300}}/>
                            </Card>
                        </Col>
                    </Row>

                    <Row gutter={[16, 16]} style={{marginTop: '20px'}}>
                        <Col span={12}>
                            <Card title={t.STORAGE_WRITE_LATENCY} bordered>
                                <div ref={storageLatencyChartRef} style={{height: 300}}/>
                            </Card>
                        </Col>
                        <Col span={12}>
                            <Card title={t.BROKER_NETWORK_THROUGHPUT} bordered>
                                <div ref={networkThroughputChartRef} style={{height: 300}}/>
                            </Card>
                        </Col>
                    </Row>

                    <Row gutter={[16, 16]} style={{marginTop: '20px'}}>
                        <Col span={12}>
                            <Card title={t.REPLICA_SYNC_LATENCY} bordered>
                                <div ref={replicaSyncChartRef} style={{height: 300}}/>
                            </Card>
                        </Col>
                        <Col span={12}>
                            <Card title={t.HOT_TOPIC_TOP10} bordered>
                                <div ref={hotTopicChartRef} style={{height: 300}}/>
                            </Card>
                        </Col>
                    </Row>

                    <Row gutter={[16, 16]} style={{marginTop: '20px'}}>
                        <Col span={12}>
                            <Card title={t.CONSUMER_CONCURRENCY} bordered>
                                <div ref={consumerConcurrencyChartRef} style={{height: 300}}/>
                                <div style={{fontSize: 12, color: '#999', marginTop: 8, textAlign: 'center'}}>
                                    {t.CONCURRENCY_SCALE_HINT}
                                </div>
                            </Card>
                        </Col>
                        <Col span={12}>
                            <Card title={t.JVM_GC_STATS} bordered>
                                <div ref={jvmGcStatsChartRef} style={{height: 300}}/>
                            </Card>
                        </Col>
                    </Row>
                </Spin>
            </div>
        </>

    );
};

export default DashboardPage;

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

const appConfig = {
    apiBaseUrl: process.env.REACT_APP_API_BASE_URL || window.location.origin
};

let _redirectHandler = null;

const remoteApi = {

    setRedirectHandler: (handler) => {
        _redirectHandler = handler;
    },

    buildUrl: (endpoint) => {
        if (endpoint.charAt(0) === '/') {
            endpoint = endpoint.substring(1);
        }
        return `${appConfig.apiBaseUrl}/${endpoint}`;
    },


    async getCsrfToken() {
        const csrfToken = this.getCookie();

        if (csrfToken) {
            return csrfToken;
        }

        const response = await fetch(remoteApi.buildUrl("/rocketmq-dashboard/csrf-token"), {
            method: 'GET',
            credentials: 'include'
        });

        const newCsrfToken = this.getCookie();
        if (!newCsrfToken) {
            console.error("Failed to get CSRF Token");
            throw new Error("CSRF Token not available");
        }
        return newCsrfToken;
    },

    getCookie() {
        return document.cookie.replace(/(?:(?:^|.*;\s*)XSRF-TOKEN\s*\=\s*([^;]*).*$)|^.*$/, '$1')
    },

    _fetch: async (url, options = {}) => {
        const headers = {
            ...options.headers,
            'Content-Type': 'application/json',
        };


        const csrfToken = await remoteApi.getCsrfToken();
        console.log(csrfToken)
        if (!csrfToken) {
            console.warn('CSRF Token not found');
        }else{
            headers["X-XSRF-TOKEN"] = csrfToken;
        }
        console.log(csrfToken)


        try {
            const response = await fetch(url, {
                ...options,
                headers,
                credentials: 'include',
            });

            if (response.redirected) {
                if (_redirectHandler) {
                    _redirectHandler();
                }
                return {__isRedirectHandled: true};
            }

            if(response.status == 403){
                window.localStorage.removeItem("csrfToken");
                console.log(111)
                await remoteApi.getCsrfToken()
            }
            return response;
        } catch (error) {
            console.error('fetch error:', error);
            window.localStorage.removeItem("csrfToken");
            console.log(111)
            await remoteApi.getCsrfToken()
        }
    },

    queryTopic: async (skipSysProcess) => {
        try {
            const params = new URLSearchParams();
            if (skipSysProcess) {
                params.append('skipSysProcess', 'true');
            }

            const response = await remoteApi._fetch(remoteApi.buildUrl(`/topic/list.query?${params.toString()}`));
            const data = await response.json();
            return data;
        } catch (error) {
            console.error("Error fetching topic list:", error);
        }
    },

    listUsers: async (brokerName, clusterName) => {
        const params = new URLSearchParams();
        if (brokerName) params.append('brokerName', brokerName);
        if (clusterName) params.append('clusterName', clusterName);
        const response = await remoteApi._fetch(remoteApi.buildUrl(`/acl/users.query?${params.toString()}`));
        return await response.json();
    },

    createUser: async (brokerName, userInfo, clusterName) => {
        const response = await remoteApi._fetch(remoteApi.buildUrl('/acl/createUser.do'), {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({brokerName, userInfo, clusterName})
        });
        return await response.json();
    },

    updateUser: async (brokerName, userInfo, clusterName) => {
        const response = await remoteApi._fetch(remoteApi.buildUrl('/acl/updateUser.do'), {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({brokerName, userInfo, clusterName})
        });
        return await response.json();
    },

    deleteUser: async (brokerName, username, clusterName) => {
        const params = new URLSearchParams();
        if (brokerName) params.append('brokerName', brokerName);
        if (clusterName) params.append('clusterName', clusterName);
        params.append('username', username);
        const response = await remoteApi._fetch(remoteApi.buildUrl(`/acl/deleteUser.do?${params.toString()}`), {
            method: 'DELETE'
        });
        return await response.json();
    },

    listAcls: async (brokerName, searchParam, clusterName) => {
        const params = new URLSearchParams();
        if (brokerName) params.append('brokerName', brokerName);
        if (clusterName) params.append('clusterName', clusterName);
        if (searchParam) params.append('searchParam', searchParam);
        if (searchParam != null) console.log(1111)
        const response = await remoteApi._fetch(remoteApi.buildUrl(`/acl/acls.query?${params.toString()}`));
        return await response.json();
    },

    createAcl: async (brokerName, subject, policies, clusterName) => {
        const response = await remoteApi._fetch(remoteApi.buildUrl('/acl/createAcl.do'), {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({brokerName, subject, policies, clusterName})
        });
        return await response.json();
    },

    updateAcl: async (brokerName, subject, policies, clusterName) => {
        const response = await remoteApi._fetch(remoteApi.buildUrl('/acl/updateAcl.do'), {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({brokerName, subject, policies, clusterName})
        });
        return await response.json();
    },

    deleteAcl: async (brokerName, subject, resource, clusterName) => {
        const params = new URLSearchParams();
        if (brokerName) params.append('brokerAddress', brokerName);
        params.append('subject', subject);
        if (resource) params.append('resource', resource);
        if (clusterName) params.append('clusterName', clusterName);
        const response = await remoteApi._fetch(remoteApi.buildUrl(`/acl/deleteAcl.do?${params.toString()}`), {
            method: 'DELETE'
        });
        return await response.json();
    },


    queryMessageByMessageId: async (msgId, topic, callback) => {
        try {
            const params = new URLSearchParams();
            params.append('msgId', msgId);
            params.append('topic', topic);

            const response = await remoteApi._fetch(remoteApi.buildUrl(`/messageTrace/viewMessage.query?${params.toString()}`));
            const data = await response.json();
            return data
        } catch (error) {
            console.error("Error querying message by ID:", error);
            callback({status: 1, errMsg: "Failed to query message by ID"});
        }
    },

    queryMessageTraceByMessageId: async (msgId, traceTopic, callback) => {
        try {
            const params = new URLSearchParams();
            params.append('msgId', msgId);
            params.append('traceTopic', traceTopic);

            const response = await remoteApi._fetch(remoteApi.buildUrl(`/messageTrace/viewMessageTraceGraph.query?${params.toString()}`));
            const data = await response.json();
            return data;
        } catch (error) {
            console.error("Error querying message trace:", error);

        }
    },
    queryDlqMessageByConsumerGroup: async (consumerGroup, beginTime, endTime, pageNum, pageSize, taskId) => {
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl("/dlqMessage/queryDlqMessageByConsumerGroup.query"), {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    topic: `%DLQ%${consumerGroup}`,
                    begin: beginTime,
                    end: endTime,
                    pageNum: pageNum,
                    pageSize: pageSize,
                    taskId: taskId,
                }),
            });
            const data = await response.json();
            return data;
        } catch (error) {
            console.error("Error querying DLQ messages by consumer group:", error);
            return {status: 1, errMsg: "Failed to query DLQ messages by consumer group"};
        }
    },
    resendDlqMessage: async (msgId, consumerGroup, topic) => {
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl("/message/consumeMessageDirectly.do"), {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                params: {
                    msgId: msgId,
                    consumerGroup: consumerGroup,
                    topic: topic
                },
            });
            const data = await response.json();
            return data;
        } catch (error) {
            console.error("Error resending DLQ message:", error);
            return {status: 1, errMsg: "Failed to resend DLQ message"};
        }
    },
    exportDlqMessage: async (msgId, consumerGroup) => {
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl(`/dlqMessage/exportDlqMessage.do?msgId=${msgId}&consumerGroup=${consumerGroup}`));

            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }

            const data = await response.json();

            const newWindow = window.open('', '_blank');

            if (!newWindow) {
                return {status: 1, errMsg: "Failed to open new window. Please allow pop-ups for this site."};
            }

            newWindow.document.write('<html><head><title>DLQ 导出内容</title></head><body>');
            newWindow.document.write('<h1>DLQ 导出 JSON 内容</h1>');
            newWindow.document.write('<pre>' + JSON.stringify(data, null, 2) + '</pre>');
            newWindow.document.write('</body></html>');
            newWindow.document.close();

            return {status: 0, msg: "导出请求成功，内容已在新页面显示"};
        } catch (error) {
            console.error("Error exporting DLQ message:", error);
            return {status: 1, errMsg: "Failed to export DLQ message: " + error.message};
        }
    },

    batchResendDlqMessage: async (messages) => {
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl("/dlqMessage/batchResendDlqMessage.do"), {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(messages),
            });
            const data = await response.json();
            return data;
        } catch (error) {
            console.error("Error batch resending DLQ messages:", error);
            return {status: 1, errMsg: "Failed to batch resend DLQ messages"};
        }
    },

    /**
     * Queries messages by topic with pagination.
     * @param {string} topic The topic to query.
     * @param {number} begin Timestamp in milliseconds for the start time.
     * @param {number} end Timestamp in milliseconds for the end time.
     * @param {number} pageNum The current page number (1-based).
     * @param {number} pageSize The number of items per page.
     * @param {string} taskId Optional task ID for continuous queries.
     * @returns {Promise<Object>} The API response.
     */
    queryMessagePageByTopic: async (topic, begin, end, pageNum, pageSize, taskId) => {
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl("/message/queryMessagePageByTopic.query"), {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    topic: topic,
                    begin: begin,
                    end: end,
                    pageNum: pageNum,
                    pageSize: pageSize,
                    taskId: taskId
                })
            });
            const data = await response.json();
            return data;
        } catch (error) {
            console.error("Error fetching message page by topic:", error);
        }
    },

    /**
     * Queries messages by topic and key.
     * @param {string} topic The topic to query.
     * @param {string} key The message key to query.
     * @returns {Promise<Object>} The API response.
     */
    queryMessageByTopicAndKey: async (topic, key) => {
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl(`/message/queryMessageByTopicAndKey.query?topic=${encodeURIComponent(topic)}&key=${key}`));
            const data = await response.json();
            return data;
        } catch (error) {
            console.error("Error fetching message by topic and key:", error);

        }
    },

    /**
     * Views a message by its message ID and topic.
     * @param {string} msgId The message ID.
     * @param {string} topic The topic of the message.
     * @returns {Promise<Object>} The API response.
     */
    viewMessage: async (msgId, topic) => {
        try {
            const encodedTopic = encodeURIComponent(topic);

            const url = remoteApi.buildUrl(
                `/message/viewMessage.query?msgId=${msgId}&topic=${encodedTopic}`
            );
            const response = await remoteApi._fetch(url);
            const data = await response.json();
            return data;
        } catch (error) {
            console.error("Error fetching message by message ID:", error);
        }
    },

    /**
     * Resends a message directly to a consumer group.
     * @param {string} msgId The message ID.
     * @param {string} consumerGroup The consumer group to resend to.
     * @param {string} topic The topic of the message.
     * @returns {Promise<Object>} The API response.
     */
    resendMessageDirectly: async (msgId, consumerGroup, topic) => {
        topic = encodeURIComponent(topic)
        consumerGroup = encodeURIComponent(consumerGroup)
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl(`/message/consumeMessageDirectly.do?msgId=${msgId}&consumerGroup=${consumerGroup}&topic=${topic}`), {
                method: 'POST',
            });
            const data = await response.json();
            return data;
        } catch (error) {
            console.error("Error resending message directly:", error);

        }
    },

    queryProducerConnection: async (topic, producerGroup, callback) => {
        topic = encodeURIComponent(topic)
        producerGroup = encodeURIComponent(producerGroup)
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl(`/producer/producerConnection.query?topic=${topic}&producerGroup=${producerGroup}`));
            const data = await response.json();
            callback(data);
        } catch (error) {
            console.error("Error fetching producer connection list:", error);
            callback({status: 1, errMsg: "Failed to fetch producer connection list"}); // Simulate error response
        }
    },

    queryConsumerGroupList: async (skipSysGroup, address) => {
        if (address === undefined) {
            address = ""
        }
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl(`/consumer/groupList.query?skipSysGroup=${skipSysGroup}&address=${address}`));
            const data = await response.json();
            return data;
        } catch (error) {
            console.error("Error fetching consumer group list:", error);
            return {status: 1, errMsg: "Failed to fetch consumer group list"};
        }
    },

    refreshConsumerGroup: async (consumerGroup) => {
        consumerGroup = encodeURIComponent(consumerGroup)
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl(`/consumer/group.refresh?consumerGroup=${consumerGroup}`));
            const data = await response.json();
            return data;
        } catch (error) {
            console.error(`Error refreshing consumer group ${consumerGroup}:`, error);
            return {status: 1, errMsg: `Failed to refresh consumer group ${consumerGroup}`};
        }
    },

    refreshAllConsumerGroup: async (address) => {
        if (address === undefined) {
            address = ""
        }
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl(`/consumer/group.refresh.all?address=${address}`));
            const data = await response.json();
            return data;
        } catch (error) {
            console.error("Error refreshing all consumer groups:", error);
            return {status: 1, errMsg: "Failed to refresh all consumer groups"};
        }
    },

    queryConsumerMonitorConfig: async (consumeGroupName) => {
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl(`/monitor/consumerMonitorConfigByGroupName.query?consumeGroupName=${consumeGroupName}`));
            const data = await response.json();
            return data;
        } catch (error) {
            console.error(`Error fetching monitor config for ${consumeGroupName}:`, error);
            return {status: 1, errMsg: `Failed to fetch monitor config for ${consumeGroupName}`};
        }
    },

    createOrUpdateConsumerMonitor: async (consumeGroupName, minCount, maxDiffTotal) => {
        consumeGroupName = encodeURIComponent(consumeGroupName)
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl("/monitor/createOrUpdateConsumerMonitor.do"), {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({consumeGroupName, minCount, maxDiffTotal})
            });
            const data = await response.json();
            return data;
        } catch (error) {
            console.error("Error creating or updating consumer monitor:", error);
            return {status: 1, errMsg: "Failed to create or update consumer monitor"};
        }
    },

    fetchBrokerNameList: async (consumerGroup) => {
        consumerGroup = encodeURIComponent(consumerGroup)
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl(`/consumer/fetchBrokerNameList.query?consumerGroup=${consumerGroup}`));
            const data = await response.json();
            return data;
        } catch (error) {
            console.error(`Error fetching broker name list for ${consumerGroup}:`, error);
            return {status: 1, errMsg: `Failed to fetch broker name list for ${consumerGroup}`};
        }
    },

    deleteConsumerGroup: async (groupName, brokerNameList) => {
        groupName = encodeURIComponent(groupName)
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl("/consumer/deleteSubGroup.do"), {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({groupName, brokerNameList})
            });
            const data = await response.json();
            return data;
        } catch (error) {
            console.error(`Error deleting consumer group ${groupName}:`, error);
            return {status: 1, errMsg: `Failed to delete consumer group ${groupName}`};
        }
    },

    queryConsumerConfig: async (consumerGroup) => {
        consumerGroup = encodeURIComponent(consumerGroup)
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl(`/consumer/examineSubscriptionGroupConfig.query?consumerGroup=${consumerGroup}`));
            const data = await response.json();
            return data;
        } catch (error) {
            console.error(`Error fetching consumer config for ${consumerGroup}:`, error);
            return {status: 1, errMsg: `Failed to fetch consumer config for ${consumerGroup}`};
        }
    },

    createOrUpdateConsumer: async (consumerRequest) => {
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl("/consumer/createOrUpdate.do"), {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(consumerRequest)
            });
            const data = await response.json();
            return data;
        } catch (error) {
            console.error("Error creating or updating consumer:", error);
            return {status: 1, errMsg: "Failed to create or update consumer"};
        }
    },

    queryTopicByConsumer: async (consumerGroup, address) => {
        consumerGroup = encodeURIComponent(consumerGroup)
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl(`/consumer/queryTopicByConsumer.query?consumerGroup=${consumerGroup}&address=${address}`));
            const data = await response.json();
            return data;
        } catch (error) {
            console.error(`Error fetching topics for consumer group ${consumerGroup}:`, error);
            return {status: 1, errMsg: `Failed to fetch topics for consumer group ${consumerGroup}`};
        }
    },

    queryConsumerConnection: async (consumerGroup, address) => {
        consumerGroup = encodeURIComponent(consumerGroup)
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl(`/consumer/consumerConnection.query?consumerGroup=${consumerGroup}&address=${address}`));
            const data = await response.json();
            return data;
        } catch (error) {
            console.error(`Error fetching consumer connections for ${consumerGroup}:`, error);
            return {status: 1, errMsg: `Failed to fetch consumer connections for ${consumerGroup}`};
        }
    },

    queryConsumerRunningInfo: async (consumerGroup, clientId, jstack = false) => {
        consumerGroup = encodeURIComponent(consumerGroup)
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl(`/consumer/consumerRunningInfo.query?consumerGroup=${consumerGroup}&clientId=${clientId}&jstack=${jstack}`));
            const data = await response.json();
            return data;
        } catch (error) {
            console.error(`Error fetching running info for client ${clientId} in group ${consumerGroup}:`, error);
            return {status: 1, errMsg: `Failed to fetch running info for client ${clientId} in group ${consumerGroup}`};
        }
    },
    queryTopicList: async () => {
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl("/topic/list.queryTopicType"));
            return await response.json();
        } catch (error) {
            console.error("Error fetching topic list:", error);
            return {status: 1, errMsg: "Failed to fetch topic list"};
        }
    },

    deleteTopic: async (topic) => {
        try {
            const url = remoteApi.buildUrl(`/topic/deleteTopic.do?topic=${encodeURIComponent(topic)}`);
            const response = await remoteApi._fetch(url, {
                method: 'POST', // 仍然使用 POST 方法，但参数在 URL 中
                headers: {
                    'Content-Type': 'application/json', // 可以根据你的后端需求决定是否需要这个 header
                },
                // body: JSON.stringify({ topic }) // 移除 body
            });
            return await response.json();
        } catch (error) {
            console.error("Error deleting topic:", error);
            return {status: 1, errMsg: "Failed to delete topic"};
        }
    },

    getTopicStats: async (topic) => {
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl(`/topic/stats.query?topic=${encodeURIComponent(topic)}`));
            return await response.json();
        } catch (error) {
            console.error("Error fetching topic stats:", error);
            return {status: 1, errMsg: "Failed to fetch topic stats"};
        }
    },

    getTopicRoute: async (topic) => {
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl(`/topic/route.query?topic=${encodeURIComponent(topic)}`));
            return await response.json();
        } catch (error) {
            console.error("Error fetching topic route:", error);
            return {status: 1, errMsg: "Failed to fetch topic route"};
        }
    },

    getTopicConsumers: async (topic) => {
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl(`/topic/queryConsumerByTopic.query?topic=${encodeURIComponent(topic)}`));
            return await response.json();
        } catch (error) {
            console.error("Error fetching topic consumers:", error);
            return {status: 1, errMsg: "Failed to fetch topic consumers"};
        }
    },

    getTopicConsumerGroups: async (topic) => {
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl(`/topic/queryTopicConsumerInfo.query?topic=${encodeURIComponent(topic)}`));
            return await response.json();
        } catch (error) {
            console.error("Error fetching consumer groups:", error);
            return {status: 1, errMsg: "Failed to fetch consumer groups"};
        }
    },

    getTopicConfig: async (topic) => {
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl(`/topic/examineTopicConfig.query?topic=${encodeURIComponent(topic)}`));
            return await response.json();
        } catch (error) {
            console.error("Error fetching topic config:", error);
            return {status: 1, errMsg: "Failed to fetch topic config"};
        }
    },

    getClusterList: async () => {
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl("/cluster/list.query"));
            return await response.json();
        } catch (error) {
            console.error("Error fetching cluster list:", error);
            return {status: 1, errMsg: "Failed to fetch cluster list"};
        }
    },

    createOrUpdateTopic: async (topicData) => {
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl("/topic/createOrUpdate.do"), {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(topicData)
            });
            return await response.json();
        } catch (error) {
            console.error("Error creating/updating topic:", error);
            return {status: 1, errMsg: "Failed to create/update topic"};
        }
    },

    resetConsumerOffset: async (data) => {
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl("/consumer/resetOffset.do"), {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(data)
            });
            return await response.json();
        } catch (error) {
            console.error("Error resetting consumer offset:", error);
            return {status: 1, errMsg: "Failed to reset consumer offset"};
        }
    },

    skipMessageAccumulate: async (data) => {
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl("/consumer/skipAccumulate.do"), {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(data)
            });
            return await response.json();
        } catch (error) {
            console.error("Error skipping message accumulate:", error);
            return {status: 1, errMsg: "Failed to skip message accumulate"};
        }
    },

    sendTopicMessage: async (messageData) => {
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl("/topic/sendTopicMessage.do"), {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(messageData)
            });
            return await response.json();
        } catch (error) {
            console.error("Error sending topic message:", error);
            return {status: 1, errMsg: "Failed to send topic message"};
        }
    },

    deleteTopicByBroker: async (brokerName, topic) => {
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl("/topic/deleteTopicByBroker.do"), {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({brokerName, topic})
            });
            return await response.json();
        } catch (error) {
            console.error("Error deleting topic by broker:", error);
            return {status: 1, errMsg: "Failed to delete topic by broker"};
        }
    },

    // New API methods for Ops page
    queryOpsHomePage: async () => {
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl("/ops/homePage.query"));
            return await response.json();
        } catch (error) {
            console.error("Error fetching ops home page data:", error);
            return {status: 1, errMsg: "Failed to fetch ops home page data"};
        }
    },

    updateNameSvrAddr: async (nameSvrAddr) => {
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl(`/ops/updateNameSvrAddr.do?nameSvrAddrList=${encodeURIComponent(nameSvrAddr)}`), {
                method: 'POST',
            });
            return await response.json();
        } catch (error) {
            console.error("Error updating NameServer address:", error);
            return {status: 1, errMsg: "Failed to update NameServer address"};
        }
    },

    addNameSvrAddr: async (newNamesrvAddr) => {
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl(`/ops/addNameSvrAddr.do?newNamesrvAddr=${encodeURIComponent(newNamesrvAddr)}`), {
                method: 'POST',
            });
            return await response.json();
        } catch (error) {
            console.error("Error adding NameServer address:", error);
            return {status: 1, errMsg: "Failed to add NameServer address"};
        }
    },

    updateIsVIPChannel: async (useVIPChannel) => {
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl(`/ops/updateIsVIPChannel.do?useVIPChannel=${useVIPChannel}`), {
                method: 'POST',
            });
            return await response.json();
        } catch (error) {
            console.error("Error updating VIP Channel status:", error);
            return {status: 1, errMsg: "Failed to update VIP Channel status"};
        }
    },

    updateUseTLS: async (useTLS) => {
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl(`/ops/updateUseTLS.do?useTLS=${useTLS}`), {
                method: 'POST',
            });
            return await response.json();
        } catch (error) {
            console.error("Error updating TLS status:", error);
            return {status: 1, errMsg: "Failed to update TLS status"};
        }
    },

    queryClusterList: async (callback) => {
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl("/cluster/list.query"));
            const data = await response.json();
            callback(data);
        } catch (error) {
            console.error("Error fetching cluster list:", error);
            callback({status: 1, errMsg: "Failed to fetch cluster list"});
        }
    },

    queryBrokerHisData: async (date, callback) => {
        try {
            const url = new URL(remoteApi.buildUrl('/dashboard/broker.query'));
            url.searchParams.append('date', date);
            const response = await remoteApi._fetch(url.toString(), {signal: AbortSignal.timeout(15000)}); // 15s timeout
            const data = await response.json();
            callback(data);
        } catch (error) {
            if (error.name === 'TimeoutError') {
                console.error("Broker history data request timed out:", error);
                callback({status: 1, errMsg: "Request timed out for broker history data"});
            } else {
                console.error("Error fetching broker history data:", error);
                callback({status: 1, errMsg: "Failed to fetch broker history data"});
            }
        }
    },

    queryTopicHisData: async (date, topicName, callback) => {
        try {
            const url = new URL(remoteApi.buildUrl('/dashboard/topic.query'));
            url.searchParams.append('date', date);
            url.searchParams.append('topicName', topicName);
            const response = await remoteApi._fetch(url.toString(), {signal: AbortSignal.timeout(15000)}); // 15s timeout
            const data = await response.json();
            callback(data);
        } catch (error) {
            if (error.name === 'TimeoutError') {
                console.error("Topic history data request timed out:", error);
                callback({status: 1, errMsg: "Request timed out for topic history data"});
            } else {
                console.error("Error fetching topic history data:", error);
                callback({status: 1, errMsg: "Failed to fetch topic history data"});
            }
        }
    },

    queryTopicCurrentData: async (callback) => {
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl('/dashboard/topicCurrent.query'), {signal: AbortSignal.timeout(15000)}); // 15s timeout
            const data = await response.json();
            callback(data);
        } catch (error) {
            if (error.name === 'TimeoutError') {
                console.error("Topic current data request timed out:", error);
                callback({status: 1, errMsg: "Request timed out for topic current data"});
            } else {
                console.error("Error fetching topic current data:", error);
                callback({status: 1, errMsg: "Failed to fetch topic current data"});
            }
        }
    },

    queryBrokerConfig: async (brokerAddr, callback) => {
        try {
            const url = new URL(remoteApi.buildUrl('/cluster/brokerConfig.query'));
            url.searchParams.append('brokerAddr', brokerAddr);
            const response = await remoteApi._fetch(url.toString());
            const data = await response.json();
            callback(data);
        } catch (error) {
            console.error("Error fetching broker config:", error);
            callback({status: 1, errMsg: "Failed to fetch broker config"});
        }
    },

    /**
     * 查询 Proxy 首页信息，包括地址列表和当前 Proxy 地址
     */
    queryProxyHomePage: async (callback) => {
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl("/proxy/homePage.query"));
            const data = await response.json();
            callback(data);
        } catch (error) {
            console.error("Error fetching proxy home page:", error);
            callback({status: 1, errMsg: "Failed to fetch proxy home page"});
        }
    },

    /**
     * 添加新的 Proxy 地址
     */
    addProxyAddr: async (newProxyAddr, callback) => {
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl("/proxy/addProxyAddr.do"), {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: new URLSearchParams({newProxyAddr}).toString()
            });
            const data = await response.json();
            callback(data);
        } catch (error) {
            console.error("Error adding proxy address:", error);
            callback({status: 1, errMsg: "Failed to add proxy address"});
        }
    },
    login: async (username, password) => {
        try {

            const response = await remoteApi._fetch(remoteApi.buildUrl("/login/login.do"), {
                method: 'POST',
                body: JSON.stringify({
                    username: username,
                    password: password
                }),
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            const data = await response.json();
            return data;
        } catch (error) {
            console.error("Error logging in:", error);
            return {status: 1, errMsg: "Failed to log in"};
        }
    },

    logout: async () => {
        try {
            const response = await remoteApi._fetch(remoteApi.buildUrl("/login/logout.do"), {
                method: 'POST'
            });
            return await response.json()
        } catch (error) {
            console.error("Error logging out:", error);
            return {status: 1, errMsg: "Failed to log out"};
        }
    }
};

const tools = {
    dashboardRefreshTime: 5000,
    generateBrokerMap: (brokerServer, clusterAddrTable, brokerAddrTable) => {
        const clusterMap = {};

        Object.entries(clusterAddrTable).forEach(([clusterName, brokerNamesInCluster]) => {
            clusterMap[clusterName] = [];

            brokerNamesInCluster.forEach(brokerName => {
                const brokerAddrs = brokerAddrTable[brokerName]?.brokerAddrs;
                if (brokerAddrs) {
                    Object.entries(brokerAddrs).forEach(([brokerIdStr, address]) => {
                        const brokerId = parseInt(brokerIdStr);
                        const detail = brokerServer[brokerName]?.[brokerIdStr];

                        if (detail) {
                            clusterMap[clusterName].push({
                                brokerName: brokerName,
                                brokerId: brokerId,
                                address: address,
                                ...detail,
                                detail: detail,
                                brokerConfig: {},
                            });
                        } else {
                            console.warn(`No detail found for broker: ${brokerName} with ID: ${brokerIdStr}`);
                        }
                    });
                } else {
                    console.warn(`No addresses found for brokerName: ${brokerName} in brokerAddrTable`);
                }
            });
        });
        return clusterMap;
    }
};

export {remoteApi, tools};

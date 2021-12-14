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

var module = app;
const SYS_GROUP_TOPIC_PREFIX = "%SYS%";
module.controller('dlqMessageController', ['$scope', 'ngDialog', '$http', 'Notification', function ($scope, ngDialog, $http, Notification) {
    $scope.allConsumerGroupList = [];
    $scope.selectedConsumerGroup = [];
    $scope.messageId = "";
    $scope.queryDlqMessageByConsumerGroupResult = [];
    $scope.queryDlqMessageByMessageIdResult = {};
    $http({
        method: "GET",
        url: "consumer/groupList.query"
    }).success(function (resp) {
        if (resp.status == 0) {
            for (const consumerGroup of resp.data) {
                if (!consumerGroup.group.startsWith(SYS_GROUP_TOPIC_PREFIX)) {
                    $scope.allConsumerGroupList.push(consumerGroup.group);
                }
            }
            $scope.allConsumerGroupList.sort();
        } else {
            Notification.error({message: resp.errMsg, delay: 2000});
        }
    });
    $scope.timepickerBegin = moment().subtract(3, 'hour').format('YYYY-MM-DD HH:mm');
    $scope.timepickerEnd = moment().format('YYYY-MM-DD HH:mm');
    $scope.timepickerOptions = {format: 'YYYY-MM-DD HH:mm', showClear: true};

    $scope.taskId = "";

    $scope.paginationConf = {
        currentPage: 1,
        totalItems: 0,
        itemsPerPage: 20,
        pagesLength: 15,
        perPageOptions: [10],
        rememberPerPage: 'perPageItems',
        onChange: function () {
            $scope.queryDlqMessageByConsumerGroup()
        }
    };

    $scope.queryDlqMessageByConsumerGroup = function () {
        $("#noMsgTip").css("display", "none");
        if ($scope.timepickerEnd < $scope.timepickerBegin) {
            Notification.error({message: "endTime is later than beginTime!", delay: 2000});
            return
        }
        if ($scope.selectedConsumerGroup === [] || (typeof $scope.selectedConsumerGroup) == "object") {
            return
        }
        $http({
            method: "POST",
            url: "dlqMessage/queryDlqMessageByConsumerGroup.query",
            data: {
                topic: DLQ_GROUP_TOPIC_PREFIX + $scope.selectedConsumerGroup,
                begin: $scope.timepickerBegin.valueOf(),
                end: $scope.timepickerEnd.valueOf(),
                pageNum: $scope.paginationConf.currentPage,
                pageSize: $scope.paginationConf.itemsPerPage,
                taskId: $scope.taskId
            }
        }).success(function (resp) {
            if (resp.status === 0) {
                $scope.messageShowList = resp.data.page.content;
                if ($scope.messageShowList.length == 0){
                    $("#noMsgTip").removeAttr("style");
                }
                for (const message of $scope.messageShowList) {
                    message.checked = false;
                }
                console.log($scope.messageShowList);
                if (resp.data.page.first) {
                    $scope.paginationConf.currentPage = 1;
                }
                $scope.paginationConf.currentPage = resp.data.page.number + 1;
                $scope.paginationConf.totalItems = resp.data.page.totalElements;
                $scope.taskId = resp.data.taskId
            } else {
                Notification.error({message: resp.errMsg, delay: 2000});
            }
        });
    }

    $scope.queryDlqMessageDetail = function (messageId, consumerGroup) {
        $http({
            method: "GET",
            url: "messageTrace/viewMessage.query",
            params: {
                msgId: messageId,
                topic: DLQ_GROUP_TOPIC_PREFIX + consumerGroup
            }
        }).success(function (resp) {
            if (resp.status == 0) {
                console.log(resp);
                ngDialog.open({
                    template: 'dlqMessageDetailViewDialog',
                    data: resp.data
                });
            } else {
                Notification.error({message: resp.errMsg, delay: 2000});
            }
        });
    };

    $scope.queryDlqMessageByMessageId = function (messageId, consumerGroup) {
        $http({
            method: "GET",
            url: "messageTrace/viewMessage.query",
            params: {
                msgId: messageId,
                topic: DLQ_GROUP_TOPIC_PREFIX + consumerGroup
            }
        }).success(function (resp) {
            if (resp.status == 0) {
                $scope.queryDlqMessageByMessageIdResult = resp.data;
            } else {
                Notification.error({message: resp.errMsg, delay: 2000});
            }
        });
    };

    $scope.changeShowMessageList = function (currentPage, totalItem) {
        var perPage = $scope.paginationConf.itemsPerPage;
        var from = (currentPage - 1) * perPage;
        var to = (from + perPage) > totalItem ? totalItem : from + perPage;
        $scope.messageShowList = $scope.queryMessageByTopicResult.slice(from, to);
        $scope.paginationConf.totalItems = totalItem;
    };

    $scope.onChangeQueryCondition = function () {
        console.log("change")
        $scope.taskId = "";
        $scope.paginationConf.currentPage = 1;
        $scope.paginationConf.totalItems = 0;
    }

    $scope.resendDlqMessage = function (messageView, consumerGroup) {
        const topic = messageView.properties.RETRY_TOPIC;
        const msgId = messageView.properties.ORIGIN_MESSAGE_ID;
        $http({
            method: "POST",
            url: "message/consumeMessageDirectly.do",
            params: {
                msgId: msgId,
                consumerGroup: consumerGroup,
                topic: topic
            }
        }).success(function (resp) {
            if (resp.status == 0) {
                ngDialog.open({
                    template: 'operationResultDialog',
                    data: {
                        result: resp.data
                    }
                });
            } else {
                ngDialog.open({
                    template: 'operationResultDialog',
                    data: {
                        result: resp.errMsg
                    }
                });
            }
        });
    };

    $scope.exportDlqMessage = function (msgId, consumerGroup) {
        window.location.href = "dlqMessage/exportDlqMessage.do?msgId=" + msgId + "&consumerGroup=" + consumerGroup;
    };

    $scope.selectedDlqMessage = [];
    $scope.batchResendDlqMessage = function (consumerGroup) {
        for (const message of $scope.messageCheckedList) {
            const dlqMessage = {};
            dlqMessage.topic = message.properties.RETRY_TOPIC;
            dlqMessage.msgId = message.properties.ORIGIN_MESSAGE_ID;
            dlqMessage.consumerGroup = consumerGroup;
            $scope.selectedDlqMessage.push(dlqMessage);
        }
        $http({
            method: "POST",
            url: "dlqMessage/batchResendDlqMessage.do",
            data: $scope.selectedDlqMessage
        }).success(function (resp) {
            $scope.selectedDlqMessage = [];
            if (resp.status == 0) {
                ngDialog.open({
                    template: 'operationResultDialog',
                    data: {
                        result: resp.data
                    }
                });
            } else {
                ngDialog.open({
                    template: 'operationResultDialog',
                    data: {
                        result: resp.errMsg
                    }
                });
            }
        });
    };

    $scope.batchExportDlqMessage = function (consumerGroup) {
        for (const message of $scope.messageCheckedList) {
            const dlqMessage = {};
            dlqMessage.msgId = message.msgId;
            dlqMessage.consumerGroup = consumerGroup;
            $scope.selectedDlqMessage.push(dlqMessage);
        }
        $http({
            method: "POST",
            url: "dlqMessage/batchExportDlqMessage.do",
            data: $scope.selectedDlqMessage,
            headers: {
                'Content-type': 'application/json'
            },
            responseType: "arraybuffer"
        }).success(function (resp) {
            $scope.selectedDlqMessage = [];
            const blob = new Blob([resp], {type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"});
            const objectUrl = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.style.display = 'none';
            a.download = 'dlqs.xlsx';
            a.href = objectUrl;
            a.click();
            document.body.removeChild(a)
        });
    };

    $scope.checkedAll = false;
    $scope.messageCheckedList = [];
    $scope.selectAll = function () {
        $scope.messageCheckedList = [];
        if ($scope.checkedAll == true) {
            angular.forEach($scope.messageShowList, function (item, index) {
                item.checked = true;
                $scope.messageCheckedList.push(item);
            });
        } else {
            angular.forEach($scope.messageShowList, function (item, index) {
                item.checked = false;
            });
        }
        checkBtn($scope.messageCheckedList)
        console.log($scope.messageCheckedList)
    }

    $scope.selectItem = function () {
        var flag = true;
        $scope.messageCheckedList = [];
        angular.forEach($scope.messageShowList, function (item, index) {
            if (item.checked) {
                $scope.messageCheckedList.push(item);
            } else {
                flag = false;
            }
        })
        $scope.checkedAll = flag;
        checkBtn($scope.messageCheckedList)
        console.log($scope.messageCheckedList);
    }

    function checkBtn(messageCheckList) {
        if (messageCheckList.length == 0) {
            $("#batchResendBtn").addClass("disabled");
            $("#batchExportBtn").addClass("disabled");
        } else {
            $("#batchResendBtn").removeClass("disabled");
            $("#batchExportBtn").removeClass("disabled");
        }
    }
}]);
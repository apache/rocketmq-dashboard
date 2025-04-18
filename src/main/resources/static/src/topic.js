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

module.directive('ngConfirmClick', [
    function () {
        return {
            link: function (scope, element, attr) {
                var msg = attr.ngConfirmClick || "Are you sure?";
                var clickAction = attr.confirmedClick;
                element.bind('click', function (event) {
                    if (window.confirm(msg)) {
                        scope.$eval(clickAction)
                    }
                });
            }
        };
    }]);
module.controller('topicController', ['$scope', 'ngDialog', '$http', 'Notification', '$window', function ($scope, ngDialog, $http, Notification, $window) {
    $scope.paginationConf = {
        currentPage: 1,
        totalItems: 0,
        itemsPerPage: 10,
        pagesLength: 15,
        perPageOptions: [10],
        rememberPerPage: 'perPageItems',
        onChange: function () {
            $scope.showTopicList(this.currentPage, this.totalItems);

        }
    };
    $scope.filterNormal = true
    $scope.filterDelay = false
    $scope.filterFifo = false
    $scope.filterTransaction = false
    $scope.filterUnspecified = false
    $scope.filterRetry = false
    $scope.filterDLQ = false
    $scope.filterSystem = false
    $scope.allTopicList = [];
    $scope.allTopicNameList = [];
    $scope.allMessageTypeList = [];
    $scope.topicShowList = [];
    $scope.userRole = $window.sessionStorage.getItem("userrole");
    $scope.writeOperationEnabled = $scope.userRole == null ? true : ($scope.userRole == 1 ? true : false);

    $scope.getTopicList = function () {
        $http({
            method: "GET",
            url: "topic/list.queryTopicType"
        }).success(function (resp) {
            if (resp.status == 0) {
                $scope.allTopicNameList = resp.data.topicNameList;
                $scope.allMessageTypeList = resp.data.messageTypeList;
                console.log($scope.allTopicNameList);
                console.log(JSON.stringify(resp));
                $scope.showTopicList(1, $scope.allTopicNameList.length);

            } else {
                Notification.error({message: resp.errMsg, delay: 5000});
            }
        });
    };

    $scope.refreshTopicList = function () {
        $http({
            method: "POST",
            url: "topic/refresh"
        }).success(function (resp) {
            if (resp.status == 0 && resp.data == true) {
                $http({
                    method: "GET",
                    url: "topic/list.queryTopicType"
                }).success(function (resp1) {
                    if (resp1.status == 0) {
                        $scope.allTopicNameList = resp1.data.topicNameList;
                        $scope.allMessageTypeList = resp1.data.messageTypeList;
                        console.log($scope.allTopicNameList);
                        console.log(JSON.stringify(resp1));
                        $scope.showTopicList(1, $scope.allTopicNameList.length);
                    } else {
                        Notification.error({message: resp1.errMsg, delay: 5000});
                    }
                });

            } else {
                Notification.error({message: resp.errMsg, delay: 5000});
            }
        });
    };

    $scope.getTopicList();

    $scope.filterStr = "";
    $scope.$watch('filterStr', function () {
        $scope.filterList(1);
    });
    $scope.$watch('filterNormal', function () {
        $scope.filterList(1);
    });
    $scope.$watch('filterFifo', function () {
        $scope.filterList(1);
    });
    $scope.$watch('filterTransaction', function () {
        $scope.filterList(1);
    });
    $scope.$watch('filterUnspecified', function () {
        $scope.filterList(1);
    });
    $scope.$watch('filterDelay', function () {
        $scope.filterList(1);
    });
    $scope.$watch('filterRetry', function () {
        $scope.filterList(1);
    });
    $scope.$watch('filterDLQ', function () {
        $scope.filterList(1);
    });
    $scope.$watch('filterSystem', function () {
        $scope.filterList(1);
    });
    $scope.filterList = function (currentPage) {
        var lowExceptStr = $scope.filterStr.toLowerCase();
        var canShowList = [];

        for (let i = 0; i < $scope.allTopicNameList.length; ++i) {
            if ($scope.filterByType($scope.allTopicNameList[i], $scope.allMessageTypeList[i])) {
                if ($scope.allTopicNameList[i].toLowerCase().indexOf(lowExceptStr) != -1) {
                    canShowList.push($scope.allTopicNameList[i]);
                }
            }
        }
        $scope.paginationConf.totalItems = canShowList.length;
        var perPage = $scope.paginationConf.itemsPerPage;
        var from = (currentPage - 1) * perPage;
        var to = (from + perPage) > canShowList.length ? canShowList.length : from + perPage;
        $scope.topicShowList = canShowList.slice(from, to);
    };

    $scope.filterByType = function (str, type) {
        if ($scope.filterRetry) {
            if (type.includes("RETRY")) {
                return true
            }
        }
        if ($scope.filterDLQ) {
            if (type.includes("DLQ")) {
                return true
            }
        }
        if ($scope.filterSystem) {
            if (type.includes("SYSTEM")) {
                return true
            }
        }
        if ($scope.isRmqVersionV5() && $scope.filterUnspecified) {
            if (type.includes("UNSPECIFIED")) {
                return true
            }
        }
        if ($scope.filterNormal) {
            if (type.includes("NORMAL")) {
                return true
            }
            if (!$scope.isRmqVersionV5() && type.includes("UNSPECIFIED")) {
                return true
            }
        }
        if ($scope.isRmqVersionV5() && $scope.filterDelay) {
            if (type.includes("DELAY")) {
                return true
            }
        }
        if ($scope.isRmqVersionV5() && $scope.filterFifo) {
            if (type.includes("FIFO")) {
                return true
            }
        }
        if ($scope.isRmqVersionV5() && $scope.filterTransaction) {
            if (type.includes("TRANSACTION")) {
                return true
            }
        }
        return false;
    };

    $scope.showTopicList = function (currentPage, totalItem) {
        if ($scope.filterStr != "") {
            $scope.filterList(currentPage);
            return;
        }
        var perPage = $scope.paginationConf.itemsPerPage;
        var from = (currentPage - 1) * perPage;
        var to = (from + perPage) > totalItem ? totalItem : from + perPage;
        console.log($scope.allTopicNameList);
        console.log(from)
        console.log(to)
        $scope.topicShowList = $scope.allTopicNameList.slice(from, to);
        $scope.paginationConf.totalItems = totalItem;
        console.log($scope.topicShowList)
        console.log($scope.paginationConf.totalItems)
        $scope.filterList(currentPage);
    };
    $scope.deleteTopic = function (topic) {
        $http({
            method: "POST",
            url: "topic/deleteTopic.do",
            params: {
                topic: topic
            }
        }).success(function (resp) {
            if (resp.status == 0) {
                Notification.info({message: "delete success!", delay: 2000});
                $scope.refreshTopicList();
            } else {
                Notification.error({message: resp.errMsg, delay: 2000});
            }
        });
    };
    $scope.statsView = function (topic) {
        $http({
            method: "GET",
            url: "topic/stats.query",
            params: {topic: topic}
        }).success(function (resp) {
            if (resp.status == 0) {
                console.log(JSON.stringify(resp));
                ngDialog.open({
                    template: 'statsViewDialog',
                    trapFocus: false,
                    data: {
                        topic: topic,
                        statsData: resp.data
                    }
                });
            } else {
                Notification.error({message: resp.errMsg, delay: 2000});
            }
        })
    };
    $scope.routerView = function (topic) {
        $http({
            method: "GET",
            url: "topic/route.query",
            params: {topic: topic}
        }).success(function (resp) {
            if (resp.status == 0) {
                console.log(JSON.stringify(resp));
                ngDialog.open({
                    template: 'routerViewDialog',
                    controller: 'routerViewDialogController',
                    trapFocus: false,
                    data: {
                        topic: topic,
                        routeData: resp.data
                    }
                });
            } else {
                Notification.error({message: resp.errMsg, delay: 2000});
            }
        })
    };


    $scope.consumerView = function (topic) {
        $http({
            method: "GET",
            url: "topic/queryConsumerByTopic.query",
            params: {topic: topic}
        }).success(function (resp) {
            if (resp.status == 0) {
                console.log(JSON.stringify(resp));
                ngDialog.open({
                    template: 'consumerViewDialog',
                    data: {
                        topic: topic,
                        consumerData: resp.data,
                        consumerGroupCount: Object.keys(resp.data).length
                    }
                });
            } else {
                Notification.error({message: resp.errMsg, delay: 2000});
            }
        })
    };
    $scope.openDeleteTopicDialog = function (topic) {
        ngDialog.open({
            template: 'deleteTopicDialog',
            controller: 'deleteTopicDialogController',
            data: {
                topic: topic,
                consumerData: "asd"
            }
        });
    };

    $scope.openConsumerResetOffsetDialog = function (topic) {

        $http({
            method: "GET",
            url: "topic/queryTopicConsumerInfo.query",
            params: {
                topic: topic
            }
        }).success(function (resp) {
            if (resp.status == 0) {
                if (resp.data.groupList == null) {
                    Notification.error({message: "don't have consume group!", delay: 2000});
                    return
                }
                ngDialog.open({
                    template: 'consumerResetOffsetDialog',
                    controller: 'consumerResetOffsetDialogController',
                    data: {
                        topic: topic,
                        selectedConsumerGroup: [],
                        allConsumerGroupList: resp.data.groupList
                    }
                });
            } else {
                Notification.error({message: resp.errMsg, delay: 2000});
            }
        });

    };

    $scope.openSkipMessageAccumulateDialog = function (topic) {
        $http({
            method: "GET",
            url: "topic/queryTopicConsumerInfo.query",
            params: {
                topic: topic
            }
        }).success(function (resp) {
            if (resp.status == 0) {
                if (resp.data.groupList == null) {
                    Notification.error({message: "don't have consume group!", delay: 2000});
                    return
                }
                ngDialog.open({
                    template: 'skipMessageAccumulateDialog',
                    controller: 'skipMessageAccumulateDialogController',
                    data: {
                        topic: topic,
                        selectedConsumerGroup: [],
                        allConsumerGroupList: resp.data.groupList
                    }
                });
            } else {
                Notification.error({message: resp.errMsg, delay: 2000});
            }
        });
    };

    $scope.openSendTopicMessageDialog = function (topic) {
        ngDialog.open({
            template: 'sendTopicMessageDialog',
            controller: 'sendTopicMessageDialogController',
            data: {
                topic: topic
            }
        });
    };

    $scope.openUpdateDialog = function (topic, sysFlag) {
        $http({
            method: "GET",
            url: "topic/examineTopicConfig.query",
            params: {
                topic: topic
            }
        }).success(function (resp) {
            if (resp.status == 0) {
                $scope.openCreateOrUpdateDialog(resp.data, sysFlag);
            } else {
                Notification.error({message: resp.errMsg, delay: 2000});
            }
        });
    };

    $scope.openCreateOrUpdateDialog = function (request, sysFlag) {
        var bIsUpdate = true;
        if (request == null) {
            request = [{
                writeQueueNums: 8,
                readQueueNums: 8,
                perm: 6,
                order: false,
                topicName: "",
                brokerNameList: []
            }];
            bIsUpdate = false;
        }
        $http({
            method: "GET",
            url: "cluster/list.query"
        }).success(function (resp) {
            if (resp.status == 0) {
                console.log(resp);
                ngDialog.open({
                    template: 'topicModifyDialog',
                    controller: 'topicModifyDialogController',
                    data: {
                        sysFlag: sysFlag,
                        topicRequestList: request,
                        allClusterNameList: Object.keys(resp.data.clusterInfo.clusterAddrTable),
                        allBrokerNameList: Object.keys(resp.data.brokerServer),
                        allMessageTypeList: resp.data.messageTypes,
                        bIsUpdate: bIsUpdate,
                        writeOperationEnabled: $scope.writeOperationEnabled
                    }
                });
            }
        });
    }

    $scope.openAddDialog = function () {
        $scope.openCreateOrUpdateDialog(null, false);
    }

}]);

module.controller('topicModifyDialogController', ['$scope', 'ngDialog', '$http', 'Notification', function ($scope, ngDialog, $http, Notification) {
        $scope.postTopicRequest = function (topicRequestItem) {
            console.log(topicRequestItem);
            var request = JSON.parse(JSON.stringify(topicRequestItem));
            console.log(request);
            $http({
                method: "POST",
                url: "topic/createOrUpdate.do",
                data: request
            }).success(function (resp) {
                if (resp.status == 0) {
                    Notification.info({message: "success!", delay: 2000});
                    ngDialog.close(this);
                } else {
                    Notification.error({message: resp.errMsg, delay: 2000});
                }
            });
        }
    }]
);
module.controller('consumerResetOffsetDialogController', ['$scope', 'ngDialog', '$http', 'Notification', function ($scope, ngDialog, $http, Notification) {
        $scope.timepicker = {};
        $scope.timepicker.date = moment().format('YYYY-MM-DD HH:mm');
        $scope.timepicker.options = {format: 'YYYY-MM-DD HH:mm', showClear: true};
        $scope.resetOffset = function () {
            console.log($scope.timepicker.date);
            console.log($scope.timepicker.date.valueOf());
            console.log($scope.ngDialogData.selectedConsumerGroup);
            $http({
                method: "POST",
                url: "consumer/resetOffset.do",
                data: {
                    resetTime: $scope.timepicker.date.valueOf(),
                    consumerGroupList: $scope.ngDialogData.selectedConsumerGroup,
                    topic: $scope.ngDialogData.topic,
                    force: true
                }
            }).success(function (resp) {
                if (resp.status == 0) {
                    ngDialog.open({
                        template: 'resetOffsetResultDialog',
                        data: {
                            result: resp.data
                        }
                    });
                    ngDialog.close(this);
                } else {
                    Notification.error({message: resp.errMsg, delay: 2000});
                }
            })
        }
    }]
);

module.controller('skipMessageAccumulateDialogController', ['$scope', 'ngDialog', '$http', 'Notification', function ($scope, ngDialog, $http, Notification) {
        $scope.skipAccumulate = function () {
            console.log($scope.ngDialogData.selectedConsumerGroup);
            $http({
                method: "POST",
                url: "consumer/skipAccumulate.do",
                data: {
                    resetTime: -1,
                    consumerGroupList: $scope.ngDialogData.selectedConsumerGroup,
                    topic: $scope.ngDialogData.topic,
                    force: true
                }
            }).success(function (resp) {
                if (resp.status == 0) {
                    ngDialog.open({
                        template: 'resetOffsetResultDialog',
                        data: {
                            result: resp.data
                        }
                    });
                    ngDialog.close(this);
                } else {
                    Notification.error({message: resp.errMsg, delay: 2000});
                }
            })
        }
    }]
);

module.controller('sendTopicMessageDialogController', ['$scope', 'ngDialog', '$http', 'Notification', function ($scope, ngDialog, $http, Notification) {
        $scope.sendTopicMessage = {
            topic: $scope.ngDialogData.topic,
            key: "key",
            tag: "tag",
            messageBody: "messageBody",
            traceEnabled: false
        };
        $scope.send = function () {
            $http({
                method: "POST",
                url: "topic/sendTopicMessage.do",
                data: $scope.sendTopicMessage
            }).success(function (resp) {
                if (resp.status == 0) {
                    ngDialog.open({
                        template: 'sendResultDialog',
                        data: {
                            result: resp.data
                        }
                    });
                    ngDialog.close(this);
                } else {
                    Notification.error({message: resp.errMsg, delay: 2000});
                }
            })
        }
    }]
);

module.controller('routerViewDialogController', ['$scope', 'ngDialog', '$http', 'Notification', function ($scope, ngDialog, $http, Notification) {
        $scope.deleteTopicByBroker = function (broker) {
            $http({
                method: "POST",
                url: "topic/deleteTopicByBroker.do",
                params: {brokerName: broker.brokerName, topic: $scope.ngDialogData.topic}
            }).success(function (resp) {
                if (resp.status == 0) {
                    Notification.info({message: "delete success", delay: 2000});
                } else {
                    Notification.error({message: resp.errMsg, delay: 2000});
                }
            })
        };
    }]
);

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

module.controller('connectController', ['$scope', 'ngDialog', '$http', 'Notification', function ($scope, ngDialog, $http, Notification) {
    $scope.allTopicList = [];
    $scope.selectedTopic = [];

    $scope.connectorList = [];
    $scope.workerTaskList = [];
    $scope.allWorkerList = [];

    $scope.respConnectors = [];
    $scope.respTasks = [];
    $scope.respWorkers = [];

    $scope.connectorPaginationConf  = {
        currentPage: 1,
        totalItems: 0,
        itemsPerPage: 10,
        pagesLength: 15,
        perPageOptions: [10],
        rememberPerPage: 'perPageItems',
        onChange: function () {
            $scope.queryWorkerConnectorList(this.currentPage, this.totalItems);
        }
    };

    $scope.taskPaginationConf = {
        currentPage: 1,
        totalItems: 0,
        itemsPerPage: 10,
        pagesLength: 15,
        perPageOptions: [10],
        rememberPerPage: 'perPageItems',
        onChange: function () {
            $scope.queryWorkerTaskList(this.currentPage, this.totalItems);
        }
    };

    $scope.workerPaginationConf = {
        currentPage: 1,
        totalItems: 0,
        itemsPerPage: 10,
        pagesLength: 15,
        perPageOptions: [10],
        rememberPerPage: 'perPageItems',
        onChange: function () {
            $scope.queryWorkerList(this.currentPage, this.totalItems);
        }
    };

    $scope.queryWorkerConnectorList = function (currentPage, totalItem) {
        var perPage = $scope.connectorPaginationConf.itemsPerPage;
        var from = (currentPage - 1) * perPage;
        var to = (from + perPage) > totalItem ? totalItem : from + perPage;
        $scope.connectorList = $scope.respConnectors.slice(from, to);
        $scope.connectorPaginationConf.totalItems = totalItem;

    };

    $scope.queryWorkerTaskList = function (currentPage, totalItem) {
        var perPage = $scope.taskPaginationConf.itemsPerPage;
        var from = (currentPage - 1) * perPage;
        var to = (from + perPage) > totalItem ? totalItem : from + perPage;
        $scope.workerTaskList = $scope.respTasks.slice(from, to);
        $scope.taskPaginationConf.totalItems = totalItem;

    };

    $scope.queryWorkerList = function (currentPage, totalItem) {
        var perPage = $scope.workerPaginationConf.itemsPerPage;
        var from = (currentPage - 1) * perPage;
        var to = (from + perPage) > totalItem ? totalItem : from + perPage;
        $scope.allWorkerList = $scope.respWorkers.slice(from, to);
        $scope.workerPaginationConf.totalItems = totalItem;

    };

    function queryTopicName() {
        $http({
            method: "GET",
            url: "topic/list.query",
        }).success(function (resp) {
            if (resp.status == 0) {
                $scope.allTopicList = resp.data.topicList.sort();
                console.log($scope.allTopicList);
            } else {
                Notification.error({message: resp.errMsg, delay: 2000});
            }
        });
    }



    $scope.showWorkerTaskList = function () {
        $http({
            method: "GET",
            url: "connect/workerTasks.query",
        }).success(function (resp) {
            if (resp.status === 0) {
                console.log(resp);

                $scope.respTasks = resp.data;
                $scope.queryWorkerTaskList($scope.taskPaginationConf.currentPage, $scope.respTasks.length);

            } else {
                Notification.error({message: resp.errMsg, delay: 2000});
            }
        });
    };

    $scope.showWorkerConnectorList = function () {
        $http({
            method: "GET",
            url: "connect/WorkerConnectors.query",
        }).success(function (resp) {
            if (resp.status === 0) {
                console.log(resp);
                $scope.respConnectors = resp.data;

                $scope.queryWorkerConnectorList($scope.connectorPaginationConf.currentPage, $scope.respConnectors.length);
            } else {
                Notification.error({message: resp.errMsg, delay: 2000});
            }
        });
    };

    $scope.showWorkerList = function () {
        $http({
            method: "GET",
            url: "connect/worker.query",
        }).success(function (resp) {
            if (resp.status === 0) {
                console.log(resp);

                $scope.respWorkers = resp.data;
                $scope.queryWorkerList($scope.workerPaginationConf.currentPage, $scope.respWorkers.length);

            } else {
                Notification.error({message: resp.errMsg, delay: 2000});
            }
        });
    };


    $scope.queryConnectorStatus = function (name) {
        $http({
            method: "GET",
            url: "connect/connectorStatus.query",
            params: {
                name: name
            }
        }).success(function (resp) {
            if (resp.data === "running") {
                Notification.info(resp.data);
            } else {
                Notification.error("not running");
            }
            console.log("Connector: " + name + ", Status:" + resp.data);
        })
    };


    $scope.stopThisConnector = function (name) {
        $http({
            method: "GET",
            url: "connect/stopConnector.do",
            params: {
                name: name
            }
        }).success(function (resp) {
            if (resp.status == 0) {
                if (resp.data === "success") {
                    Notification.info({message: "success!", delay: 2000});
                } else {
                    Notification.error(resp.data);
                }
            } else {
                Notification.error({message: resp.errMsg, delay: 2000});
            }
            $scope.showWorkerConnectorList();
        })
    };

    $scope.stopAllConnectors = function () {
        $http({
            method: "GET",
            url: "connect/stopAllConnectors.do"
        }).success(function (resp) {
            if (resp.status == 0) {
                if (resp.data === "success") {
                    Notification.info({message: "success!", delay: 2000});
                } else {
                    Notification.error(resp.data);
                }
            } else {
                Notification.error({message: resp.errMsg, delay: 2000});
            }
            $scope.queryWorkerConnectorList();
        })
    };

    $scope.reloadAllConnectors = function () {
        $http({
            method: "GET",
            url: "connect/reloadConnector.do"
        }).success(function (resp) {
            if (resp.status == 0) {
                if (resp.data === "success") {
                    Notification.info({message: "success!", delay: 2000});
                } else {
                    Notification.error(resp.data);
                }
            } else {
                Notification.error({message: resp.errMsg, delay: 2000});
            }
        })

    };


    $scope.connectorDetail = function (item) {
        ngDialog.open({
            template: 'ConnectorViewDialog',
            data: item
        });
    };

    $scope.taskDetail = function (item) {
        ngDialog.open({
            template: 'TaskViewDialog',
            data: item
        });
    };

    $scope.workerDetail = function (item) {
        ngDialog.open({
            template: 'WorkerViewDialog',
            data: item
        });
    };


    $scope.openCreationDialog = function () {
        $scope.showWorkerList();
        queryTopicName();
        ngDialog.open({
            template: 'connectorCreationDialog',
            scope: $scope

        });
    };


    $scope.postConnectorRequest = function (connectRequestItem) {
        console.log(connectRequestItem);
        connectRequestItem.clusterAddr = connectRequestItem.clusterAddr.ipAddr;
        var request = JSON.parse(JSON.stringify(connectRequestItem));
        console.log(request);
        $http({
            method: "POST",
            url: "connect/createConnector.do",
            data: request
        }).success(function (resp) {
            if (resp.status == 0) {
                if (resp.data === "success") {
                    Notification.info({message: "success!", delay: 2000});
                    $scope.queryWorkerConnectorList();
                    ngDialog.close(this);
                } else {
                    Notification.error(resp.data);
                }
            } else {
                Notification.error({message: resp.errMsg, delay: 2000});
            }
        });
    }

}]);
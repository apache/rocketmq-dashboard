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
module.controller('proxyController', ['$scope', '$location', '$http', 'Notification', 'remoteApi', 'tools', '$window',
    function ($scope, $location, $http, Notification, remoteApi, tools, $window) {
        $scope.proxyAddrList = [];
        $scope.userRole = $window.sessionStorage.getItem("userrole");
        $scope.writeOperationEnabled = $scope.userRole == null ? true : ($scope.userRole == 1 ? true : false);
        $scope.inputReadonly = !$scope.writeOperationEnabled;
        $scope.newProxyAddr = "";
        $scope.allProxyConfig = {};

        $http({
            method: "GET",
            url: "proxy/homePage.query"
        }).success(function (resp) {
            if (resp.status == 0) {
                $scope.proxyAddrList = resp.data.proxyAddrList;
                $scope.selectedProxy = resp.data.currentProxyAddr;
                $scope.showProxyDetailConfig($scope.selectedProxy);
                localStorage.setItem('proxyAddr',$scope.selectedProxy);
            } else {
                Notification.error({message: resp.errMsg, delay: 2000});
            }
        });

        $scope.eleChange = function (data) {
            $scope.proxyAddrList = data;
        }
        $scope.showDetailConf = function () {
            $(".proxyModal").modal();
        }


        $scope.showProxyDetailConfig = function (proxyAddr) {
            $http({
                method: "GET",
                url: "proxy/proxyDetailConfig.query",
                params: {proxyAddress: proxyAddr}
            }).success(function (resp) {
                if (resp.status == 0) {
                    $scope.allProxyConfig = resp.data;
                } else {
                    Notification.error({message: resp.errMsg, delay: 2000});
                }
            });
        };

        $scope.updateProxyAddr = function () {
            $http({
                method: "POST",
                url: "proxy/updateProxyAddr.do",
                params: {proxyAddr: $scope.selectedProxy}
            }).success(function (resp) {
                if (resp.status == 0) {
                    localStorage.setItem('proxyAddr', $scope.selectedProxy);
                    Notification.info({message: "SUCCESS", delay: 2000});
                } else {
                    Notification.error({message: resp.errMsg, delay: 2000});
                }
            });
            $scope.showProxyDetailConfig($scope.selectedProxy);
        };

        $scope.addProxyAddr = function () {
            $http({
                method: "POST",
                url: "proxy/addProxyAddr.do",
                params: {newProxyAddr: $scope.newProxyAddr}
            }).success(function (resp) {
                if (resp.status == 0) {
                    if ($scope.proxyAddrList.indexOf($scope.newProxyAddr) == -1) {
                        $scope.proxyAddrList.push($scope.newProxyAddr);
                    }
                    $("#proxyAddr").val("");
                    $scope.newProxyAddr = "";
                    Notification.info({message: "SUCCESS", delay: 2000});
                } else {
                    Notification.error({message: resp.errMsg, delay: 2000});
                }
            });
        };
    }])

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

module.controller('aclController', ['$scope', 'ngDialog', '$http', 'Notification', '$window', function ($scope, ngDialog, $http, Notification, $window) {
    $scope.paginationConf = {
        currentPage: 1,
        totalItems: 0,
        itemsPerPage: 10,
        pagesLength: 15,
        perPageOptions: [10],
        rememberPerPage: 'perPageItems',
        onChange: function () {
            $scope.showPlainAccessConfigs(this.currentPage, this.totalItems);
        }
    };

    $scope.plainAccessConfigs = [];
    $scope.allPlainAccessConfigs = [];
    $scope.globalWhiteAddrs = [];
    $scope.allGlobalWhiteAddrs = [];
    $scope.userRole = $window.sessionStorage.getItem("userrole");
    $scope.writeOperationEnabled = $scope.userRole == null ? true : ($scope.userRole == 1 ? true : false);
    $scope.showSecretKeyType = {};

    $scope.refreshPlainAccessConfigs = function () {
        $http({
            method: "GET",
            url: "acl/config.query",
            params: {}
        }).success(function (resp) {

            // globalWhiteAddrs
            // plainAccessConfigs
            if (resp.status == 0) {
                $scope.allPlainAccessConfigs = resp.data.plainAccessConfigs;
                $scope.allGlobalWhiteAddrs = resp.data.globalWhiteAddrs;
                $scope.showSecretKeyType = {};
                $scope.allPlainAccessConfigs.forEach(e => $scope.showSecretKeyType[e.accessKey] = {
                    type: 'password',
                    action: 'SHOW'
                });
                $scope.showPlainAccessConfigs(1, $scope.allPlainAccessConfigs.length);
            } else {
                Notification.error({message: resp.errMsg, delay: 2000});
            }
        });
    }
    $scope.refreshPlainAccessConfigs();
    $scope.filterStr = "";
    $scope.$watch('filterStr', function () {
        $scope.paginationConf.currentPage = 1;
        $scope.filterList(1);
    });

    $scope.filterList = function (currentPage) {
        var lowExceptStr = $scope.filterStr.toLowerCase();
        var canShowList = [];

        $scope.allPlainAccessConfigs.forEach(function (element) {
            if (element.accessKey.toLowerCase().indexOf(lowExceptStr) != -1) {
                canShowList.push(element);
            }
        });
        $scope.paginationConf.totalItems = canShowList.length;
        var perPage = $scope.paginationConf.itemsPerPage;
        var from = (currentPage - 1) * perPage;
        var to = (from + perPage) > canShowList.length ? canShowList.length : from + perPage;
        $scope.plainAccessConfigs = canShowList.slice(from, to);
    };

    $scope.showPlainAccessConfigs = function (currentPage, totalItem) {
        var perPage = $scope.paginationConf.itemsPerPage;
        var from = (currentPage - 1) * perPage;
        var to = (from + perPage) > totalItem ? totalItem : from + perPage;
        $scope.plainAccessConfigs = $scope.allPlainAccessConfigs.slice(from, to);
        $scope.paginationConf.totalItems = totalItem;
        $scope.filterList($scope.paginationConf.currentPage)
    };


    // add acl account
    $scope.openAddDialog = function () {
        var request = {};
        request.accessKey = '';
        request.secretKey = '';
        request.admin = false;
        request.defaultTopicPerm = 'DENY';
        request.defaultGroupPerm = 'SUB';
        ngDialog.open({
            preCloseCallback: function (value) {
                $scope.refreshPlainAccessConfigs();
            },
            template: 'addAclAccountDialog',
            controller: 'addAclAccountDialogController',
            data: request
        });
    }

    $scope.deleteAclConfig = function (accessKey) {
        $http({
            method: "POST",
            url: "acl/delete.do",
            data: {accessKey: accessKey}
        }).success(function (resp) {
            if (resp.status == 0) {
                Notification.info({message: "success!", delay: 2000});
                $scope.refreshPlainAccessConfigs();
            } else {
                Notification.error({message: resp.errMsg, delay: 2000});
            }
        });
    }
    $scope.openUpdateDialog = function (request) {
        ngDialog.open({
            preCloseCallback: function (value) {
                $scope.refreshPlainAccessConfigs();
            },
            template: 'updateAclAccountDialog',
            controller: 'updateAclAccountDialogController',
            data: request
        });
    }

    // add acl topic permission
    $scope.openAddTopicDialog = function (request) {
        $.extend(request, {pub: true, sub: true, deny: false})
        ngDialog.open({
            preCloseCallback: function (value) {
                $scope.refreshPlainAccessConfigs();
            },
            template: 'addAclTopicDialog',
            controller: 'addAclTopicDialogController',
            data: request
        });
    }

    // update acl topic permission
    $scope.openUpdateTopicDialog = function (request, topic) {
        var perm = {pub: false, sub: false, deny: false};
        var topicInfo = topic.split('=');
        $.each(topicInfo[1].split('|'), function (i, e) {
            switch (e) {
                case 'PUB':
                    perm.pub = true;
                    break;
                case 'SUB':
                    perm.sub = true;
                    break;
                case 'DENY':
                    perm.deny = true;
                    break;
                default:
                    break;
            }
        });

        $.extend(request, perm, {topic: topicInfo[0]});
        ngDialog.open({
            preCloseCallback: function (value) {
                $scope.refreshPlainAccessConfigs();
            },
            template: 'updateAclTopicDialog',
            controller: 'updateAclTopicDialogController',
            data: request
        });
    }

    // add acl group permission
    $scope.openAddGroupDialog = function (request) {
        $.extend(request, {pub: true, sub: true, deny: false})
        ngDialog.open({
            preCloseCallback: function (value) {
                $scope.refreshPlainAccessConfigs();
            },
            template: 'addAclGroupDialog',
            controller: 'addAclGroupDialogController',
            data: request
        });
    }

    // update acl group permission
    $scope.openUpdateGroupDialog = function (request, group) {
        var perm = {pub: false, sub: false, deny: false};
        var groupInfo = group.split('=');
        $.each(groupInfo[1].split('|'), function (i, e) {
            switch (e) {
                case 'PUB':
                    perm.pub = true;
                    break;
                case 'SUB':
                    perm.sub = true;
                    break;
                case 'DENY':
                    perm.deny = true;
                    break;
                default:
                    break;
            }
        });

        $.extend(request, perm, {group: groupInfo[0]});
        ngDialog.open({
            preCloseCallback: function (value) {
                $scope.refreshPlainAccessConfigs();
            },
            template: 'updateAclGroupDialog',
            controller: 'updateAclGroupDialogController',
            data: request
        });
    }

    $scope.deletePermConfig = function (config, name, type) {
        var request = {config: config};
        switch (type) {
            case 'topic':
                request.topicPerm = name;
                break;
            case 'group':
                request.groupPerm = name;
                break;
            default:
                break;
        }
        $http({
            method: "POST",
            url: "acl/perm/delete.do",
            data: request
        }).success(function (resp) {
            if (resp.status == 0) {
                Notification.info({message: "success!", delay: 2000});
                $scope.refreshPlainAccessConfigs();
            } else {
                Notification.error({message: resp.errMsg, delay: 2000});
            }
        });
    }

    $scope.synchronizeData = function (request) {
        $http({
            method: "POST",
            url: "acl/sync.do",
            data: request
        }).success(function (resp) {
            if (resp.status == 0) {
                Notification.info({message: "success!", delay: 2000});
                $scope.refreshPlainAccessConfigs();
            } else {
                Notification.error({message: resp.errMsg, delay: 2000});
            }
        });
    }

    $scope.openAddAddrDialog = function () {
        ngDialog.open({
            preCloseCallback: function (value) {
                $scope.refreshPlainAccessConfigs();
            },
            template: 'addWhiteListDialog',
            controller: 'addWhiteListDialogController'
        });
    }

    $scope.deleteGlobalWhiteAddr = function (request) {
        $http({
            method: "DELETE",
            url: "acl/white/list/delete.do?request=" + request
        }).success(function (resp) {
            if (resp.status == 0) {
                Notification.info({message: "success!", delay: 2000});
                $scope.refreshPlainAccessConfigs();
            } else {
                Notification.error({message: resp.errMsg, delay: 2000});
            }
        });
    }

    $scope.synchronizeWhiteList = function (request) {
        $http({
            method: "POST",
            url: "acl/white/list/sync.do",
            data: request
        }).success(function (resp) {
            if (resp.status == 0) {
                Notification.info({message: "success!", delay: 2000});
                $scope.refreshPlainAccessConfigs();
            } else {
                Notification.error({message: resp.errMsg, delay: 2000});
            }
        });
    }

    $scope.switchSecretKeyType = function (accessKey) {
        if ($scope.showSecretKeyType[accessKey].type == 'password') {
            $scope.showSecretKeyType[accessKey] = {type: 'text', action: 'HIDE'};
        } else {
            $scope.showSecretKeyType[accessKey] = {type: 'password', action: 'SHOW'};
        }
    }
}]);

module.controller('addAclAccountDialogController', ['$scope', 'ngDialog', '$http', 'Notification', function ($scope, ngDialog, $http, Notification) {
        $scope.addRequest = function (requestItem) {
            $http({
                method: "POST",
                url: "acl/add.do",
                data: requestItem
            }).success(function (resp) {
                if (resp.status == 0) {
                    Notification.info({message: "success!", delay: 2000});
                } else {
                    Notification.error({message: resp.errMsg, delay: 2000});
                }
            });
        }
    }]
);

module.controller('updateAclAccountDialogController', ['$scope', 'ngDialog', '$http', 'Notification', function ($scope, ngDialog, $http, Notification) {
        $scope.updateAclAccountRequest = function (requestItem) {
            $http({
                method: "POST",
                url: "acl/update.do",
                data: requestItem
            }).success(function (resp) {
                if (resp.status == 0) {
                    Notification.info({message: "success!", delay: 2000});
                } else {
                    Notification.error({message: resp.errMsg, delay: 2000});
                }
            });
        }
    }]
);

module.controller('addAclTopicDialogController', ['$scope', 'ngDialog', '$http', 'Notification', function ($scope, ngDialog, $http, Notification) {
        $scope.updateAclAccountRequest = function (requestItem) {
            if ((requestItem.deny && requestItem.sub) || (requestItem.deny && requestItem.pub)) {
                alert("Forbid deny && pub/sub.");
                return false;
            }
            if (!requestItem.topic) {
                alert("topic is null");
                return false;
            }
            //var request = requestItem.originalData;
            var originalData = $.extend(true, {}, requestItem.originalData);
            if (!originalData.topicPerms) {
                originalData.topicPerms = new Array();
            }
            var topicPerm = concatPerm(requestItem.topic, requestItem.pub ? 0x01 : 0, requestItem.sub ? 0x02 : 0, requestItem.deny ? 0x04 : 0);
            originalData.topicPerms.push(topicPerm);
            var request = {topicPerm: topicPerm, config: originalData};
            $http({
                method: "POST",
                url: "acl/topic/add.do",
                data: request
            }).success(function (resp) {
                if (resp.status == 0) {
                    Notification.info({message: "success!", delay: 2000});
                } else {
                    Notification.error({message: resp.errMsg, delay: 2000});
                }
            });
        }
    }]
);

module.controller('updateAclTopicDialogController', ['$scope', 'ngDialog', '$http', 'Notification', function ($scope, ngDialog, $http, Notification) {
        $scope.updateAclAccountRequest = function (requestItem) {
            if ((requestItem.deny && requestItem.sub) || (requestItem.deny && requestItem.pub)) {
                alert("Forbid deny && pub/sub.");
                return false;
            }
            var originalData = $.extend(true, {}, requestItem.originalData);
            if (!originalData.topicPerms) {
                originalData.topicPerms = new Array();
            }
            var topicPerm = concatPerm(requestItem.topic, requestItem.pub ? 0x01 : 0, requestItem.sub ? 0x02 : 0, requestItem.deny ? 0x04 : 0);

            for (var i = 0; i < originalData.topicPerms.length; i++) {
                if (originalData.topicPerms[i].split('=')[0] == requestItem.topic) {
                    originalData.topicPerms[i] = topicPerm;
                }
            }
            var request = {topicPerm: topicPerm, config: originalData};
            $http({
                method: "POST",
                url: "acl/topic/add.do",
                data: request
            }).success(function (resp) {
                if (resp.status == 0) {
                    Notification.info({message: "success!", delay: 2000});
                } else {
                    Notification.error({message: resp.errMsg, delay: 2000});
                }
            });
        }
    }]
);

module.controller('addAclGroupDialogController', ['$scope', 'ngDialog', '$http', 'Notification', function ($scope, ngDialog, $http, Notification) {
        $scope.updateAclAccountRequest = function (requestItem) {
            if ((requestItem.deny && requestItem.sub) || (requestItem.deny && requestItem.pub)) {
                alert("Forbid deny && pub/sub.");
                return false;
            }
            //var request = requestItem.originalData;
            var originalData = $.extend(true, {}, requestItem.originalData);
            if (!originalData.groupPerms) {
                originalData.groupPerms = new Array();
            }
            var groupPerm = concatPerm(requestItem.group, requestItem.pub ? 0x01 : 0, requestItem.sub ? 0x02 : 0, requestItem.deny ? 0x04 : 0);
            originalData.groupPerms.push(groupPerm);
            var request = {groupPerm: groupPerm, config: originalData};
            $http({
                method: "POST",
                url: "acl/group/add.do",
                data: request
            }).success(function (resp) {
                if (resp.status == 0) {
                    Notification.info({message: "success!", delay: 2000});
                } else {
                    Notification.error({message: resp.errMsg, delay: 2000});
                }
            });
        }
    }]
);

module.controller('updateAclGroupDialogController', ['$scope', 'ngDialog', '$http', 'Notification', function ($scope, ngDialog, $http, Notification) {
        $scope.updateAclAccountRequest = function (requestItem) {
            if ((requestItem.deny && requestItem.sub) || (requestItem.deny && requestItem.pub)) {
                alert("Forbid deny && pub/sub.");
                return false;
            }
            var originalData = $.extend(true, {}, requestItem.originalData);
            if (!originalData.groupPerms) {
                originalData.groupPerms = new Array();
            }
            var groupPerm = concatPerm(requestItem.group, requestItem.pub ? 0x01 : 0, requestItem.sub ? 0x02 : 0, requestItem.deny ? 0x04 : 0);

            for (var i = 0; i < originalData.groupPerms.length; i++) {
                if (originalData.groupPerms[i].split('=')[0] == requestItem.group) {
                    originalData.groupPerms[i] = groupPerm;
                }
            }
            var request = {groupPerm: groupPerm, config: originalData};
            $http({
                method: "POST",
                url: "acl/group/add.do",
                data: request
            }).success(function (resp) {
                if (resp.status == 0) {
                    Notification.info({message: "success!", delay: 2000});
                } else {
                    Notification.error({message: resp.errMsg, delay: 2000});
                }
            });
        }
    }]
);

/**
 *
 * pub: 0x01, sub: 0x02, deny: 0x04
 */
function concatPerm(name, pub, sub, deny) {
    var perm = '';

    switch (pub | sub | deny) {
        case 0x01:
            perm = 'PUB';
            break;
        case 0x02:
            perm = 'SUB';
            break;
        case 0x03:
            perm = 'PUB|SUB';
            break;
        case 0x04:
            perm = 'DENY';
            break;
        default:
            perm = 'DENY';
            break;
    }

    return name + '=' + perm;
}

module.controller('addWhiteListDialogController', ['$scope', 'ngDialog', '$http', 'Notification', function ($scope, ngDialog, $http, Notification) {
        $scope.addWhiteListRequest = function (requestItem) {
            $http({
                method: "POST",
                url: "acl/white/list/add.do",
                data: requestItem.split(',')
            }).success(function (resp) {
                if (resp.status == 0) {
                    Notification.info({message: "success!", delay: 2000});
                } else {
                    Notification.error({message: resp.errMsg, delay: 2000});
                }
            });
        }
    }]
);

module.controller('aclBelongItemDialogController', ['$scope', 'ngDialog', '$http', 'Notification', function ($scope, ngDialog, $http, Notification) {
        $scope.postBelongItemRequest = function (topicRequestItem) {
            topicRequestItem.type = 1
            $http({
                method: "POST",
                url: "acl/belong/item/add.do",
                data: topicRequestItem
            }).success(function (resp) {
                if (resp.status == 0) {
                    Notification.info({message: "success!", delay: 2000});
                } else {
                    Notification.error({message: resp.errMsg, delay: 2000});
                }
            });
        }
    }]
);
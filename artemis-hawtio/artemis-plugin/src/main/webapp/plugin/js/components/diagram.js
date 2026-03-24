/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 /// <reference path="tree.component.ts"/>
var Artemis;
(function (Artemis) {
    Artemis._module.component('artemisBrokerDiagram', {
        template:
            `<div class="container-topology">
                <h1>Broker Diagram
                    <button type="button" class="btn btn-link jvm-title-popover"
                              uib-popover-template="'diagram-instructions.html'" popover-placement="bottom-left"
                              popover-title="Instructions" popover-trigger="'outsideClick'">
                        <span class="pficon pficon-help"></span>
                    </button>
                </h1>
                <!-- Inhibit the context menu of pf-topology for the its items -->
                <style type="text/css">pf-topology .popup { visibility: hidden; }</style>
                <pf-topology items="$ctrl.data.items" relations="$ctrl.data.relations" kinds="$ctrl.kinds" icons="$ctrl.data.icons" nodes="$ctrl.nodes" item-selected="$ctrl.itemSelected(item)" search-text="searchText" show-labels="$ctrl.showLabels" tooltip-function="$ctrl.tooltip(node)" chart-rendered="$ctrl.chartRendered(vertices, added)">

                <label style="margin-right: 1em">Show labels:
                    <input type="checkbox" ng-model="$ctrl.showLabels">
                </label>
                 <label style="margin-right: 1em">Show addresses:
                    <input type="checkbox" ng-model="$ctrl.showAddresses">
                </label>
                <label style="margin-right: 1em">Show queues:
                    <input type="checkbox" ng-model="$ctrl.showQueues">
                </label>
                <label style="margin-right: 1em">Show internal addresses:
                    <input type="checkbox" ng-model="$ctrl.showInternalAddresses">
                </label>
                <label style="margin-right: 1em">Show internal queues:
                    <input type="checkbox" ng-model="$ctrl.showInternalQueues">
                </label>

                <label style="margin-right: 1em">Show Live Brokers:
                    <input type="checkbox" ng-model="$ctrl.showLiveBrokers">
                </label>
                <label style="margin-right: 1em">Show Backup Brokers:
                    <input type="checkbox" ng-model="$ctrl.showBackupBrokers">
                </label>
                <label style="margin-right: 1em">Show Connectors:
                    <input type="checkbox" ng-model="$ctrl.showConnectors">
                </label>
                <button type="submit" class="btn btn-primary"
                    ng-click="$ctrl.refresh()">Refresh
                </button>
            </div>
            <div ng-show="$ctrl.showAttributes">
                <pf-table-view
                    config="$ctrl.config"
                    columns="$ctrl.tableColumns"
                    items="$ctrl.attributes">
                </pf-table-view>
            </div>
            <script type="text/ng-template" id="diagram-instructions.html">
            <div>
                <p>
                    This page is a graphical representation of the cluster topology. It will show all the brokers in the cluster
                    as well as well as any Adresses and Queues on the broker the console is connected to.
                </p>
                <p>
                    It is possible to view the attributes of the addresses, queues and connected broker by left clicking
                    on each node.
                </p>
                </div>
            </script>
        `,
        controller: BrokerDiagramController
    })
    .name;


    function BrokerDiagramController($scope, workspace, jolokia, localStorage, artemisMessage, $location, $timeout, $filter) {
        Artemis.log.debug("loaded browse " + Artemis.browseQueueModule);
        var ctrl = this;
        ctrl.index = 0;
        ctrl.showLabels = false;
        ctrl.showAddresses = true;
        ctrl.showQueues = true;
        ctrl.showInternalAddresses = false;
        ctrl.showInternalQueues = false;
        ctrl.showLiveBrokers = true;
        ctrl.showBackupBrokers = true;
        ctrl.showConnectors = true;
        ctrl.hiddenRelations = [];
        function updateAddressKind() {
            if(ctrl.kinds.Address && !ctrl.showAddresses) {
               delete ctrl.kinds.Address;
            } else if (!ctrl.kinds.Address && ctrl.showAddresses) {
                ctrl.kinds.Address = true;
            }
        }
        $scope.$watch('$ctrl.showAddresses', function () {
            updateAddressKind();
        });
        function updateQueueKind() {
            if(ctrl.kinds.Queue && !ctrl.showQueues) {
               delete ctrl.kinds.Queue;
            } else if (!ctrl.kinds.Queue && ctrl.showQueues) {
                ctrl.kinds.Queue = true;
            }
        }
        $scope.$watch('$ctrl.showQueues', function () {
            updateQueueKind();
        });
        function updateInternalAddressKind() {
            if(ctrl.kinds.InternalAddress && !ctrl.showInternalAddresses) {
               delete ctrl.kinds.InternalAddress;
            } else if (!ctrl.kinds.InternalAddress && ctrl.showInternalAddresses) {
                ctrl.kinds.InternalAddress = true;
            }
        }
        $scope.$watch('$ctrl.showInternalAddresses', function () {
            updateInternalAddressKind();
        });
        function updateInternalQueueKind() {
            if(ctrl.kinds.InternalQueue && !ctrl.showInternalQueues) {
               delete ctrl.kinds.InternalQueue;
            } else if (!ctrl.kinds.InternalQueue && ctrl.showInternalQueues) {
                ctrl.kinds.InternalQueue = true;
            }
        }
        $scope.$watch('$ctrl.showInternalQueues', function () {
            updateInternalQueueKind();
        });
        function updateLiveBrokerKind() {
            if(ctrl.kinds.ThisBroker && !ctrl.showLiveBrokers) {
               delete ctrl.kinds.ThisBroker;
            } else if (!ctrl.kinds.ThisBroker && ctrl.showLiveBrokers) {
                ctrl.kinds.ThisBroker = true;
            }
            if(ctrl.kinds.MasterBroker && !ctrl.showLiveBrokers) {
               delete ctrl.kinds.MasterBroker;
            } else if (!ctrl.kinds.MasterBroker && ctrl.showLiveBrokers) {
                ctrl.kinds.MasterBroker = true;
            }
        }
        $scope.$watch('$ctrl.showLiveBrokers', function () {
            updateLiveBrokerKind();
        });
        function updateBackupBrokerKind() {
            if(ctrl.kinds.SlaveBroker && !ctrl.showBackupBrokers) {
               delete ctrl.kinds.SlaveBroker;
            } else if (!ctrl.kinds.SlaveBroker && ctrl.showBackupBrokers) {
                ctrl.kinds.SlaveBroker = true;
            }
        }
        $scope.$watch('$ctrl.showBackupBrokers', function () {
            updateBackupBrokerKind();
        });
        function updateConnectors() {
            if(!ctrl.showConnectors) {
                ctrl.data.relations = [];
            } else {
                ctrl.data.relations = ctrl.hiddenRelations;
            }
        }
        $scope.$watch('$ctrl.showConnectors', function () {
            updateConnectors();
        });
        ctrl.datasets = [];
        //icons can be found at https://www.patternfly.org/v3/styles/icons/index.html
        ctrl.serverIcon = "\ue90d";
        Artemis.log.debug(ctrl.serverIcon);
        ctrl.addressIcon = "";//\ue91a";
        ctrl.queueIcon = "";//\ue90a";
        ctrl.icons = {
            "ThisBroker": {
              "type": "glyph",
              "icon": ctrl.serverIcon,
              "background": "#456BD9",
              "fontfamily": "PatternFlyIcons-webfont"
            },
            "MasterBroker": {
              "type": "glyph",
              "icon": ctrl.serverIcon,
              "fontfamily": "PatternFlyIcons-webfont"
            },
            "SlaveBroker": {
              "type": "glyph",
              "icon": ctrl.serverIcon,
              "fontfamily": "PatternFlyIcons-webfont"
            },
            "Address": {
                "type": "glyph",
                "icon": ctrl.addressIcon,
                "fontfamily": "PatternFlyIcons-webfont"
            },
            "InternalAddress": {
                "type": "glyph",
                "icon": ctrl.addressIcon,
                "fontfamily": "PatternFlyIcons-webfont"
            },
            "Queue": {
                "type": "glyph",
                "background": "#456BD9",
                "icon": ctrl.queueIcon,
                "fontfamily": "PatternFlyIcons-webfont"
            },
            "InternalQueue": {
                "type": "glyph",
                "background": "#456BD9",
                "icon": ctrl.queueIcon,
                "fontfamily": "PatternFlyIcons-webfont"
            }
        };

        load();
        ctrl.hiddenRelations = ctrl.relations;
        function load() {
            ctrl.items = {};

            ctrl.relations = [];

            ctrl.datasets.push({
                "items": ctrl.items,
                "relations": ctrl.relations,
                "icons": ctrl.icons
            });

            Artemis.log.debug("index " + ctrl.index);

            ctrl.data = ctrl.datasets[ctrl.index];

            ctrl.data.url = "fooBar";

            ctrl.kinds = {
                "ThisBroker": true,
                "MasterBroker": true,
                "SlaveBroker": true,
                "Address": true,
                "Queue": true
            };

            ctrl.icons = ctrl.data.icons;

            ctrl.nodes = {
                "ThisBroker": {
                     "name": "ThisBroker",
                     "title": "hello",
                     "enabled": true,
                     "radius": 28,
                     "textX": 0,
                     "textY": 5,
                     "height": 30,
                     "width": 30,
                     "icon": ctrl.icons["ThisBroker"].icon,
                     "fontFamily": ctrl.icons["ThisBroker"].fontfamily
                   },
                "MasterBroker": {
                    "name": "MasterBroker",
                    "enabled": true,
                    "radius": 28,
                    "textX": 0,
                    "textY": 5,
                    "height": 30,
                    "width": 30,
                    "icon": ctrl.icons["MasterBroker"].icon,
                    "fontFamily": ctrl.icons["MasterBroker"].fontfamily
                },
                "SlaveBroker": {
                    "name": "SlaveBroker",
                    "enabled": true,
                    "radius": 28,
                    "textX": 0,
                    "textY": 5,
                    "height": 30,
                    "icon": ctrl.icons["SlaveBroker"].icon,
                    "fontFamily": ctrl.icons["SlaveBroker"].fontfamily
                },
                "Address": {
                    "name": "Address",
                    "enabled": ctrl.showDestinations,
                    "radius": 16,
                    "textX": 0,
                    "textY": 5,
                    "height": 18,
                    "width": 18,
                    "icon": ctrl.icons["Address"].icon,
                    "fontFamily": ctrl.icons["Address"].fontfamily
                },
                "Queue": {
                    "name": "Queue",
                    "enabled": ctrl.showDestinations,
                    "radius": 16,
                    "textX": 0,
                    "textY": 5,
                    "height": 18,
                    "width": 18,
                    "icon": ctrl.icons["Queue"].icon,
                    "fontFamily": ctrl.icons["Queue"].fontfamily
                }
            };

            ctrl.tableColumns = [
                { header: 'attribute', itemField: 'attribute' },
                { header: 'value', itemField: 'value' }
            ];
            ctrl.attributes = [];
            ctrl.config = {
                selectionMatchProp: 'attribute',
                showCheckboxes: false
            };
            ctrl.showAttributes = false;

            updateAddressKind();
            updateQueueKind();
            updateInternalAddressKind();
            updateInternalQueueKind();
            updateLiveBrokerKind();
            updateBackupBrokerKind();
            updateConnectors();

            loadThisBroker();
            Core.$apply($scope);
        }
        ctrl.itemSelected = function(item) {
            ctrl.showAttributes = false;
            ctrl.attributes = [];
            if (!item || !item.mbean) {
                Core.$apply($scope);
                return;
            }
            var atts = jolokia.request({ type: "read", mbean: item.mbean}, {method: "post"});
            var val = atts.value;
            if (val) {
                angular.forEach(val, function (value, key) {
                    attribute = {
                        "attribute": key,
                        "value": value
                    }
                   ctrl.attributes.push(attribute);
                });
            }
            ctrl.showAttributes = true;
            Core.$apply($scope);
        }
        ctrl.tooltip = function (node) {
            var status = [
                'Name: ' + node.item.name,
                'Type: ' + node.item.brokerKind
            ];
            return status;
        }
        ctrl.chartRendered = function (vertices, added) {
            // Inhibit the dblclick handler of pf-topology for the its items.
            added.each(function (d) { d.url = "javascript:void(0)"; });
        }
        ctrl.refresh = function () {
            ctrl.datasets = [];
            load();
        }

        function loadThisBroker() {
            var mBean = Artemis.getBrokerMBean(workspace, jolokia);
            var atts = jolokia.request({ type: "read", mbean: mBean}, {method: "post"});
            var val = atts.value;
            var details = Core.parseMBean(mBean);

            if (details) {
                var properties = details['attributes'];
                Artemis.log.debug("Got broker: " + mBean + " properties: " + angular.toJson(properties, true));
                if (properties) {
                    var brokerAddress = properties["broker"] || "unknown";
                    var brokerName = artemisJmxDomain + ":broker=" + brokerAddress;
                    var backupRes = jolokia.request({ type: "read", mbean: mBean, attribute: "Backup"}, {method: "get"});

                    var isBackup = backupRes.value;
                    var nodeId = val["NodeID"];
                    var response = jolokia.request({ type: 'exec', mbean: mBean, operation: 'listNetworkTopology()' }, Core.onSuccess(null));
                    var responseValue = response.value;
                    var remoteBrokers = angular.fromJson(responseValue);
                    var thisBroker = remoteBrokers.find(broker => broker.nodeID == nodeId);
                    if(!thisBroker) {
                        if(isBackup) {
                            thisBroker = {
                                backup: "broker"
                            };
                        } else {
                            thisBroker = {
                                live: "broker"
                            };
                        }
                    }
                    if (thisBroker.live) {
                        ctrl.items[thisBroker.live] = {
                            "name": thisBroker.live,
                            "kind": "ThisBroker",
                            "brokerKind": "master",
                            "status": "broker",
                            "display_kind": "Server",
                            "mbean": mBean
                        }
                    }
                    if (thisBroker.backup) {
                        ctrl.items[thisBroker.backup] = {
                            "name": thisBroker.backup,
                            "kind": "SlaveBroker",
                            "brokerKind": "slave",
                            "status": "broker",
                            "display_kind": "Server"
                        };
                        if (thisBroker.live) {
                            ctrl.relations.push({
                                "source": thisBroker.live,
                                "target": thisBroker.backup
                            });
                        }
                    }
                    createAddresses(mBean, thisBroker.live)
                }

                angular.forEach(remoteBrokers, function (remoteBroker) {
                    if (nodeId != remoteBroker.nodeID) {
                       if (remoteBroker.live) {
                          ctrl.items[remoteBroker.live] = {
                              "name": remoteBroker.live,
                              "kind": "MasterBroker",
                              "brokerKind": "master",
                              "status": "broker",
                              "display_kind": "Server"
                          };
                          //if we arent a backup then connect to it as we are in the cluster
                          if(!isBackup) {}
                              ctrl.relations.push({
                                  "source": thisBroker.live,
                                  "target": remoteBroker.live
                              });
                          }
                          if (remoteBroker.backup) {
                              ctrl.items[remoteBroker.backup] = {
                                  "name": remoteBroker.backup,
                                  "kind": "SlaveBroker",
                                  "brokerKind": "slave",
                                  "status": "broker",
                                  "display_kind": "Server"
                              };
                              ctrl.relations.push({
                                 "source": remoteBroker.backup,
                                 "target": remoteBroker.live
                              });
                          }
                    }
                });
            }
        }

        function createAddresses(brokerMBean, brokerId) {
           jolokia.search(brokerMBean + ",component=addresses,*", Core.onSuccess(function (response) {
              angular.forEach(response, function (objectName) {
                 var details = Core.parseMBean(objectName);
                 if (details) {
                    var properties = details['attributes'];
                    if (properties) {
                        if (!properties.subcomponent) {

                           Artemis.log.debug("Got Address: " + objectName + " properties: " + angular.toJson(properties, true));
                           addressKind = properties.address.startsWith("$", 1) || properties.address.startsWith("notif", 1) ? "InternalAddress" : "Address";
                           ctrl.items[properties.address] = {
                               "name": properties.address,
                               "kind": addressKind,
                               "brokerKind": "address",
                               "status": "Valid",
                               "display_kind": "Server",
                               "mbean": objectName
                           }
                           ctrl.relations.push({
                               "source": brokerId,
                               "target": properties.address
                           });
                        }
                        if (properties.queue) {
                            Artemis.log.debug("Got Queue: " + objectName + " properties: " + angular.toJson(properties, true));
                            queueKind = properties.queue.startsWith("$", 1) || properties.queue.startsWith("notif", 1) ? "InternalQueue" : "Queue";
                            ctrl.items["queue." + properties.queue] = {
                               "name": properties.queue,
                               "kind": queueKind,
                               "brokerKind": "queue",
                               "status": "Valid",
                               "display_kind": "Service",
                               "mbean": objectName
                           }
                           ctrl.relations.push({
                               "source": properties.address,
                               "target": "queue." + properties.queue
                           });
                        }
                    }
                 }
              });
           }));
        }
    }
    BrokerDiagramController.$inject = ['$scope', 'workspace', 'jolokia', 'localStorage', 'artemisMessage', '$location', '$timeout', '$filter'];

})(Artemis || (Artemis = {}));

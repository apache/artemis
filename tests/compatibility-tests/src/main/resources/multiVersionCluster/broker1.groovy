package multiVersionCluster
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.activemq.artemis.api.core.QueueConfiguration
import org.apache.activemq.artemis.api.core.RoutingType
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl
import org.apache.activemq.artemis.core.server.JournalType
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy
import org.apache.activemq.artemis.core.settings.impl.AddressSettings
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType

String folder = arg[0];
String id = arg[1];
String port = arg[2];
String otherPort = arg[3]

configuration = new ConfigurationImpl();
configuration.setJournalType(JournalType.NIO);
configuration.setBrokerInstance(new File(folder + "/" + id));
configuration.addAcceptorConfiguration("artemis", "tcp://localhost:" + port);
configuration.addConnectorConfiguration("local", "tcp://localhost:" + port);
configuration.addConnectorConfiguration("other", "tcp://localhost:" + otherPort);
configuration.setSecurityEnabled(false);
configuration.setPersistenceEnabled(true);

if (configuration.metaClass.hasMetaProperty("globalMaxMessages")) {
    configuration.globalMaxMessages = 10
} else {
    configuration.globalMaxSize = 10 * 1024
}

configuration.addAddressSetting("#", new AddressSettings()
    .setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE)
    .setRedistributionDelay(0).setMaxSizeBytes(100 * 1024 * 1024).setMaxSizeMessages(10_000));

// Configure cluster connection
ClusterConnectionConfiguration clusterConfiguration = new ClusterConnectionConfiguration()
    .setName("my-cluster")
    .setConnectorName("local")
    .setRetryInterval(500)
    .setMessageLoadBalancingType(MessageLoadBalancingType.ON_DEMAND)
    .setStaticConnectors(["other"])

configuration.addClusterConfiguration(clusterConfiguration)

configuration.addAddressConfiguration(new CoreAddressConfiguration().setName("MultiVersionClusterTestQueue"));
configuration.addQueueConfiguration(new QueueConfiguration("MultiVersionClusterTestQueue")
    .setAddress("MultiVersionClusterTestQueue")
    .setRoutingType(RoutingType.ANYCAST));

theBroker1 = new EmbeddedActiveMQ();
theBroker1.setConfiguration(configuration);
theBroker1.start();

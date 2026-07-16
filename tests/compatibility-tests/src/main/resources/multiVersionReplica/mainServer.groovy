package multiVersionReplica
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
import org.apache.activemq.artemis.core.config.ha.ReplicatedPolicyConfiguration
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration
import org.apache.activemq.artemis.core.security.Role

// starts an artemis server
import org.apache.activemq.artemis.core.server.JournalType
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy
import org.apache.activemq.artemis.core.settings.impl.AddressSettings
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager
import org.apache.activemq.artemis.spi.core.security.jaas.InVMLoginModule

String folder = arg[0];
String id = arg[1];
String port = arg[2];
String backupPort = arg[3]
boolean security = Boolean.valueOf(arg[4]);

configuration = new ConfigurationImpl();
configuration.setJournalType(JournalType.NIO);
configuration.setBrokerInstance(new File(folder + "/" + id));
configuration.addAcceptorConfiguration("artemis", "tcp://localhost:" + port);
configuration.addConnectorConfiguration("local", "tcp://localhost:" + port);
configuration.setSecurityEnabled(security);
if (security) {
    configuration.setClusterUser("cluster")
    configuration.setClusterPassword("cluster")
}
configuration.setPersistenceEnabled(true);

if (configuration.metaClass.hasMetaProperty("globalMaxMessages")) {
    configuration.globalMaxMessages = 10
} else {
    configuration.globalMaxSize = 10 * 1024
}

configuration.setHAPolicyConfiguration(new ReplicatedPolicyConfiguration().setClusterName("main"))
configuration.addAddressesSetting("#", new AddressSettings().setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE));


ClusterConnectionConfiguration backupConfiguration = new ClusterConnectionConfiguration(new URI("static://(tcp://localhost:" + backupPort + ")")).setName("main").setConnectorName("local")
configuration.addClusterConfiguration(backupConfiguration)

if (security) {
    configuration.putSecurityRoles("#", new HashSet<Role>(Arrays.asList(new Role("amq", true, true, true, true, true, true, true, true))))
}

configuration.addAddressConfiguration(new CoreAddressConfiguration().setName("MultiVersionReplicaTestQueue"));
configuration.addQueueConfiguration(new QueueConfiguration("MultiVersionReplicaTestQueue").setAddress("MultiVersionReplicaTestQueue").setRoutingType(RoutingType.ANYCAST));

theMainServer = new EmbeddedActiveMQ();
theMainServer.setConfiguration(configuration);

if (security) {
    SecurityConfiguration securityConfiguration = new SecurityConfiguration()
    securityConfiguration.addUser("admin", "admin")
    securityConfiguration.addRole("admin", "amq")
    securityConfiguration.setDefaultUser("admin")
    ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager(InVMLoginModule.class.getName(), securityConfiguration)
    theMainServer.setSecurityManager(securityManager);
}

theMainServer.start();

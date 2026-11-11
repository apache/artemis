package multiVersionCoreFederation
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
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration
import org.apache.activemq.artemis.core.config.FederationConfiguration
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration
import org.apache.activemq.artemis.core.config.federation.FederationQueuePolicyConfiguration
import org.apache.activemq.artemis.core.config.federation.FederationUpstreamConfiguration
import org.apache.activemq.artemis.core.security.Role
import org.apache.activemq.artemis.core.server.JournalType
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy
import org.apache.activemq.artemis.core.settings.impl.AddressSettings
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager
import org.apache.activemq.artemis.spi.core.security.jaas.InVMLoginModule

String folder = arg[0];
boolean security = Boolean.valueOf(arg[1]);

id = 1;

configuration = new ConfigurationImpl();
configuration.setJournalType(JournalType.NIO);
configuration.setBrokerInstance(new File(folder + "/" + id));
configuration.addAcceptorConfiguration("artemis", "tcp://localhost:61001");
configuration.addConnectorConfiguration("broker1", "tcp://localhost:61000");
configuration.setSecurityEnabled(security);
configuration.setPersistenceEnabled(true);

if (configuration.metaClass.hasMetaProperty("globalMaxMessages")) {
    configuration.globalMaxMessages = 10
} else {
    configuration.globalMaxSize = 10 * 1024
}

configuration.addAddressSetting("#", new AddressSettings()
    .setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE).setMaxSizeMessages(100_000).setMaxSizeMessages(100 * 1024 * 1024));

// Configure core federation upstream to broker1
FederationQueuePolicyConfiguration queuePolicy = new FederationQueuePolicyConfiguration()
queuePolicy.setName("queue-federation-policy")
queuePolicy.addInclude(new FederationQueuePolicyConfiguration.Matcher()
    .setQueueMatch("MultiVersionCoreFederationTestQueue")
    .setAddressMatch("MultiVersionCoreFederationTestQueue"))

FederationUpstreamConfiguration upstream = new FederationUpstreamConfiguration()
upstream.setName("upstream-broker1")
upstream.getConnectionConfiguration().setStaticConnectors(["broker1"])
upstream.getConnectionConfiguration().setCircuitBreakerTimeout(-1)
upstream.addPolicyRef("queue-federation-policy")

if (security) {
    upstream.getConnectionConfiguration().setUsername("admin")
    upstream.getConnectionConfiguration().setPassword("admin")
}

FederationConfiguration federationConfig = new FederationConfiguration()
federationConfig.setName("default")
federationConfig.addQueuePolicy(queuePolicy)
federationConfig.addUpstreamConfiguration(upstream)

configuration.getFederationConfigurations().add(federationConfig)

if (security) {
    configuration.putSecurityRoles("#", new HashSet<Role>(Arrays.asList(new Role("amq", true, true, true, true, true, true, true, true))))
}

configuration.addAddressConfiguration(new CoreAddressConfiguration().setName("MultiVersionCoreFederationTestQueue"));
configuration.addQueueConfiguration(new QueueConfiguration("MultiVersionCoreFederationTestQueue")
    .setAddress("MultiVersionCoreFederationTestQueue")
    .setRoutingType(RoutingType.ANYCAST));

theBroker2 = new EmbeddedActiveMQ();
theBroker2.setConfiguration(configuration);

if (security) {
    SecurityConfiguration securityConfiguration = new SecurityConfiguration()
    securityConfiguration.addUser("admin", "admin")
    securityConfiguration.addRole("admin", "amq")
    securityConfiguration.setDefaultUser("admin")
    ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager(InVMLoginModule.class.getName(), securityConfiguration)
    theBroker2.setSecurityManager(securityManager);
}

theBroker2.start();

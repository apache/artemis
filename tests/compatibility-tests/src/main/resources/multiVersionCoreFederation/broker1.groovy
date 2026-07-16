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
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration
import org.apache.activemq.artemis.core.security.Role
import org.apache.activemq.artemis.core.server.JournalType
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy
import org.apache.activemq.artemis.core.settings.impl.AddressSettings
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager
import org.apache.activemq.artemis.spi.core.security.jaas.InVMLoginModule

String folder = arg[0];
boolean security = Boolean.valueOf(arg[1]);

id = 0;

configuration = new ConfigurationImpl();
configuration.setJournalType(JournalType.NIO);
configuration.setBrokerInstance(new File(folder + "/" + id));
configuration.addAcceptorConfiguration("artemis", "tcp://localhost:61000");
configuration.setSecurityEnabled(security);
configuration.setPersistenceEnabled(true);

configuration.addAddressSetting("#", new AddressSettings()
        .setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE).setMaxSizeMessages(100_000).setMaxSizeMessages(100 * 1024 * 1024));

if (security) {
    configuration.putSecurityRoles("#", new HashSet<Role>(Arrays.asList(new Role("amq", true, true, true, true, true, true, true, true))))
}

configuration.addAddressConfiguration(new CoreAddressConfiguration().setName("MultiVersionCoreFederationTestQueue"));
configuration.addQueueConfiguration(new QueueConfiguration("MultiVersionCoreFederationTestQueue")
    .setAddress("MultiVersionCoreFederationTestQueue")
    .setRoutingType(RoutingType.ANYCAST));

theBroker1 = new EmbeddedActiveMQ();
theBroker1.setConfiguration(configuration);

if (security) {
    SecurityConfiguration securityConfiguration = new SecurityConfiguration()
    securityConfiguration.addUser("admin", "admin")
    securityConfiguration.addRole("admin", "amq")
    securityConfiguration.setDefaultUser("admin")
    ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager(InVMLoginModule.class.getName(), securityConfiguration)
    theBroker1.setSecurityManager(securityManager);
}

theBroker1.start();

package multiVersionFederation
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
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederationQueuePolicyElement
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPFederatedBrokerConnectionElement
import org.apache.activemq.artemis.core.server.JournalType
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy
import org.apache.activemq.artemis.core.settings.impl.AddressSettings

String folder = arg[0];


id = 0;

configuration = new ConfigurationImpl();
configuration.setJournalType(JournalType.NIO);
configuration.setBrokerInstance(new File(folder + "/" + id));
configuration.addAcceptorConfiguration("amqp", "tcp://localhost:" + 61000);
configuration.setSecurityEnabled(false);
configuration.setPersistenceEnabled(true);

configuration.addAddressSetting("#", new AddressSettings()
        .setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE).setMaxSizeMessages(100_000).setMaxSizeMessages(100 * 1024 * 1024));


// Configure AMQP broker connection with federation
AMQPBrokerConnectConfiguration amqpConnection = new AMQPBrokerConnectConfiguration()
    .setName("federation-to-broker2")
    .setUri("tcp://localhost:61001")
    .setReconnectAttempts(-1)
    .setRetryInterval(1000)

// Configure federation for queues
AMQPFederatedBrokerConnectionElement federation = new AMQPFederatedBrokerConnectionElement();
federation.setName("broker2-federation")

AMQPFederationQueuePolicyElement queuePolicy = new AMQPFederationQueuePolicyElement()
    .setName("queue-federation-policy")
    .addToIncludes("MultiVersionFederationTestQueue", "MultiVersionFederationTestQueue")

federation.addRemoteQueuePolicy(queuePolicy)
amqpConnection.addFederation(federation)

configuration.addAMQPConnection(amqpConnection)

configuration.addAddressConfiguration(new CoreAddressConfiguration().setName("MultiVersionFederationTestQueue"));
configuration.addQueueConfiguration(new QueueConfiguration("MultiVersionFederationTestQueue")
    .setAddress("MultiVersionFederationTestQueue")
    .setRoutingType(RoutingType.ANYCAST));

theBroker1 = new EmbeddedActiveMQ();
theBroker1.setConfiguration(configuration);
theBroker1.start();

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
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPMirrorBrokerConnectionElement
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl

// starts an artemis server
import org.apache.activemq.artemis.core.server.JournalType
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ

String folder = arg[0];
String id = arg[1];
String queueName = arg[2]
String topicName = arg[3]
boolean useDual = Boolean.parseBoolean(arg[4])

configuration = new ConfigurationImpl();
configuration.setJournalType(JournalType.NIO);
configuration.setBrokerInstance(new File(folder + "/" + id));
configuration.addAcceptorConfiguration("artemis", "tcp://localhost:61617");
configuration.setSecurityEnabled(false);
configuration.setPersistenceEnabled(true);

configuration.addAddressConfiguration(new CoreAddressConfiguration().setName(queueName).addRoutingType(RoutingType.ANYCAST));
configuration.addQueueConfiguration(new QueueConfiguration(queueName).setAddress(queueName).setRoutingType(RoutingType.ANYCAST));
configuration.addAddressConfiguration(new CoreAddressConfiguration().setName(topicName).addRoutingType(RoutingType.MULTICAST));

if (useDual) {
    try {
        AMQPBrokerConnectConfiguration connection = new AMQPBrokerConnectConfiguration("mirror", "tcp://localhost:61616").setReconnectAttempts(-1).setRetryInterval(100);
        AMQPMirrorBrokerConnectionElement replication = new AMQPMirrorBrokerConnectionElement().setDurable(true).setSync(false).setMessageAcknowledgements(true)
        connection.addElement(replication);
        configuration.addAMQPConnection(connection);
    } catch (Throwable ignored) {
    }
}


theBackupServer = new EmbeddedActiveMQ();
theBackupServer.setConfiguration(configuration);
theBackupServer.start();

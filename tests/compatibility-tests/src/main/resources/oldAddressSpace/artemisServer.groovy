package oldAddressSpace

import org.apache.activemq.artemis.api.core.RoutingType
import org.apache.activemq.artemis.api.core.SimpleString
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl

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

// starts an artemis server
import org.apache.activemq.artemis.core.server.JournalType
import org.apache.activemq.artemis.core.server.impl.AddressInfo
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy
import org.apache.activemq.artemis.core.settings.impl.AddressSettings
import org.apache.activemq.artemis.jms.server.config.impl.JMSConfigurationImpl
import org.apache.activemq.artemis.jms.server.embedded.EmbeddedJMS

String folder = arg[0];
String type = arg[1]
String id = "server";

String queueName = "myQueue";
String queueAddress = "jms.queue.myQueue";
String topicAddress = "jms.topic.myTopic";

configuration = new ConfigurationImpl();
configuration.setJournalType(JournalType.NIO);
configuration.setBrokerInstance(new File(folder + "/" + id));
configuration.addAcceptorConfiguration("artemis", "tcp://0.0.0.0:61616");
configuration.setSecurityEnabled(false);
configuration.setPersistenceEnabled(false);

AddressSettings addressSettings = new AddressSettings();
addressSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.BLOCK)
        .setMaxSizeBytes(10 * 1024)
        .setPageSizeBytes(1024)
        .setDeadLetterAddress(SimpleString.of("DLA"))
        .setExpiryAddress(SimpleString.of("Expiry"));

if (!(type.startsWith("ARTEMIS-1") || type.startsWith("HORNETQ"))) {
    addressSettings.setAutoCreateAddresses(false);
    addressSettings.setAutoCreateQueues(false);
}
configuration.addAddressSetting("#", addressSettings);

addressSettings = new AddressSettings();
addressSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE)
        .setMaxSizeBytes(1024 * 1024 * 1024)
        .setPageSizeBytes(1024)
        .setDeadLetterAddress(SimpleString.of("DLA"))
        .setExpiryAddress(SimpleString.of("Expiry"));

if (!(type.startsWith("ARTEMIS-1") || type.startsWith("HORNETQ"))) {
    addressSettings.setAutoCreateAddresses(false);
    addressSettings.setAutoCreateQueues(false);
}
configuration.addAddressSetting("jms.#", addressSettings);

// if the client is using the wrong address, it will wrongly block

jmsConfiguration = new JMSConfigurationImpl();

server = new EmbeddedJMS();
server.setConfiguration(configuration);
server.setJmsConfiguration(jmsConfiguration);
server.start();

if (type.startsWith("ARTEMIS-1") || type.startsWith("HORNETQ")) {
    server.getJMSServerManager().createQueue(true, queueName, null, true, null);
} else {
    server.getActiveMQServer().addAddressInfo(new AddressInfo(SimpleString.of(queueAddress), RoutingType.ANYCAST));
    server.getActiveMQServer().createQueue(SimpleString.of(queueAddress), RoutingType.ANYCAST, SimpleString.of(queueAddress), null, true, false);

    server.getActiveMQServer().addAddressInfo(new AddressInfo(SimpleString.of(topicAddress), RoutingType.MULTICAST));
}

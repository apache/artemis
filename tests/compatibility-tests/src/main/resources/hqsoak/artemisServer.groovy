package servers

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
String id = arg[1];
String type = arg[2];
String globalMaxSize = arg[5];

configuration = new ConfigurationImpl();
configuration.setJournalType(JournalType.NIO);
configuration.setBrokerInstance(new File(folder + "/" + id));
configuration.addAcceptorConfiguration("artemis", "tcp://0.0.0.0:61616?anycastPrefix=jms.queue.&multicastPrefix=jms.topic.");
configuration.setSecurityEnabled(false);
configuration.setPersistenceEnabled(true);
try {
    if (!type.startsWith("ARTEMIS-1")) {
        configuration.addAddressSetting("#", new AddressSettings().setAutoCreateAddresses(false).setDeadLetterAddress(SimpleString.of("DLQ"))
                .setExpiryAddress(SimpleString.of("EXP")));
        if (globalMaxSize != null) {
            configuration.getAddressSettings().get("#").setPageSizeBytes(Long.parseLong(globalMaxSize)).setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE)
            configuration.setGlobalMaxSize(Long.parseLong(globalMaxSize));
        }
    }
} catch (Throwable e) {
    // need to ignore this for 1.4
    e.printStackTrace();
}

jmsConfiguration = new JMSConfigurationImpl();

// used here even though it's deprecated to be compatible with older versions of the broker
server = new EmbeddedJMS();
server.setConfiguration(configuration);
server.setJmsConfiguration(jmsConfiguration);
server.start();

AddressInfo info = new AddressInfo(SimpleString.of("topic")).setAutoCreated(false).addRoutingType(RoutingType.MULTICAST);
server.activeMQServer.addAddressInfo(info);


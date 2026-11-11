package servers

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
configuration.setPersistenceEnabled(persistent);
try {
    if (!type.startsWith("ARTEMIS-1")) {
        configuration.addAddressesSetting("#", new AddressSettings().setAutoCreateAddresses(true));
        if (globalMaxSize != null) {
            configuration.getAddressesSettings().get("#").setPageSizeBytes((int)Long.parseLong(globalMaxSize)).setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE)
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

server.getJMSServerManager().createTopic(true, "topic");
server.getJMSServerManager().createQueue(true, "queue", null, true);

if (setAddressSettings) {

    // this is to force records that will have pittfals between versions
    server.getJMSServerManager().getActiveMQServer().getActiveMQServerControl().
            addAddressSettings("ad1", //@Parameter(desc = "an address match", name = "addressMatch") String addressMatch,
                    "dla", // @Parameter(desc = "the dead letter address setting", name = "DLA") String DLA,
                    "exp", //@Parameter(desc = "the expiry address setting", name = "expiryAddress") String expiryAddress,
                    0l, //@Parameter(desc = "the expiry delay setting", name = "expiryDelay") long expiryDelay,
                    false, //@Parameter(desc = "are any queues created for this address a last value queue", name = "lastValueQueue") boolean lastValueQueue,
                    1, //@Parameter(desc = "the delivery attempts", name = "deliveryAttempts") int deliveryAttempts,
                    10 * 1024 * 1024, //@Parameter(desc = "the max size in bytes", name = "maxSizeBytes") long maxSizeBytes,
                    1024 * 1024, //@Parameter(desc = "the page size in bytes", name = "pageSizeBytes") int pageSizeBytes,
                    3, //@Parameter(desc = "the max number of pages in the soft memory cache", name = "pageMaxCacheSize") int pageMaxCacheSize,
                    0l, //@Parameter(desc = "the redelivery delay", name = "redeliveryDelay") long redeliveryDelay,
                    0, //@Parameter(desc = "the redelivery delay multiplier", name = "redeliveryMultiplier") double redeliveryMultiplier,
                    0, //@Parameter(desc = "the maximum redelivery delay", name = "maxRedeliveryDelay") long maxRedeliveryDelay,
                    0, //@Parameter(desc = "the redistribution delay", name = "redistributionDelay") long redistributionDelay,
                    false, //@Parameter(desc = "do we send to the DLA when there is no where to route the message", name = "sendToDLAOnNoRoute") boolean sendToDLAOnNoRoute,
                    "BLOCK", //@Parameter(desc = "the policy to use when the address is full", name = "addressFullMessagePolicy") String addressFullMessagePolicy,
                    1000, //@Parameter(desc = "when a consumer falls below this threshold in terms of messages consumed per second it will be considered 'slow'", name = "slowConsumerThreshold") long slowConsumerThreshold,
                    1000, //@Parameter(desc = "how often (in seconds) to check for slow consumers", name = "slowConsumerCheckPeriod") long slowConsumerCheckPeriod,
                    "NOTIFY", //@Parameter(desc = "the policy to use when a slow consumer is detected", name = "slowConsumerPolicy") String slowConsumerPolicy,
                    true, //@Parameter(desc = "allow queues to be created automatically", name = "autoCreateJmsQueues") boolean autoCreateJmsQueues,
                    true, // @Parameter(desc = "allow auto-created queues to be deleted automatically", name = "autoDeleteJmsQueues") boolean autoDeleteJmsQueues,
                    true, //@Parameter(desc = "allow topics to be created automatically", name = "autoCreateJmsTopics") boolean autoCreateJmsTopics,
                    true) //@Parameter(desc = "allow auto-created topics to be deleted automatically", name = "autoDeleteJmsTopics") boolean autoDeleteJmsTopics) throws Exception;
}

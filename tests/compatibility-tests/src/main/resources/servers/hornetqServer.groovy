package servers
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

// starts a hornetq server

import org.hornetq.api.core.TransportConfiguration
import org.hornetq.core.config.impl.ConfigurationImpl
import org.hornetq.core.remoting.impl.netty.NettyAcceptorFactory
import org.hornetq.core.remoting.impl.netty.TransportConstants
import org.hornetq.jms.server.config.impl.JMSConfigurationImpl
import org.hornetq.jms.server.config.impl.*
import org.hornetq.jms.server.embedded.EmbeddedJMS

String folder = arg[0];
String id = arg[1];

configuration = new ConfigurationImpl();
configuration.setSecurityEnabled(false);
configuration.setJournalDirectory(folder + "/" + id + "/journal");
configuration.setBindingsDirectory(folder + "/" + id + "/binding");
configuration.setPagingDirectory(folder + "/" + id + "/paging");
configuration.setLargeMessagesDirectory(folder + "/" + id + "/largemessage");
configuration.setJournalType(org.hornetq.core.server.JournalType.NIO);
configuration.setPersistenceEnabled(false);

HashMap map = new HashMap();
map.put(TransportConstants.HOST_PROP_NAME, "localhost");
map.put(TransportConstants.PORT_PROP_NAME, "61616");
TransportConfiguration tpc = new TransportConfiguration(NettyAcceptorFactory.class.getName(), map);
configuration.getAcceptorConfigurations().add(tpc);

jmsConfiguration = new JMSConfigurationImpl();

JMSQueueConfigurationImpl queueConfiguration = new JMSQueueConfigurationImpl("queue", null, true);
TopicConfigurationImpl topicConfiguration = new TopicConfigurationImpl("topic");


jmsConfiguration.getQueueConfigurations().add(queueConfiguration);
jmsConfiguration.getTopicConfigurations().add(topicConfiguration);
server = new EmbeddedJMS();
server.setConfiguration(configuration);
server.setJmsConfiguration(jmsConfiguration);
server.start();


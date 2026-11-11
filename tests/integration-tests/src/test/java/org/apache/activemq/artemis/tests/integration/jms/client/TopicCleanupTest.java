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
package org.apache.activemq.artemis.tests.integration.jms.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.filter.impl.FilterImpl;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.Bindings;
import org.apache.activemq.artemis.core.postoffice.impl.BindingsImpl;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.QueueImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQTopic;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.junit.jupiter.api.Test;

/**
 * This test will simulate a situation where the Topics used to have an extra queue on startup. The server was then
 * written to perform a cleanup, and that cleanup should always work. This test will create the dirty situation where
 * the test should recover from
 */
public class TopicCleanupTest extends JMSTestBase {

   @Override
   protected boolean usePersistence() {
      return true;
   }

   @Test
   public void testSendTopic() throws Exception {
      Topic topic = createTopic("topic");
      Connection conn = cf.createConnection();

      try {
         conn.setClientID("someID");

         Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer cons = sess.createDurableSubscriber(topic, "someSub");

         conn.start();

         MessageProducer prod = sess.createProducer(topic);

         TextMessage msg1 = sess.createTextMessage("text");

         prod.send(msg1);

         assertNotNull(cons.receive(5000));

         conn.close();

         StorageManager storage = server.getStorageManager();

         for (int i = 0; i < 100; i++) {
            long txid = storage.generateID();

            final Queue queue = new QueueImpl(QueueConfiguration.of("topic").setRoutingType(RoutingType.MULTICAST).setId(storage.generateID()), FilterImpl.createFilter(Filter.GENERIC_IGNORED_FILTER), null, null, server.getScheduledPool(), server.getPostOffice(), storage, server.getAddressSettingsRepository(), server.getExecutorFactory().getExecutor(), server, null);

            LocalQueueBinding binding = new LocalQueueBinding(queue.getAddress(), queue, server.getNodeID());

            storage.addQueueBinding(txid, binding);

            storage.commitBindings(txid);
         }

         jmsServer.stop();

         jmsServer.start();

      } finally {
         try {
            conn.close();
         } catch (Throwable igonred) {
         }
      }

   }

   @Test
   public void testWildcardSubscriber() throws Exception {
      ActiveMQTopic topic = (ActiveMQTopic) createTopic("topic.A");
      Connection conn = cf.createConnection();
      conn.start();

      try {
         Session consumerStarSession = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer consumerStar = consumerStarSession.createConsumer(ActiveMQJMSClient.createTopic("topic.*"));

         Session consumerASession = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer consumerA = consumerASession.createConsumer(topic);

         Session producerSession = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producerA = producerSession.createProducer(topic);
         TextMessage msg1 = producerSession.createTextMessage("text");
         producerA.send(msg1);

         consumerStar.close();
         consumerA.close();

         producerA.send(msg1);

         conn.close();

         boolean foundStrayRoutingBinding = false;
         Bindings bindings = server.getPostOffice().getBindingsForAddress(SimpleString.of(topic.getAddress()));
         Map<SimpleString, List<Binding>> routingNames = ((BindingsImpl) bindings).getRoutingNameBindingMap();
         for (SimpleString key : routingNames.keySet()) {
            if (!key.toString().equals(topic.getAddress())) {
               foundStrayRoutingBinding = true;
               assertEquals(0, ((LocalQueueBinding) routingNames.get(key).get(0)).getQueue().getMessageCount());
            }
         }

         assertFalse(foundStrayRoutingBinding);
      } finally {
         jmsServer.stop();

         jmsServer.start();

         try {
            conn.close();
         } catch (Throwable igonred) {
         }
      }
   }
}

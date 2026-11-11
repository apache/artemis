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
package org.apache.activemq.artemis.tests.integration.plugin;

import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_ADD_ADDRESS;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_ADD_BINDING;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_CLOSE_CONSUMER;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_CLOSE_SESSION;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_CREATE_CONNECTION;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_CREATE_CONSUMER;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_CREATE_QUEUE;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_CREATE_SESSION;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_DELIVER;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_DEPLOY_BRIDGE;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_DESTROY_CONNECTION;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_MESSAGE_ROUTE;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_REMOVE_ADDRESS;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_REMOVE_BINDING;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_SEND;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.AFTER_SESSION_METADATA_ADDED;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_ADD_ADDRESS;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_ADD_BINDING;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_CLOSE_CONSUMER;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_CLOSE_SESSION;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_CREATE_CONSUMER;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_CREATE_QUEUE;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_CREATE_SESSION;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_DELIVER;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_DEPLOY_BRIDGE;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_MESSAGE_ROUTE;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_REMOVE_ADDRESS;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_REMOVE_BINDING;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_SEND;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.BEFORE_SESSION_METADATA_ADDED;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.MESSAGE_ACKED;
import static org.apache.activemq.artemis.tests.integration.plugin.MethodCalledVerifier.MESSAGE_EXPIRED;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.integration.openwire.BasicOpenWireTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class OpenwirePluginTest extends BasicOpenWireTest {

   private final Map<String, AtomicInteger> methodCalls = new ConcurrentHashMap<>();
   private final MethodCalledVerifier verifier = new MethodCalledVerifier(methodCalls);

   @Override
   protected void applySettings(ActiveMQServer server,
                                final Configuration configuration,
                                final int pageSize,
                                final long maxAddressSize,
                                final Integer maxReadPageMessages,
                                final Integer maxReadPageBytes,
                                final Map<String, AddressSettings> settings) {
      server.registerBrokerPlugin(verifier);
      server.registerBrokerPlugin(new ActiveMQServerPlugin() {

         @Override
         public void afterCreateConnection(RemotingConnection connection) throws ActiveMQException {
            try {
               //Verify that calling getClientID() before initialized doesn't cause an error
               //Test for ARTEMIS-1713
               connection.getClientID();
            } catch (Exception e) {
               throw new ActiveMQException(e.getMessage());
            }
         }
      });

      server.getAddressSettingsRepository().addMatch("autoCreated", new AddressSettings().setAutoDeleteAddresses(true)
            .setAutoDeleteQueues(true).setAutoCreateQueues(true).setAutoCreateAddresses(true).setMaxReadPageMessages(-1).setMaxReadPageBytes(-1));
   }

   @Test
   @Timeout(10)
   public void testAckedMessageAreConsumed() throws JMSException {
      connection.start();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Queue queue = session.createQueue(queueName);
      MessageProducer producer = session.createProducer(queue);
      producer.send(session.createTextMessage("Hello"));

      // Consume the message...
      MessageConsumer consumer = session.createConsumer(queue);
      Message msg = consumer.receive(1000);
      assertNotNull(msg);

      // Reset the session.
      session.close();
      session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

      // Attempt to Consume the message...
      consumer = session.createConsumer(queue);
      msg = consumer.receive(1000);
      assertNull(msg);

      session.close();
      connection.close();

      verifier.validatePluginMethodsEquals(0, MESSAGE_EXPIRED, BEFORE_DEPLOY_BRIDGE, AFTER_DEPLOY_BRIDGE,
            BEFORE_SESSION_METADATA_ADDED, AFTER_SESSION_METADATA_ADDED);
      verifier.validatePluginMethodsAtLeast(1, AFTER_CREATE_CONNECTION, AFTER_DESTROY_CONNECTION, BEFORE_CREATE_SESSION,
            AFTER_CREATE_SESSION, BEFORE_CLOSE_SESSION, AFTER_CLOSE_SESSION, BEFORE_CREATE_CONSUMER,
            AFTER_CREATE_CONSUMER, BEFORE_CLOSE_CONSUMER, AFTER_CLOSE_CONSUMER, BEFORE_CREATE_QUEUE, AFTER_CREATE_QUEUE,
            MESSAGE_ACKED, BEFORE_SEND, AFTER_SEND, BEFORE_MESSAGE_ROUTE, AFTER_MESSAGE_ROUTE, BEFORE_DELIVER,
            AFTER_DELIVER, BEFORE_ADD_ADDRESS, AFTER_ADD_ADDRESS, BEFORE_ADD_BINDING, AFTER_ADD_BINDING,
            BEFORE_REMOVE_BINDING, AFTER_REMOVE_BINDING);

   }

   @Test
   @Timeout(10)
   public void testAutoCreatedQueue() throws JMSException {
      connection.start();
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Queue queue = session.createQueue("autoCreated");

      // Reset the session.
      session.close();
      session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      session.createConsumer(queue);
      session.close();
      connection.close();

      //Happens more than once because of advisories
      verifier.validatePluginMethodsAtLeast(2, BEFORE_ADD_ADDRESS, AFTER_ADD_ADDRESS,
            BEFORE_REMOVE_ADDRESS, AFTER_REMOVE_ADDRESS);

   }

}

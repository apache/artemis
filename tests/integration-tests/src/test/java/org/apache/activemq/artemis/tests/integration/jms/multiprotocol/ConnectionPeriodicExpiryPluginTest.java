/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.jms.multiprotocol;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.plugin.impl.ConnectionPeriodicExpiryPlugin;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class ConnectionPeriodicExpiryPluginTest extends MultiprotocolJMSClientTestSupport {

   protected static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Override
   protected void addConfiguration(ActiveMQServer server) throws Exception {

      ConnectionPeriodicExpiryPlugin plugin = new ConnectionPeriodicExpiryPlugin();
      plugin.setPeriodSeconds(2);
      plugin.setAccuracyWindowSeconds(1);
      plugin.setAcceptorMatchRegex("netty-acceptor");
      server.getConfiguration().getBrokerPlugins().add(plugin);
   }

   @Test
   @Timeout(5)
   public void testAMQP() throws Exception {
      Connection connection = createConnection(); //AMQP
      testExpiry(connection);
   }

   @Test
   @Timeout(5)
   public void testCore() throws Exception {
      Connection connection = createCoreConnection();
      testExpiry(connection);
   }

   @Test
   @Timeout(5)
   public void testOpenWire() throws Exception {
      Connection connection = createOpenWireConnection();
      testExpiry(connection);
   }

   private void testExpiry(Connection connection) throws JMSException, InterruptedException {
      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Queue queue = session.createQueue(getQueueName());
         final MessageConsumer consumer = session.createConsumer(queue);
         consumer.setMessageListener(message -> {
            // don't care
         });

         final CountDownLatch gotExpired = new CountDownLatch(1);
         connection.setExceptionListener(exception -> {
            gotExpired.countDown(); });

         assertFalse(gotExpired.await(1500, TimeUnit.MILLISECONDS));
         Wait.assertTrue(() -> gotExpired.await(100, TimeUnit.MILLISECONDS), 4000, 100);

      } finally {
         try {
            connection.close();
         } catch (Exception expected) {
            // potential error on already disconnected
         }
      }
   }
}

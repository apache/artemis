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
package org.apache.activemq.artemis.tests.integration.federation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.activemq.artemis.core.config.FederationConfiguration;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.junit.jupiter.api.Test;

public class NettyFederatedQueueTest extends FederatedTestBase {

   @Override
   protected boolean isNetty() {
      return true;
   }

   @Override
   protected boolean isPersistenceEnabled() {
      return true;
   }

   @Test
   public void testFederatedQueueBiDirectionalUpstream() throws Exception {
      AssertionLoggerHandler loggerHandler = new AssertionLoggerHandler();
      runAfter(() -> loggerHandler.close());

      String queueName = getName();
      FederationConfiguration federationConfiguration0 = FederatedTestUtil.createQueueUpstreamFederationConfiguration("server1", queueName);
      getServer(0).getConfiguration().getFederationConfigurations().add(federationConfiguration0);
      getServer(0).getFederationManager().deploy();

      FederationConfiguration federationConfiguration1 = FederatedTestUtil.createQueueUpstreamFederationConfiguration("server0", queueName);
      getServer(1).getConfiguration().getFederationConfigurations().add(federationConfiguration1);
      getServer(1).getFederationManager().deploy();

      ConnectionFactory cf1 = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61616?consumerWindowSize=0");
      ConnectionFactory cf2 = CFUtil.createConnectionFactory("CORE", "tcp://localhost:61617?consumerWindowSize=0");

      Connection connection1 = cf1.createConnection();
      connection1.start();
      runAfter(connection1::close);
      Session session1 = connection1.createSession(true, Session.SESSION_TRANSACTED);
      MessageProducer producer1 = session1.createProducer(session1.createQueue(queueName));

      Connection connection2 = cf2.createConnection();
      connection2.start();
      runAfter(connection2::close);
      Session session2 = connection2.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageConsumer consumer2 = session2.createConsumer(session2.createQueue(queueName));

      producer1.send(session1.createTextMessage("Test"));
      session1.commit();

      assertNotNull(consumer2.receive(5000));

      for (int i = 0; i < 1000; i++) {
         producer1.send(session1.createTextMessage("test"));
      }
      session1.commit();

      final MessageConsumer consumer1 = session1.createConsumer(session1.createQueue(queueName));

      for (int i = 0; i < 100; i++) {
         assertNotNull(consumer1.receive(5000));
         session1.commit();
         assertNotNull(consumer2.receive(5000));
      }

      assertNotNull(consumer2.receive(5000));

      assertFalse(loggerHandler.findText("AMQ222153"));
   }
}

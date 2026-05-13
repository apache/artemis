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
import javax.jms.Session;
import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JMSTopicSubscriberTest extends MultiprotocolJMSClientTestSupport {

   protected static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Override
   protected void addConfiguration(ActiveMQServer server) throws Exception {
      server.getAddressSettingsRepository().getMatch(getTopicName()).setAutoCreateQueues(false);
   }

   @Test
   @Timeout(30)
   public void testCoreSubscriptionQueueCreatedWhenAutoCreateDisabled() throws Exception {
      Connection connection = createCoreConnection();
      testSubscriptionQueueCreatedWhenAutoCreateDisabled(connection);
   }

   @Test
   @Timeout(30)
   public void testOpenWireSubscriptionQueueCreatedWhenAutoCreateDisabled() throws Exception {
      Connection connection = createOpenWireConnection();
      testSubscriptionQueueCreatedWhenAutoCreateDisabled(connection);
   }

   @Test
   @Timeout(30)
   public void testAMQPSubscriptionQueueCreatedWhenAutoCreateDisabled() throws Exception {
      Connection connection = createConnection();
      testSubscriptionQueueCreatedWhenAutoCreateDisabled(connection);
   }

   private void testSubscriptionQueueCreatedWhenAutoCreateDisabled(Connection connection) throws Exception {
      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Topic topic = session.createTopic(getTopicName());
         assertEquals(0, server.getPostOffice().getBindingsForAddress(SimpleString.of(getTopicName())).size());
         session.createConsumer(topic);
         Wait.assertEquals(1, () -> server.getPostOffice().getBindingsForAddress(SimpleString.of(getTopicName())).size(), 2000, 100);
      } finally {
         connection.close();
      }
   }
}

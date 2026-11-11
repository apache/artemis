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
package org.apache.activemq.artemis.tests.integration.paging;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;
import java.util.Date;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public class PagingSizeWildcardTest extends ActiveMQTestBase {

   @Test
   public void testWildcardPageSize() throws Exception {

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      ActiveMQServer server = createServer(true, config, 200, 400, -1, -1, null);
      server.start();

      ActiveMQJMSConnectionFactory cf = (ActiveMQJMSConnectionFactory) ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY));

      try {
         Connection conn = cf.createConnection();
         conn.start();
         Session sessA = conn.createSession(true, Session.SESSION_TRANSACTED);
         Topic subA = sessA.createTopic("A.a");
         MessageConsumer consumerA = sessA.createConsumer(subA);

         Session sessW = conn.createSession(true, Session.SESSION_TRANSACTED);
         Topic subW = sessA.createTopic("A.#");
         MessageConsumer consumerW = sessW.createConsumer(subW);

         final int numMessages = 5;
         publish(cf, numMessages);

         for (int i = 0; i < numMessages; i++) {
            assertNotNull(consumerA.receive(1000), " on " + i);
            assertNotNull(consumerW.receive(1000), " on " + i);
         }

         // commit in reverse order to dispatch
         sessW.commit();
         sessA.commit();

         for (SimpleString psName : server.getPagingManager().getStoreNames()) {
            assertTrue(server.getPagingManager().getPageStore(psName).getAddressSize() >= 0, "non negative size: " + psName);
         }
         conn.close();

      } finally {
         server.stop();
      }
   }


   @Test
   public void testDurableSubReveresOrderAckPageSize() throws Exception {

      Configuration config = createDefaultInVMConfig().setJournalSyncNonTransactional(false);

      ActiveMQServer server = createServer(true, config, 200, 400, -1, -1, null);
      server.start();

      ActiveMQJMSConnectionFactory cf = (ActiveMQJMSConnectionFactory) ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY));

      try {
         Connection conn = cf.createConnection();
         conn.setClientID("IDD");
         conn.start();

         Session sessA = conn.createSession(true, Session.SESSION_TRANSACTED);
         Topic topic = sessA.createTopic("A.a");
         MessageConsumer consumerA = sessA.createDurableConsumer(topic, "1");

         Session sessW = conn.createSession(true, Session.SESSION_TRANSACTED);
         MessageConsumer consumerW = sessW.createDurableConsumer(topic, "2");

         final int numMessages = 5;
         publish(cf, numMessages);

         for (int i = 0; i < numMessages; i++) {
            assertNotNull(consumerA.receive(1000), " on " + i);
            assertNotNull(consumerW.receive(1000), " on " + i);
         }

         // commit in reverse order to dispatch
         sessW.commit();
         sessA.commit();

         for (SimpleString psName : server.getPagingManager().getStoreNames()) {
            assertTrue(server.getPagingManager().getPageStore(psName).getAddressSize() >= 0, "non negative size: " + psName);
         }
         conn.close();

      } finally {
         server.stop();
      }
   }

   private void publish(ActiveMQJMSConnectionFactory cf, int numMessages) throws Exception {
      Connection conn = cf.createConnection();
      conn.start();
      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Topic subA = sess.createTopic("A.a");
      MessageProducer messageProducer = sess.createProducer(subA);

      for (int i = 0; i < numMessages; i++) {
         messageProducer.send(sess.createTextMessage(new Date().toString()));
      }
      conn.close();
   }

}

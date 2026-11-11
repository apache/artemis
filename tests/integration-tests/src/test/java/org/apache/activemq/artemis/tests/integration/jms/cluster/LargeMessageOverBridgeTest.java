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
package org.apache.activemq.artemis.tests.integration.jms.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.MapMessage;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.Arrays;
import java.util.Collection;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.JMSClusteredTestBase;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class LargeMessageOverBridgeTest extends JMSClusteredTestBase {

   // TODO: find a solution to this
   // the "jms." prefix is needed because the cluster connection is matching on this
   public static final String QUEUE = "jms.Q1";

   private final boolean persistent;

   @Override
   protected boolean enablePersistence() {
      return persistent;
   }

   @Parameters(name = "persistent={0}")
   public static Collection getParameters() {
      return Arrays.asList(new Object[][]{{true}, {false}});
   }

   @Override
   protected final ConfigurationImpl createBasicConfig(final int serverID) {
      ConfigurationImpl configuration = super.createBasicConfig(serverID);
      configuration.setJournalFileSize(1024 * 1024);
      return configuration;
   }

   public LargeMessageOverBridgeTest(boolean persistent) {
      this.persistent = persistent;
   }

   /**
    * This was causing a text message to ber eventually converted into large message when sent over the bridge
    */
   @TestTemplate
   public void testSendHalfLargeTextMessage() throws Exception {
      createQueue(QUEUE);

      Queue queue = (Queue) context1.lookup("queue/" + QUEUE);
      Connection conn1 = cf1.createConnection();
      Session session1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer prod1 = session1.createProducer(queue);

      Connection conn2 = cf2.createConnection();
      Session session2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageConsumer cons2 = session2.createConsumer(queue);
      conn2.start();

      final String text = "a".repeat(51180);

      for (int i = 0; i < 10; i++) {
         TextMessage msg = session1.createTextMessage(text);
         prod1.send(msg);
      }

      TextMessage msg2 = (TextMessage) cons2.receive(5000);
      assertNotNull(msg2);

      assertEquals(text, msg2.getText());

      conn1.close();
      conn2.close();

   }

   /**
    * This was causing a text message to ber eventually converted into large message when sent over the bridge
    */
   @TestTemplate
   public void testSendMapMessageOverCluster() throws Exception {
      createQueue("jms." + QUEUE);

      Queue queue = (Queue) context1.lookup("queue/jms." + QUEUE);
      Connection conn1 = cf1.createConnection();
      Session session1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer prod1 = session1.createProducer(queue);

      Connection conn2 = cf2.createConnection();
      Session session2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageConsumer cons2 = session2.createConsumer(queue);
      conn2.start();

      final String body = "a".repeat(3810002);

      final int NUMBER_OF_MESSAGES = 1;

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         MapMessage msg = session1.createMapMessage();
         msg.setString("str", body);
         msg.setIntProperty("i", i);
         prod1.send(msg);
      }

      for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
         MapMessage msg = (MapMessage) cons2.receive(5000);
         assertEquals(body, msg.getString("str"));
      }

      assertNull(cons2.receiveNoWait());

      conn1.close();
      conn2.close();

   }

   /**
    * the hack to create the failing condition in certain tests
    */
   private void installHack(Configuration config) {
      if (this.getName().equals("testSendBytesAsLargeOnBridgeOnly")) {
         for (ClusterConnectionConfiguration conn : config.getClusterConfigurations()) {
            conn.setMinLargeMessageSize(1000);
         }
      }
   }

   @Override
   protected Configuration createConfigServer(final int source, final int destination) throws Exception {
      Configuration config = super.createConfigServer(source, destination);

      installHack(config);

      return config;
   }

   /**
    * This was causing a text message to ber eventually converted into large message when sent over the bridge
    */
   @TestTemplate
   public void testSendBytesAsLargeOnBridgeOnly() throws Exception {
      createQueue(QUEUE);

      Queue queue = (Queue) context1.lookup("queue/" + QUEUE);
      Connection conn1 = cf1.createConnection();
      Session session1 = conn1.createSession(true, Session.SESSION_TRANSACTED);
      MessageProducer prod1 = session1.createProducer(queue);

      Connection conn2 = cf2.createConnection();
      Session session2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageConsumer cons2 = session2.createConsumer(queue);
      conn2.start();

      byte[] bytes = new byte[10 * 1024];

      for (int i = 0; i < bytes.length; i++) {
         bytes[i] = getSamplebyte(i);
      }

      for (int i = 0; i < 10; i++) {
         BytesMessage msg = session1.createBytesMessage();
         msg.writeBytes(bytes);
         prod1.send(msg);
      }

      session1.commit();

      for (int i = 0; i < 5; i++) {
         BytesMessage msg2 = (BytesMessage) cons2.receive(5000);
         assertNotNull(msg2);
         msg2.acknowledge();

         for (int j = 0; j < bytes.length; j++) {
            assertEquals(msg2.readByte(), bytes[j], "Position " + i);
         }
      }

      conn1.close();
      conn2.close();
   }

   /**
    * The message won't be large to the client while it will be considered large through the bridge
    */
   @TestTemplate
   public void testSendLargeForBridge() throws Exception {
      createQueue(QUEUE);

      Queue queue = (Queue) context1.lookup("queue/" + QUEUE);

      ActiveMQConnectionFactory cf1 = ActiveMQJMSClient.createConnectionFactoryWithHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY, generateInVMParams(1)));
      cf1.setMinLargeMessageSize(200 * 1024);

      Connection conn1 = cf1.createConnection();
      Session session1 = conn1.createSession(true, Session.SESSION_TRANSACTED);
      MessageProducer prod1 = session1.createProducer(queue);

      Connection conn2 = cf2.createConnection();
      Session session2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageConsumer cons2 = session2.createConsumer(queue);
      conn2.start();

      byte[] bytes = new byte[150 * 1024];

      for (int i = 0; i < bytes.length; i++) {
         bytes[i] = getSamplebyte(i);
      }

      for (int i = 0; i < 10; i++) {
         BytesMessage msg = session1.createBytesMessage();
         msg.writeBytes(bytes);
         prod1.send(msg);
      }

      session1.commit();

      for (int i = 0; i < 5; i++) {
         BytesMessage msg2 = (BytesMessage) cons2.receive(5000);
         assertNotNull(msg2);
         msg2.acknowledge();

         for (int j = 0; j < bytes.length; j++) {
            assertEquals(msg2.readByte(), bytes[j], "Position " + i);
         }
      }

      conn1.close();
      conn2.close();
   }

}

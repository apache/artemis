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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.util.Arrays;
import java.util.Collection;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.AddressControl;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameter;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.integration.management.ManagementControlHelper;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class MoveMessageDuplicateIDTest extends JMSTestBase {
   @Parameter(index = 0)
   public String protocol = "AMQP";

   @Parameters(name = "protocol={0}")
   public static Collection<Object[]> parameters() {
      return Arrays.asList(new Object[][]{{"AMQP"}, {"CORE"}, {"OPENWIRE"}});
   }

   @Override
   protected Configuration createDefaultConfig(boolean netty) throws Exception {
      return super.createDefaultConfig(netty).setMessageExpiryScanPeriod(50);
   }

   @TestTemplate
   public void testTwoQueuesSingleDLQ() throws Exception {
      server.getAddressSettingsRepository().clear();
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setDeadLetterAddress(SimpleString.of("JUNKYARD")).setExpiryAddress(SimpleString.of("JUNKYARD")).setMaxDeliveryAttempts(1));

      createQueue("JUNKYARD");
      Queue junkQueue = server.locateQueue("JUNKYARD");
      assertNotNull(junkQueue);
      javax.jms.Queue queue1 = createQueue("q1");
      javax.jms.Queue queue2 = createQueue("q2");

      ConnectionFactory factory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");

      Connection conn = factory.createConnection();
      Session sess = conn.createSession(true, Session.SESSION_TRANSACTED);
      MessageProducer prod1 = sess.createProducer(queue1);
      MessageProducer prod2 = sess.createProducer(queue2);

      for (int i = 0; i < 100; i++) {
         TextMessage txt = sess.createTextMessage("txt");
         txt.setStringProperty("_AMQ_DUPL_ID", "" + i);
         prod1.send(txt);
         prod2.send(txt);
      }
      sess.commit();

      conn.start();
      MessageConsumer consumer = sess.createConsumer(queue1);
      for (int i = 0; i < 100; i++) {
         TextMessage textMessage = (TextMessage) consumer.receive(5000);
         assertNotNull(textMessage);
      }
      sess.rollback();

      assertNull(consumer.receiveNoWait());
      consumer.close();

      Wait.assertEquals(100L, junkQueue::getMessageCount, 2000, 10);

      consumer = sess.createConsumer(queue2);
      for (int i = 0; i < 100; i++) {
         TextMessage textMessage = (TextMessage) consumer.receive(5000);
         assertNotNull(textMessage);
      }
      sess.rollback();

      assertNull(consumer.receiveNoWait());

      consumer.close();
      conn.close();

      Wait.assertEquals(200L, junkQueue::getMessageCount, 2000, 10);
   }

   @TestTemplate
   public void testMultiplSubscriptionSingleExpire() throws Exception {
      server.getAddressSettingsRepository().clear();
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setDeadLetterAddress(SimpleString.of("DLQ")).setExpiryAddress(SimpleString.of("DLQ")));

      createQueue("DLQ");
      Queue dlqServerQueue = server.locateQueue("DLQ");
      assertNotNull(dlqServerQueue);
      Topic topic = createTopic("test-topic");
      AddressControl control = ManagementControlHelper.createAddressControl(SimpleString.of(topic.getTopicName()), mbeanServer);

      ConnectionFactory cf = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");

      Connection conn = cf.createConnection();

      conn.setClientID("client1");

      Session sess2 = conn.createSession(true, Session.SESSION_TRANSACTED);

      sess2.createDurableSubscriber(topic, "client-sub1");
      sess2.createDurableSubscriber(topic, "client-sub2");

      conn.close();

      conn = cf.createConnection();
      Session sess = conn.createSession(true, Session.SESSION_TRANSACTED);
      MessageProducer prod = sess.createProducer(topic);
      prod.setTimeToLive(1);

      for (int i = 0; i < 100; i++) {
         TextMessage txt = sess.createTextMessage("txt");
         txt.setStringProperty("_AMQ_DUPL_ID", "" + i);
         prod.send(txt);
      }

      sess.commit();

      conn.close();

      Wait.assertEquals(0L, control::getMessageCount, 2000, 10);
      Wait.assertEquals(200L, dlqServerQueue::getMessageCount, 2000, 10);

   }

   @TestTemplate
   public void testTwoQueuesSingleExpire() throws Exception {
      server.getAddressSettingsRepository().clear();
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setDeadLetterAddress(SimpleString.of("JUNKYARD")).setExpiryAddress(SimpleString.of("JUNKYARD")));

      createQueue("JUNKYARD");
      Queue junkQueue = server.locateQueue("JUNKYARD");
      assertNotNull(junkQueue);
      javax.jms.Queue queue1 = createQueue("q1");
      javax.jms.Queue queue2 = createQueue("q2");

      ConnectionFactory cf = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");

      Connection conn = cf.createConnection();
      Session sess = conn.createSession(true, Session.SESSION_TRANSACTED);
      MessageProducer prod1 = sess.createProducer(queue1);
      MessageProducer prod2 = sess.createProducer(queue2);
      prod1.setTimeToLive(1);
      prod2.setTimeToLive(1);

      for (int i = 0; i < 100; i++) {
         TextMessage txt = sess.createTextMessage("txt");
         txt.setStringProperty("_AMQ_DUPL_ID", "" + i);
         prod1.send(txt);
      }
      sess.commit();

      Wait.assertEquals(100L, junkQueue::getMessageCount, 2000, 10);

      for (int i = 0; i < 100; i++) {
         TextMessage txt = sess.createTextMessage("txt");
         txt.setStringProperty("_AMQ_DUPL_ID", "" + i);
         prod2.send(txt);
      }
      sess.commit();

      conn.close();
      Wait.assertEquals(200L, junkQueue::getMessageCount, 2000, 10);
   }

}

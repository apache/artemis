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
package org.apache.activemq.artemis.tests.integration.openwire;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Random;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnection;
import org.apache.activemq.artemis.jms.client.ActiveMQQueue;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameter;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

//adapted from https://issues.apache.org/jira/browse/ARTEMIS-1416
@ExtendWith(ParameterizedTestExtension.class)
public class LargeMessageQueueAutoCreationTest extends BasicOpenWireTest {

   Queue queue1;
   Random random = new Random();
   ActiveMQConnection testConn;
   ClientSession clientSession;

   @Parameter(index = 0)
   public boolean usingCore;

   @Parameters(name = "isCore={0}")
   public static Collection<Object[]> params() {
      return Arrays.asList(new Object[][]{{true}, {false}});
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      String randomSuffix = new BigInteger(130, random).toString(32);
      testConn = (ActiveMQConnection)coreCf.createConnection();
      clientSession = testConn.getSessionFactory().createSession();
      queue1 = createCoreQueue("queue1_" + randomSuffix);
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      testConn.close();
      super.tearDown();
   }

   @Override
   protected void extraServerConfig(Configuration serverConfig) {
      serverConfig.setJournalType(JournalType.NIO);
      Map<String, AddressSettings> map = serverConfig.getAddressSettings();
      Map.Entry<String, AddressSettings> entry = map.entrySet().iterator().next();
      AddressSettings settings = entry.getValue();
      settings.setAutoCreateQueues(true);
   }


   protected Queue createCoreQueue(final String queueName) throws Exception {
      SimpleString address = SimpleString.of(queueName);
      clientSession.createAddress(address, RoutingType.ANYCAST, false);
      return new ActiveMQQueue(queueName);
   }

   @TestTemplate
   @Timeout(30)
   public void testSmallString() throws Exception {
      sendStringOfSize(1024);
   }

   @TestTemplate
   @Timeout(30)
   public void testHugeString() throws Exception {
      sendStringOfSize(1024 * 1024);
   }

   private void sendStringOfSize(int msgSize) throws JMSException {

      ConnectionFactory factoryToUse = usingCore ? coreCf : factory;

      Connection conn = factoryToUse.createConnection();

      try {
         Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod = session.createProducer(queue1);

         TextMessage m = session.createTextMessage();

         m.setJMSDeliveryMode(DeliveryMode.PERSISTENT);

         final String originalString = RandomUtil.randomAlphaNumericString(msgSize);

         m.setText(originalString);

         prod.send(m);

         conn.close();

         conn = factoryToUse.createConnection();

         session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer cons = session.createConsumer(queue1);

         conn.start();

         TextMessage rm = (TextMessage) cons.receive(5000);
         assertNotNull(rm);

         String str = rm.getText();
         assertEquals(originalString, str);
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }
}
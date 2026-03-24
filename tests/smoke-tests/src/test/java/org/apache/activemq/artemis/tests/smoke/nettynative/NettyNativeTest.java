/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.activemq.artemis.tests.smoke.nettynative;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.smoke.common.SmokeTestBase;
import org.apache.activemq.artemis.util.ServerUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.File;

public class NettyNativeTest extends SmokeTestBase {

   public static final String SERVER_NAME = "nettynative";
   protected static final String SERVER_ADMIN_USERNAME = "admin";
   protected static final String SERVER_ADMIN_PASSWORD = "admin";

   @Before
   public void before() throws Exception {
      cleanupData(SERVER_NAME);
      disableCheckThread();
      startServer(SERVER_NAME, 0, 0);
      ServerUtil.waitForServerToStart(0, SERVER_ADMIN_USERNAME, SERVER_ADMIN_PASSWORD, 30000);
   }

   @Test
   public void testNettyNativeAvailable() throws Exception {
      ConnectionFactory factory = new ActiveMQConnectionFactory("tcp://localhost:61616", SERVER_ADMIN_USERNAME, SERVER_ADMIN_PASSWORD);

      try (Connection connection = factory.createConnection()) {
         try (Session session = connection.createSession(true, Session.SESSION_TRANSACTED)) {
            Queue queue = session.createQueue("TEST");
            MessageProducer producer = session.createProducer(queue);
            producer.send(session.createTextMessage("TEST"));

            session.commit();

            MessageConsumer consumer = session.createConsumer(queue);
            connection.start();

            TextMessage txt = (TextMessage) consumer.receive(10000);
            Assert.assertNotNull(txt);
            Assert.assertEquals("TEST", txt.getText());

            session.commit();
         }
      }

      File artemisLog = new File("target/" + SERVER_NAME + "/log/artemis.log");
      checkLogRecord(artemisLog, true, "Acceptor using native");
      checkLogRecord(artemisLog, false, "Acceptor using nio");
   }

}

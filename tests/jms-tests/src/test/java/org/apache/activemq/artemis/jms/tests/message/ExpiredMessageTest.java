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
package org.apache.activemq.artemis.jms.tests.message;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.jms.tests.JMSTestCase;
import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.Test;

public class ExpiredMessageTest extends JMSTestCase {

   @Test
   public void testSimpleExpiration() throws Exception {
      Connection conn = getConnectionFactory().createConnection();

      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageProducer prod = session.createProducer(queue1);
      prod.setTimeToLive(1);

      Message m = session.createTextMessage("This message will die");

      prod.send(m);

      // wait for the message to die

      Thread.sleep(250);

      MessageConsumer cons = session.createConsumer(queue1);

      conn.start();

      ProxyAssertSupport.assertNull(cons.receiveNoWait());

      conn.close();
   }

   @Test
   public void testExpiredAndLivingMessages() throws Exception {
      Connection conn = getConnectionFactory().createConnection();
      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer prod = session.createProducer(queue1);

      // sent 2 messages: 1 expiring, 1 living
      TextMessage livingMessage = session.createTextMessage("This message will live");
      TextMessage expiringMessage = session.createTextMessage("This message will expire");

      prod.setTimeToLive(1);
      prod.send(expiringMessage);

      prod.setTimeToLive(0);
      prod.send(livingMessage);

      // wait for the expiring message to die
      Thread.sleep(250);

      MessageConsumer cons = session.createConsumer(queue1);
      conn.start();

      // receive living message
      Message receivedMessage = cons.receive(1000);
      ProxyAssertSupport.assertNotNull("did not receive living message", receivedMessage);
      ProxyAssertSupport.assertTrue(receivedMessage instanceof TextMessage);
      ProxyAssertSupport.assertEquals(livingMessage.getText(), ((TextMessage) receivedMessage).getText());

      // we do not receive the expiring message
      ProxyAssertSupport.assertNull(cons.receiveNoWait());

      conn.close();
   }

   @Test
   public void testManyExpiredMessagesAtOnce() throws Exception {
      Connection conn = getConnectionFactory().createConnection();

      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageProducer prod = session.createProducer(queue1);
      prod.setTimeToLive(1);

      Message m = session.createTextMessage("This message will die");

      final int MESSAGE_COUNT = 100;

      for (int i = 0; i < MESSAGE_COUNT; i++) {
         prod.send(m);
      }

      MessageConsumer cons = session.createConsumer(queue1);
      conn.start();

      ProxyAssertSupport.assertNull(cons.receiveNoWait());

      conn.close();
   }
}

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
import javax.jms.DeliveryMode;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.jms.tests.ActiveMQServerTestCase;
import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.Test;

public class JMSPriorityHeaderTest extends ActiveMQServerTestCase {

   /*
    * Note - this test is testing our current implementation of message ordering since the spec
    * does not mandate that all higher priority messages are delivered first - this
    * is just how we currently do it
    */
   @Test
   public void testMessageOrder() throws Exception {
      Connection conn = getConnectionFactory().createConnection();

      conn.start();

      Session sessSend = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageProducer prod = sessSend.createProducer(queue1);

      TextMessage m0 = sessSend.createTextMessage("a");
      TextMessage m1 = sessSend.createTextMessage("b");
      TextMessage m2 = sessSend.createTextMessage("c");
      TextMessage m3 = sessSend.createTextMessage("d");
      TextMessage m4 = sessSend.createTextMessage("e");
      TextMessage m5 = sessSend.createTextMessage("f");
      TextMessage m6 = sessSend.createTextMessage("g");
      TextMessage m7 = sessSend.createTextMessage("h");
      TextMessage m8 = sessSend.createTextMessage("i");
      TextMessage m9 = sessSend.createTextMessage("j");

      prod.send(m0, DeliveryMode.NON_PERSISTENT, 0, 0);
      prod.send(m1, DeliveryMode.NON_PERSISTENT, 1, 0);
      prod.send(m2, DeliveryMode.NON_PERSISTENT, 2, 0);
      prod.send(m3, DeliveryMode.NON_PERSISTENT, 3, 0);
      prod.send(m4, DeliveryMode.NON_PERSISTENT, 4, 0);
      prod.send(m5, DeliveryMode.NON_PERSISTENT, 5, 0);
      prod.send(m6, DeliveryMode.NON_PERSISTENT, 6, 0);
      prod.send(m7, DeliveryMode.NON_PERSISTENT, 7, 0);
      prod.send(m8, DeliveryMode.NON_PERSISTENT, 8, 0);
      prod.send(m9, DeliveryMode.NON_PERSISTENT, 9, 0);

      // NP messages are sent async so we need to allow them time to all hit the server
      Thread.sleep(2000);

      Session sessReceive = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageConsumer cons = sessReceive.createConsumer(queue1);

      {
         TextMessage t = (TextMessage) cons.receive(1000);
         ProxyAssertSupport.assertNotNull(t);
         ProxyAssertSupport.assertEquals("j", t.getText());
      }
      {
         TextMessage t = (TextMessage) cons.receive(1000);
         ProxyAssertSupport.assertNotNull(t);
         ProxyAssertSupport.assertEquals("i", t.getText());
      }
      {
         TextMessage t = (TextMessage) cons.receive(1000);
         ProxyAssertSupport.assertNotNull(t);
         ProxyAssertSupport.assertEquals("h", t.getText());
      }
      {
         TextMessage t = (TextMessage) cons.receive(1000);
         ProxyAssertSupport.assertNotNull(t);
         ProxyAssertSupport.assertEquals("g", t.getText());
      }
      {
         TextMessage t = (TextMessage) cons.receive(1000);
         ProxyAssertSupport.assertNotNull(t);
         ProxyAssertSupport.assertEquals("f", t.getText());
      }
      {
         TextMessage t = (TextMessage) cons.receive(1000);
         ProxyAssertSupport.assertNotNull(t);
         ProxyAssertSupport.assertEquals("e", t.getText());
      }
      {
         TextMessage t = (TextMessage) cons.receive(1000);
         ProxyAssertSupport.assertNotNull(t);
         ProxyAssertSupport.assertEquals("d", t.getText());
      }
      {
         TextMessage t = (TextMessage) cons.receive(1000);
         ProxyAssertSupport.assertNotNull(t);
         ProxyAssertSupport.assertEquals("c", t.getText());
      }
      {
         TextMessage t = (TextMessage) cons.receive(1000);
         ProxyAssertSupport.assertNotNull(t);
         ProxyAssertSupport.assertEquals("b", t.getText());
      }
      {
         TextMessage t = (TextMessage) cons.receive(1000);
         ProxyAssertSupport.assertNotNull(t);
         ProxyAssertSupport.assertEquals("a", t.getText());
      }
      {
         TextMessage t = (TextMessage) cons.receiveNoWait();
         ProxyAssertSupport.assertNull(t);
      }

      cons.close();

      prod.send(m0, DeliveryMode.NON_PERSISTENT, 0, 0);
      prod.send(m1, DeliveryMode.NON_PERSISTENT, 0, 0);
      prod.send(m2, DeliveryMode.NON_PERSISTENT, 0, 0);
      prod.send(m3, DeliveryMode.NON_PERSISTENT, 3, 0);
      prod.send(m4, DeliveryMode.NON_PERSISTENT, 3, 0);
      prod.send(m5, DeliveryMode.NON_PERSISTENT, 4, 0);
      prod.send(m6, DeliveryMode.NON_PERSISTENT, 4, 0);
      prod.send(m7, DeliveryMode.NON_PERSISTENT, 5, 0);
      prod.send(m8, DeliveryMode.NON_PERSISTENT, 5, 0);
      prod.send(m9, DeliveryMode.NON_PERSISTENT, 6, 0);

      // Give them time to hit the server
      Thread.sleep(2000);

      cons = sessReceive.createConsumer(queue1);

      {
         TextMessage t = (TextMessage) cons.receive(1000);
         ProxyAssertSupport.assertNotNull(t);
         ProxyAssertSupport.assertEquals("j", t.getText());
      }
      {
         TextMessage t = (TextMessage) cons.receive(1000);
         ProxyAssertSupport.assertNotNull(t);
         ProxyAssertSupport.assertEquals("h", t.getText());
      }
      {
         TextMessage t = (TextMessage) cons.receive(1000);
         ProxyAssertSupport.assertNotNull(t);
         ProxyAssertSupport.assertEquals("i", t.getText());
      }
      {
         TextMessage t = (TextMessage) cons.receive(1000);
         ProxyAssertSupport.assertNotNull(t);
         ProxyAssertSupport.assertEquals("f", t.getText());
      }
      {
         TextMessage t = (TextMessage) cons.receive(1000);
         ProxyAssertSupport.assertNotNull(t);
         ProxyAssertSupport.assertEquals("g", t.getText());
      }
      {
         TextMessage t = (TextMessage) cons.receive(1000);
         ProxyAssertSupport.assertNotNull(t);
         ProxyAssertSupport.assertEquals("d", t.getText());
      }
      {
         TextMessage t = (TextMessage) cons.receive(1000);
         ProxyAssertSupport.assertNotNull(t);
         ProxyAssertSupport.assertEquals("e", t.getText());
      }
      {
         TextMessage t = (TextMessage) cons.receive(1000);
         ProxyAssertSupport.assertNotNull(t);
         ProxyAssertSupport.assertEquals("a", t.getText());
      }
      {
         TextMessage t = (TextMessage) cons.receive(1000);
         ProxyAssertSupport.assertNotNull(t);
         ProxyAssertSupport.assertEquals("b", t.getText());
      }
      {
         TextMessage t = (TextMessage) cons.receive(1000);
         ProxyAssertSupport.assertNotNull(t);
         ProxyAssertSupport.assertEquals("c", t.getText());
      }
      {
         TextMessage t = (TextMessage) cons.receiveNoWait();
         ProxyAssertSupport.assertNull(t);
      }

      conn.close();
   }

   @Test
   public void testSimple() throws Exception {
      Connection conn = getConnectionFactory().createConnection();

      conn.start();

      Session sessSend = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageProducer prod = sessSend.createProducer(queue1);

      TextMessage m0 = sessSend.createTextMessage("a");

      prod.send(m0, DeliveryMode.NON_PERSISTENT, 7, 0);

      // Let it hit server
      Thread.sleep(2000);

      Session sessReceive = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageConsumer cons = sessReceive.createConsumer(queue1);

      {
         TextMessage t = (TextMessage) cons.receive(1000);
         ProxyAssertSupport.assertNotNull(t);
         ProxyAssertSupport.assertEquals("a", t.getText());
         ProxyAssertSupport.assertEquals(7, t.getJMSPriority());
      }

      // Give the message enough time to reach the consumer

      Thread.sleep(2000);

      {
         TextMessage t = (TextMessage) cons.receiveNoWait();
         ProxyAssertSupport.assertNull(t);
      }

      conn.close();
   }
}

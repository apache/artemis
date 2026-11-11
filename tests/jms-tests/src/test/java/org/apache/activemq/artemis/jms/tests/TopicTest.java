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
package org.apache.activemq.artemis.jms.tests;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.io.Serializable;

import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.Test;

public class TopicTest extends JMSTestCase {



   /**
    * The simplest possible topic test.
    */
   @Test
   public void testTopic() throws Exception {
      Connection conn = createConnection();

      Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer p = s.createProducer(ActiveMQServerTestCase.topic1);
      MessageConsumer c = s.createConsumer(ActiveMQServerTestCase.topic1);
      conn.start();

      p.send(s.createTextMessage("payload"));
      TextMessage m = (TextMessage) c.receive();

      ProxyAssertSupport.assertEquals("payload", m.getText());
   }

   @Test
   public void testTopic2() throws Exception {
      Connection conn = createConnection();

      Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer p = s.createProducer(ActiveMQServerTestCase.topic1);
      MessageConsumer c = s.createConsumer(ActiveMQServerTestCase.topic1);
      conn.start();

      p.send(s.createTextMessage("payload"));
      TextMessage m = (TextMessage) c.receive();

      ProxyAssertSupport.assertEquals("payload", m.getText());
   }

   @Test
   public void testTopicName() throws Exception {
      Topic topic = (Topic) ic.lookup("/topic/Topic1");
      ProxyAssertSupport.assertEquals("Topic1", topic.getTopicName());
   }

   @Test
   public void testRace() throws Exception {
      Connection conn = createConnection();

      Session sSend = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageProducer prod = sSend.createProducer(ActiveMQServerTestCase.topic1);
      prod.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

      Session s1 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Session s2 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Session s3 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageConsumer c1 = s1.createConsumer(ActiveMQServerTestCase.topic1);
      MessageConsumer c2 = s2.createConsumer(ActiveMQServerTestCase.topic1);
      MessageConsumer c3 = s3.createConsumer(ActiveMQServerTestCase.topic1);

      final int numMessages = 500;

      TestListener l1 = new TestListener(numMessages);
      TestListener l2 = new TestListener(numMessages);
      TestListener l3 = new TestListener(numMessages);

      c1.setMessageListener(l1);
      c2.setMessageListener(l2);
      c3.setMessageListener(l3);

      conn.start();

      for (int i = 0; i < numMessages; i++) {
         byte[] blah = new byte[10000];
         String str = new String(blah);

         Wibble2 w = new Wibble2();
         w.s = str;
         ObjectMessage om = sSend.createObjectMessage(w);

         prod.send(om);
      }

      l1.waitForMessages();
      l2.waitForMessages();
      l3.waitForMessages();

      ProxyAssertSupport.assertFalse(l1.failed);
      ProxyAssertSupport.assertFalse(l2.failed);
      ProxyAssertSupport.assertFalse(l3.failed);
   }

   static class Wibble2 implements Serializable {

      private static final long serialVersionUID = -5146179676719808756L;

      String s;
   }

   static class TestListener implements MessageListener {

      boolean failed;

      int count;

      int num;

      TestListener(final int num) {
         this.num = num;
      }

      @Override
      public synchronized void onMessage(final Message m) {
         ObjectMessage om = (ObjectMessage) m;

         try {
            Wibble2 w = (Wibble2) om.getObject();
         } catch (Exception e) {
            failed = true;
         }

         count++;

         if (count == num) {
            notify();
         }
      }

      synchronized void waitForMessages() throws Exception {
         while (count < num) {
            this.wait();
         }
      }
   }

}

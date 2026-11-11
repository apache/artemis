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

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.HashSet;
import java.util.Set;

import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.Test;

public class QueueTest extends JMSTestCase {

   /**
    * The simplest possible queue test.
    */
   @Test
   public void testQueue() throws Exception {
      Connection conn = null;

      try {
         conn = createConnection();

         Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer p = s.createProducer(queue1);
         MessageConsumer c = s.createConsumer(queue1);
         conn.start();

         p.send(s.createTextMessage("payload"));
         TextMessage m = (TextMessage) c.receive(5000);

         ProxyAssertSupport.assertEquals("payload", m.getText());
      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   @Test
   public void testBytesMessagePersistence() throws Exception {
      Connection conn = null;

      byte[] bytes = new byte[]{'a', 'b', 'c', 'd', 'e', 'f', 'g', 123, 55, 0, 12, -100, -11};
      conn = createConnection();
      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer prod = sess.createProducer(queue1);
      prod.setDeliveryMode(DeliveryMode.PERSISTENT);

      for (int i = 0; i < 1; i++) {
         BytesMessage bm = sess.createBytesMessage();

         bm.writeBytes(bytes);

         prod.send(bm);
      }

      conn.close();

      stop();

      startNoDelete();

      // Apache Artemis server restart implies new ConnectionFactory lookup
      deployAndLookupAdministeredObjects();

      conn = createConnection();
      sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      conn.start();
      MessageConsumer cons = sess.createConsumer(queue1);
      for (int i = 0; i < 1; i++) {
         BytesMessage bm = (BytesMessage) cons.receive(3000);

         ProxyAssertSupport.assertNotNull(bm);

         byte[] bytes2 = new byte[bytes.length];

         bm.readBytes(bytes2);

         for (int j = 0; j < bytes.length; j++) {
            ProxyAssertSupport.assertEquals(bytes[j], bytes2[j]);
         }
      }
   }

   @Test
   public void testClosedConsumerAfterStart() throws Exception {
      // This loop is to increase chances of a failure.
      for (int counter = 0; counter < 20; counter++) {
         Connection conn1 = null;

         Connection conn2 = null;

         try {
            conn1 = createConnection();

            conn2 = createConnection();

            Session s = conn1.createSession(true, Session.SESSION_TRANSACTED);

            MessageProducer p = s.createProducer(queue1);

            for (int i = 0; i < 20; i++) {
               p.send(s.createTextMessage("message " + i));
            }

            s.commit();

            Session s2 = conn2.createSession(true, Session.SESSION_TRANSACTED);

            // Create a consumer, start the session, close the consumer..
            // This shouldn't cause any message to be lost
            MessageConsumer c2 = s2.createConsumer(queue1);
            conn2.start();
            c2.close();

            c2 = s2.createConsumer(queue1);

            // There is a possibility the messages arrive out of order if they hit the closed
            // consumer and are cancelled back before delivery to the other consumer has finished.
            // There is nothing much we can do about this
            Set<String> texts = new HashSet<>();

            for (int i = 0; i < 20; i++) {
               TextMessage txt = (TextMessage) c2.receive(5000);
               ProxyAssertSupport.assertNotNull(txt);
               texts.add(txt.getText());
            }

            for (int i = 0; i < 20; i++) {
               ProxyAssertSupport.assertTrue(texts.contains("message " + i));
            }

            s2.commit();

            checkEmpty(queue1);
         } finally {
            if (conn1 != null) {
               conn1.close();
            }
            if (conn2 != null) {
               conn2.close();
            }
         }
      }
   }

   @Test
   public void testRedeployQueue() throws Exception {
      Connection conn = createConnection();

      Session s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      MessageProducer p = s.createProducer(queue1);
      MessageConsumer c = s.createConsumer(queue1);
      conn.start();

      for (int i = 0; i < 500; i++) {
         p.send(s.createTextMessage("payload " + i));
      }

      conn.close();

      stop();

      startNoDelete();

      deployAndLookupAdministeredObjects();

      conn = createConnection();
      s = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      p = s.createProducer(queue1);
      c = s.createConsumer(queue1);
      conn.start();

      for (int i = 0; i < 500; i++) {
         TextMessage message = (TextMessage) c.receive(3000);
         ProxyAssertSupport.assertNotNull(message);
         ProxyAssertSupport.assertNotNull(message.getJMSDestination());
      }
   }

   @Test
   public void testQueueName() throws Exception {
      ProxyAssertSupport.assertEquals("Queue1", queue1.getQueueName());
   }
}

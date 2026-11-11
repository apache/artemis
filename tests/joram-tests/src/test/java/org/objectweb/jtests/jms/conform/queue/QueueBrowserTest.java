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
package org.objectweb.jtests.jms.conform.queue;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.QueueBrowser;
import javax.jms.TextMessage;
import java.util.Enumeration;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.jtests.jms.framework.PTPTestCase;
import org.objectweb.jtests.jms.framework.TestConfig;

/**
 * Test the {@code javax.jms.QueueBrowser} features.
 */
public class QueueBrowserTest extends PTPTestCase {

   protected QueueBrowser receiverBrowser;

   protected QueueBrowser senderBrowser;

   /**
    * Test the {@code QueueBrowser} of the sender.
    */
   @Test
   public void testSenderBrowser() {
      try {
         TextMessage message_1 = senderSession.createTextMessage();
         message_1.setText("testBrowser:message_1");
         TextMessage message_2 = senderSession.createTextMessage();
         message_2.setText("testBrowser:message_2");

         receiver.close();

         // send two messages...
         sender.send(message_1);
         sender.send(message_2);

         // ask the browser to browse the sender's session
         Enumeration enumeration = senderBrowser.getEnumeration();
         int count = 0;
         while (enumeration.hasMoreElements()) {
            // one more message in the queue
            count++;
            // check that the message in the queue is one of the two which where sent
            Object obj = enumeration.nextElement();
            Assert.assertTrue(obj instanceof TextMessage);
            TextMessage msg = (TextMessage) obj;
            Assert.assertTrue(msg.getText().startsWith("testBrowser:message_"));
         }
         // check that there is effectively 2 messages in the queue
         Assert.assertEquals(2, count);

         receiver = receiverSession.createReceiver(receiverQueue);
         // receive the first message...
         Message m = receiver.receive(TestConfig.TIMEOUT);
         // ... and check it is the first which was sent.
         Assert.assertTrue(m instanceof TextMessage);
         TextMessage msg = (TextMessage) m;
         Assert.assertEquals("testBrowser:message_1", msg.getText());

         // receive the second message...
         m = receiver.receive(TestConfig.TIMEOUT);
         // ... and check it is the second which was sent.
         Assert.assertTrue(m instanceof TextMessage);
         msg = (TextMessage) m;
         Assert.assertEquals("testBrowser:message_2", msg.getText());

         // ask the browser to browse the sender's session
         enumeration = receiverBrowser.getEnumeration();
         // check that there is no messages in the queue
         // (the two messages have been acknowledged and so removed
         // from the queue)
         Assert.assertFalse(enumeration.hasMoreElements());
      } catch (JMSException e) {
         fail(e);
      }
   }

   /**
    * Test that a {@code QueueBrowser} created with a message selector browses only the messages matching this
    * selector.
    */
   @Test
   public void testBrowserWithMessageSelector() {
      try {
         senderBrowser = senderSession.createBrowser(senderQueue, "pi = 3.14159");

         receiver.close();

         TextMessage message_1 = senderSession.createTextMessage();
         message_1.setText("testBrowserWithMessageSelector:message_1");
         TextMessage message_2 = senderSession.createTextMessage();
         message_2.setDoubleProperty("pi", 3.14159);
         message_2.setText("testBrowserWithMessageSelector:message_2");

         sender.send(message_1);
         sender.send(message_2);

         Enumeration enumeration = senderBrowser.getEnumeration();
         int count = 0;
         while (enumeration.hasMoreElements()) {
            count++;
            Object obj = enumeration.nextElement();
            Assert.assertTrue(obj instanceof TextMessage);
            TextMessage msg = (TextMessage) obj;
            Assert.assertEquals("testBrowserWithMessageSelector:message_2", msg.getText());
         }
         Assert.assertEquals(1, count);
      } catch (JMSException e) {
         fail(e);
      }
   }

   @Override
   @Before
   public void setUp() throws Exception {
      try {
         super.setUp();
         receiverBrowser = receiverSession.createBrowser(receiverQueue);
         senderBrowser = senderSession.createBrowser(senderQueue);
      } catch (JMSException e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   @After
   public void tearDown() throws Exception {
      try {
         receiverBrowser.close();
         senderBrowser.close();
         super.tearDown();
      } catch (JMSException ignored) {
      } finally {
         receiverBrowser = null;
         senderBrowser = null;
      }
   }
}

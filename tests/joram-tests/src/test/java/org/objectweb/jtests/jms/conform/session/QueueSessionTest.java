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
package org.objectweb.jtests.jms.conform.session;

import javax.jms.InvalidDestinationException;
import javax.jms.InvalidSelectorException;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.TextMessage;

import org.junit.Assert;
import org.junit.Test;
import org.objectweb.jtests.jms.framework.PTPTestCase;
import org.objectweb.jtests.jms.framework.TestConfig;

/**
 * Test queue sessions
 * <p>
 * See JMS specifications, sec. 4.4 Session
 */
public class QueueSessionTest extends PTPTestCase {

   /**
    * Test that if we rollback a transaction which has consumed a message, the message is effectively redelivered.
    */
   @Test
   public void testRollbackRececeivedMessage() {
      try {
         senderConnection.stop();
         // senderSession has been created as non transacted
         // we create it again but as a transacted session
         senderSession = senderConnection.createQueueSession(true, 0);
         Assert.assertTrue(senderSession.getTransacted());
         // we create again the sender
         sender = senderSession.createSender(senderQueue);
         senderConnection.start();

         receiverConnection.stop();
         // receiverSession has been created as non transacted
         // we create it again but as a transacted session
         receiverSession = receiverConnection.createQueueSession(true, 0);
         Assert.assertTrue(receiverSession.getTransacted());

         if (receiver != null) {
            receiver.close();
         }
         // we create again the receiver
         receiver = receiverSession.createReceiver(receiverQueue);
         receiverConnection.start();

         // we send a message...
         TextMessage message = senderSession.createTextMessage();
         message.setText("testRollbackRececeivedMessage");
         sender.send(message);
         // ... and commit the *producer* transaction
         senderSession.commit();

         // we receive a message...
         Message m = receiver.receive(TestConfig.TIMEOUT);
         Assert.assertNotNull(m);
         Assert.assertTrue(m instanceof TextMessage);
         TextMessage msg = (TextMessage) m;
         // ... which is the one which was sent...
         Assert.assertEquals("testRollbackRececeivedMessage", msg.getText());
         // ...and has not been redelivered
         Assert.assertFalse(msg.getJMSRedelivered());

         // we rollback the *consumer* transaction
         receiverSession.rollback();

         // we receive again a message
         m = receiver.receive(TestConfig.TIMEOUT);
         Assert.assertNotNull(m);
         Assert.assertTrue(m instanceof TextMessage);
         msg = (TextMessage) m;
         // ... which is still the one which was sent...
         Assert.assertEquals("testRollbackRececeivedMessage", msg.getText());
         // .. but this time, it has been redelivered
         Assert.assertTrue(msg.getJMSRedelivered());

      } catch (Exception e) {
         fail(e);
      }
   }

   /**
    * Test that a call to the {@code createBrowser()} method with an invalid messaeg session throws a
    * {@code javax.jms.InvalidSelectorException}.
    */
   @Test
   public void testCreateBrowser_2() {
      try {
         senderSession.createBrowser(senderQueue, "definitely not a message selector!");
         Assert.fail("Should throw a javax.jms.InvalidSelectorException.\n");
      } catch (InvalidSelectorException e) {
      } catch (JMSException e) {
         Assert.fail("Should throw a javax.jms.InvalidSelectorException, not a " + e);
      }
   }

   /**
    * Test that a call to the {@code createBrowser()} method with an invalid {@code Queue} throws a
    * {@code javax.jms.InvalidDestinationException}.
    */
   @Test
   public void testCreateBrowser_1() {
      try {
         senderSession.createBrowser((Queue) null);
         Assert.fail("Should throw a javax.jms.InvalidDestinationException.\n");
      } catch (InvalidDestinationException e) {
      } catch (JMSException e) {
         Assert.fail("Should throw a javax.jms.InvalidDestinationException, not a " + e);
      }
   }

   /**
    * Test that a call to the {@code createReceiver()} method with an invalid message selector throws a
    * {@code javax.jms.InvalidSelectorException}.
    */
   @Test
   public void testCreateReceiver_2() {
      try {
         receiver = senderSession.createReceiver(senderQueue, "definitely not a message selector!");
         Assert.fail("Should throw a javax.jms.InvalidSelectorException.\n");
      } catch (InvalidSelectorException e) {
      } catch (JMSException e) {
         Assert.fail("Should throw a javax.jms.InvalidSelectorException, not a " + e);
      }
   }

   /**
    * Test that a call to the {@code createReceiver()} method with an invalid {@code Queue} throws a
    * {@code javax.jms.InvalidDestinationException}>
    */
   @Test
   public void testCreateReceiver_1() {
      try {
         receiver = senderSession.createReceiver((Queue) null);
         Assert.fail("Should throw a javax.jms.InvalidDestinationException.\n");
      } catch (InvalidDestinationException e) {
         // expected
      } catch (JMSException e) {
         Assert.fail("Should throw a javax.jms.InvalidDestinationException, not a " + e);
      }
   }
}

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

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.junit.Assert;
import org.junit.Test;
import org.objectweb.jtests.jms.framework.PTPTestCase;
import org.objectweb.jtests.jms.framework.TestConfig;

/**
 * Test sessions
 * <p>
 * See JMS specifications, sec. 4.4 Session
 */
public class SessionTest extends PTPTestCase {

   /**
    * Test that an attempt to call the {@code recover()} method on a <strong>transacted </strong> {@code Session} throws
    * a {@code javax.jms.IllegalStateException}.
    */
   @Test
   public void testRecoverTransactedSession() {
      try {
         // senderSession has been created as non transacted
         Assert.assertFalse(senderSession.getTransacted());
         // we create it again but as a transacted session
         senderSession = senderConnection.createQueueSession(true, 0);
         Assert.assertTrue(senderSession.getTransacted());
         senderSession.recover();
         Assert.fail("Should raise an IllegalStateException, the session is not transacted.\n");
      } catch (javax.jms.IllegalStateException e) {
      } catch (java.lang.IllegalStateException e) {
         Assert.fail("Should raise a javax.jms.IllegalStateException, not a java.lang.IllegalStateException.\n");
      } catch (Exception e) {
         Assert.fail("Should raise a javax.jms.IllegalStateException, not a " + e);
      }
   }

   /**
    * Test that a call to the {@code rollback()} method on a <strong>transacted</strong> {@code Session} rollbacks all
    * the messages sent in the transaction.
    */
   @Test
   public void testRollbackTransactedSession() {
      try {
         // re-create senderSession as a transacted session
         senderSession = senderConnection.createQueueSession(true, 0);
         sender = senderSession.createSender(senderQueue);
         Assert.assertTrue(senderSession.getTransacted());

         TextMessage message = senderSession.createTextMessage();
         message.setText("testRollbackTransactedSession");
         // send a message within a transacted session
         sender.send(message);

         // rollback the transaction -> the sent message shouldn't be received
         senderSession.rollback();

         TextMessage m = (TextMessage) receiver.receiveNoWait();
         // test that no message has been received
         Assert.assertNull(m);
      } catch (Exception e) {
         fail(e);
      }
   }

   /**
    * Test that a call to the {@code rollback()} method on a <strong>transacted</strong> {@code Session} rollbacks all
    * the messages sent in the transaction.
    */
   @Test
   public void testCommitTransactedSession() {
      try {
         // re-create senderSession as a transacted session
         senderSession = senderConnection.createQueueSession(true, 0);
         sender = senderSession.createSender(senderQueue);
         Assert.assertTrue(senderSession.getTransacted());

         TextMessage message = senderSession.createTextMessage();
         message.setText("testCommitTransactedSession");
         // send a message within a transacted session
         sender.send(message);

         TextMessage m = (TextMessage) receiver.receiveNoWait();
         // test that no message has been received (the transaction has not been committed yet)
         Assert.assertNull(m);

         // commit the transaction -> the sent message should be received
         senderSession.commit();

         m = (TextMessage) receiver.receive(TestConfig.TIMEOUT);
         Assert.assertNotNull(m);
         Assert.assertEquals("testCommitTransactedSession", m.getText());
      } catch (Exception e) {
         fail(e);
      }
   }

   /**
    * Test that an attempt to call the {@code roolback()} method on a <strong>non transacted</strong> {@code Session}
    * throws a {@code javax.jms.IllegalStateException}.
    */
   @Test
   public void testRollbackNonTransactedSession() {
      try {
         // senderSession has been created as non transacted in the setUp() method
         Assert.assertFalse(senderSession.getTransacted());
         senderSession.rollback();
         Assert.fail("Should raise an IllegalStateException, the session is not transacted.\n");
      } catch (javax.jms.IllegalStateException e) {
      } catch (java.lang.IllegalStateException e) {
         Assert.fail("Should raise a javax.jms.IllegalStateException, not a java.lang.IllegalStateException.\n");
      } catch (Exception e) {
         Assert.fail("Should raise a javax.jms.IllegalStateException, not a " + e);
      }
   }

   /**
    * Test that an attempt to call the {@code commit()} method on a <strong>non transacted</strong> {@code Session}
    * throws a {@code javax.jms.IllegalStateException}.
    */
   @Test
   public void testCommitNonTransactedSession() {
      try {
         // senderSession has been created as non transacted in the setUp() method
         Assert.assertFalse(senderSession.getTransacted());
         senderSession.commit();
         Assert.fail("Should raise an IllegalStateException, the session is not transacted.\n");
      } catch (javax.jms.IllegalStateException e) {
      } catch (java.lang.IllegalStateException e) {
         Assert.fail("Should raise a javax.jms.IllegalStateException, not a java.lang.IllegalStateException.\n");
      } catch (Exception e) {
         Assert.fail("Should raise a javax.jms.IllegalStateException, not a " + e);
      }
   }

   /**
    * Test that the {@code getTransacted()} method of a {@code Session} returns {@code true} if the session is
    * transacted, {@code false} else.
    */
   @Test
   public void testGetTransacted() {
      try {
         // senderSession has been created as non transacted
         Assert.assertFalse(senderSession.getTransacted());
         // we re-create senderSession as a transacted session
         senderSession = senderConnection.createQueueSession(true, Session.AUTO_ACKNOWLEDGE);
         Assert.assertTrue(senderSession.getTransacted());
      } catch (Exception e) {
         fail(e);
      }
   }

   /**
    * Test that invoking the {@code acknowledge()} method of a received message from a closed session must throw an
    * {@code IllegalStateException}.
    */
   @Test
   public void testAcknowledge() {
      try {
         if (receiverSession != null) {
            receiverSession.close();
         }
         receiverSession = receiverConnection.createQueueSession(false, Session.CLIENT_ACKNOWLEDGE);
         receiver = receiverSession.createReceiver(receiverQueue);

         Message message = senderSession.createMessage();
         sender.send(message);

         Message m = receiver.receive(TestConfig.TIMEOUT);
         receiverSession.close();
         m.acknowledge();
         Assert.fail("sec. 4.4.1 Invoking the acknowledge method of a received message from a closed " + " session must throw an [javax.jms.]IllegalStateException.\n");
      } catch (javax.jms.IllegalStateException e) {
      } catch (JMSException e) {
         Assert.fail("Should raise a javax.jms.IllegalStateException, not a " + e);
      } catch (java.lang.IllegalStateException e) {
         Assert.fail("sec. 4.4.1 Invoking the acknowledge method of a received message from a closed " + "session must throw an [javax.jms.]IllegalStateException, " + "[not a java.lang.IllegalStateException]");
      }
   }

   /**
    * Test that it is valid to use message objects created or received via the [closed] session with the exception of a
    * received message {@code acknowledge()} method.
    */
   @Test
   public void testUseMessage() {
      try {
         TextMessage message = senderSession.createTextMessage();
         message.setText("testUseMessage");
         sender.send(message);

         TextMessage m = (TextMessage) receiver.receive(TestConfig.TIMEOUT);
         receiverSession.close();
         Assert.assertEquals("testUseMessage", m.getText());
      } catch (Exception e) {
         Assert.fail("sec. 4.4.1 It is valid to continue to use message objects created or received via " + "the [closed] session.\n");
      }
   }

   /**
    * Test that an attempt to use a {@code Session} which has been closed throws a
    * {@code javax.jms.IllegalStateException}.
    */
   @Test
   public void testUsedClosedSession() {
      try {
         senderSession.close();
         senderSession.createMessage();
         Assert.fail("sec. 4.4.1 An attempt to use [a closed session] must throw a [javax.jms.]IllegalStateException.\n");
      } catch (javax.jms.IllegalStateException e) {
      } catch (JMSException e) {
         Assert.fail("Should raise a javax.jms.IllegalStateException, not a " + e);
      } catch (java.lang.IllegalStateException e) {
         Assert.fail("Should raise a javax.jms.IllegalStateException, not a java.lang.IllegalStateException");
      }
   }

   /**
    * Test that closing a closed session does <strong>not</strong> throw an exception.
    */
   @Test
   public void testCloseClosedSession() {
      try {
         // senderSession is already started
         // we close it once
         senderSession.close();
         // we close it a second time
         senderSession.close();
      } catch (Exception e) {
         Assert.fail("sec. 4.4.1 Closing a closed session must NOT throw an exception.\n");
      }
   }
}

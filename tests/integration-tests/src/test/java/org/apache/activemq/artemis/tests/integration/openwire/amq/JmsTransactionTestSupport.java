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
package org.apache.activemq.artemis.tests.integration.openwire.amq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQPrefetchPolicy;
import org.apache.activemq.artemis.tests.integration.openwire.BasicOpenWireTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * adapted from: org.apache.activemq.JmsTransactionTestSupport
 */
public abstract class JmsTransactionTestSupport extends BasicOpenWireTest implements MessageListener {

   private static final int MESSAGE_COUNT = 5;
   private static final String MESSAGE_TEXT = "message";

   protected boolean topic = true;

   protected Session session;
   protected MessageConsumer consumer;
   protected MessageProducer producer;
   protected JmsResourceProvider resourceProvider;
   protected Destination destination;
   protected int batchCount = 10;
   protected int batchSize = 20;

   private List<Message> unackMessages = new ArrayList<>(MESSAGE_COUNT);
   private List<Message> ackMessages = new ArrayList<>(MESSAGE_COUNT);
   private boolean resendPhase;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      resourceProvider = getJmsResourceProvider();
      topic = resourceProvider.isTopic();
      // We will be using transacted sessions.
      setSessionTransacted();
      reconnect();
   }

   protected void reconnect() throws Exception {

      if (connection != null) {
         // Close the prev connection.
         connection.close();
      }
      session = null;
      connection = (ActiveMQConnection) resourceProvider.createConnection(this.factory);
      reconnectSession();
      connection.start();
   }

   /**
    * Recreates the connection.
    */
   protected void reconnectSession() throws JMSException {
      if (session != null) {
         session.close();
      }

      session = resourceProvider.createSession(connection);
      destination = resourceProvider.createDestination(session, this);
      producer = resourceProvider.createProducer(session, destination);
      consumer = resourceProvider.createConsumer(session, destination);
   }

   protected void setSessionTransacted() {
      resourceProvider.setTransacted(true);
   }

   protected void beginTx() throws Exception {
      // no-op for local tx
   }

   protected void commitTx() throws Exception {
      session.commit();
   }

   protected void rollbackTx() throws Exception {
      session.rollback();
   }

   /**
    * Sends a batch of messages and validates that the messages are received.
    */
   @Test
   public void testSendReceiveTransactedBatches() throws Exception {

      TextMessage message = session.createTextMessage("Batch Message");
      for (int j = 0; j < batchCount; j++) {
         beginTx();
         for (int i = 0; i < batchSize; i++) {
            producer.send(message);
         }
         messageSent();
         commitTx();

         beginTx();
         for (int i = 0; i < batchSize; i++) {
            message = (TextMessage) consumer.receive(1000 * 5);
            assertNotNull(message, "Received only " + i + " messages in batch " + j);
            assertEquals("Batch Message", message.getText());
         }

         commitTx();
      }
   }

   protected void messageSent() throws Exception {
   }

   /**
    * Sends a batch of messages and validates that the rollbacked message was not consumed.
    */
   @Test
   public void testSendRollback() throws Exception {
      Message[] outbound = new Message[]{session.createTextMessage("First Message"), session.createTextMessage("Second Message")};

      // sends a message
      beginTx();
      producer.send(outbound[0]);
      commitTx();

      // sends a message that gets rollbacked
      beginTx();
      producer.send(session.createTextMessage("I'm going to get rolled back."));
      rollbackTx();

      // sends a message
      beginTx();
      producer.send(outbound[1]);
      commitTx();

      // receives the first message
      beginTx();
      List<Message> messages = new ArrayList<>();
      Message message = consumer.receive(1000);
      messages.add(message);

      // receives the second message
      message = consumer.receive(4000);
      messages.add(message);

      // validates that the rollbacked was not consumed
      commitTx();
      Message[] inbound = new Message[messages.size()];
      messages.toArray(inbound);
      assertTextMessagesEqual("Rollback did not work.", outbound, inbound);
   }

   /**
    * spec section 3.6 acking a message with automation acks has no effect.
    */
   @Test
   public void testAckMessageInTx() throws Exception {
      Message[] outbound = new Message[]{session.createTextMessage("First Message")};

      // sends a message
      beginTx();
      producer.send(outbound[0]);
      outbound[0].acknowledge();
      commitTx();
      outbound[0].acknowledge();

      // receives the first message
      beginTx();
      List<Message> messages = new ArrayList<>();
      Message message = consumer.receive(1000);
      messages.add(message);

      // validates that the rollbacked was not consumed
      commitTx();
      Message[] inbound = new Message[messages.size()];
      messages.toArray(inbound);
      assertTextMessagesEqual("Message not delivered.", outbound, inbound);
   }

   /**
    * Sends a batch of messages and validates that the message sent before session close is not consumed.
    * <p>
    * This test only works with local transactions, not xa.
    */
   @Test
   public void testSendSessionClose() throws Exception {
      Message[] outbound = new Message[]{session.createTextMessage("First Message"), session.createTextMessage("Second Message")};

      // sends a message
      beginTx();
      producer.send(outbound[0]);
      commitTx();

      // sends a message that gets rollbacked
      beginTx();
      producer.send(session.createTextMessage("I'm going to get rolled back."));
      consumer.close();

      reconnectSession();

      // sends a message
      producer.send(outbound[1]);
      commitTx();

      // receives the first message
      List<Message> messages = new ArrayList<>();
      beginTx();
      Message message = consumer.receive(1000);
      messages.add(message);

      // receives the second message
      message = consumer.receive(4000);
      messages.add(message);

      // validates that the rollbacked was not consumed
      commitTx();
      Message[] inbound = new Message[messages.size()];
      messages.toArray(inbound);
      assertTextMessagesEqual("Rollback did not work.", outbound, inbound);
   }

   /**
    * Sends a batch of messages and validates that the message sent before session close is not consumed.
    */
   @Test
   public void testSendSessionAndConnectionClose() throws Exception {
      Message[] outbound = new Message[]{session.createTextMessage("First Message"), session.createTextMessage("Second Message")};

      // sends a message
      beginTx();
      producer.send(outbound[0]);
      commitTx();

      // sends a message that gets rollbacked
      beginTx();
      producer.send(session.createTextMessage("I'm going to get rolled back."));
      consumer.close();
      session.close();

      reconnect();

      // sends a message
      beginTx();
      producer.send(outbound[1]);
      commitTx();

      // receives the first message
      List<Message> messages = new ArrayList<>();
      beginTx();
      Message message = consumer.receive(1000);
      messages.add(message);

      // receives the second message
      message = consumer.receive(4000);
      messages.add(message);

      // validates that the rollbacked was not consumed
      commitTx();
      Message[] inbound = new Message[messages.size()];
      messages.toArray(inbound);
      assertTextMessagesEqual("Rollback did not work.", outbound, inbound);
   }

   /**
    * Sends a batch of messages and validates that the rollbacked message was redelivered.
    */
   @Test
   public void testReceiveRollback() throws Exception {
      Message[] outbound = new Message[]{session.createTextMessage("First Message"), session.createTextMessage("Second Message")};

      // lets consume any outstanding messages from prev test runs
      beginTx();
      while (consumer.receive(1000) != null) {
      }
      commitTx();

      // sent both messages
      beginTx();
      producer.send(outbound[0]);
      producer.send(outbound[1]);
      commitTx();


      List<Message> messages = new ArrayList<>();
      beginTx();
      Message message = consumer.receive(1000);
      messages.add(message);
      assertEquals(outbound[0], message);
      commitTx();

      // rollback so we can get that last message again.
      beginTx();
      message = consumer.receive(1000);
      assertNotNull(message);
      assertEquals(outbound[1], message);
      rollbackTx();

      // Consume again.. the prev message should
      // get redelivered.
      beginTx();
      message = consumer.receive(5000);
      assertNotNull(message, "Should have re-received the message again!");
      messages.add(message);
      commitTx();

      Message[] inbound = new Message[messages.size()];
      messages.toArray(inbound);
      assertTextMessagesEqual("Rollback did not work", outbound, inbound);
   }

   /**
    * Sends a batch of messages and validates that the rollbacked message was redelivered.
    */
   @Test
   public void testReceiveTwoThenRollback() throws Exception {
      Message[] outbound = new Message[]{session.createTextMessage("First Message"), session.createTextMessage("Second Message")};

      //
      beginTx();
      producer.send(outbound[0]);
      producer.send(outbound[1]);
      commitTx();

      List<Message> messages = new ArrayList<>();
      beginTx();
      TextMessage message = (TextMessage) consumer.receive(1000);
      assertEquals(outbound[0], message);

      message = (TextMessage) consumer.receive(1000);
      assertNotNull(message);
      assertEquals(outbound[1], message);

      message = (TextMessage) consumer.receiveNoWait();
      assertNull(message);

      rollbackTx();

      // Consume again.. the prev message should
      // get redelivered.
      beginTx();
      message = (TextMessage) consumer.receive(5000);
      assertNotNull(message, "Should have re-received the first message again!");
      messages.add(message);
      assertEquals(outbound[0], message);
      message = (TextMessage) consumer.receive(5000);
      assertNotNull(message, "Should have re-received the second message again!");
      messages.add(message);
      assertEquals(outbound[1], message);

      message = (TextMessage) consumer.receiveNoWait();
      assertNull(message);
      commitTx();

      Message[] inbound = new Message[messages.size()];
      messages.toArray(inbound);
      assertTextMessagesEqual("Rollback did not work", outbound, inbound);
   }

   /**
    * Sends a batch of messages and validates that the rollbacked message was not consumed.
    */
   @Test
   public void testSendReceiveWithPrefetchOne() throws Exception {
      setPrefetchToOne();
      Message[] outbound = new Message[]{session.createTextMessage("First Message"), session.createTextMessage("Second Message"), session.createTextMessage("Third Message"), session.createTextMessage("Fourth Message")};

      beginTx();
      for (int i = 0; i < outbound.length; i++) {
         // sends a message
         producer.send(outbound[i]);
      }
      commitTx();

      // receives the first message
      beginTx();
      for (int i = 0; i < outbound.length; i++) {
         Message message = consumer.receive(1000);
         assertNotNull(message);
      }

      // validates that the rollbacked was not consumed
      commitTx();
   }

   /**
    * Perform the test that validates if the rollbacked message was redelivered multiple times.
    */
   @Test
   public void testReceiveTwoThenRollbackManyTimes() throws Exception {
      testReceiveTwoThenRollback();
   }

   /**
    * Sends a batch of messages and validates that the rollbacked message was not consumed. This test differs by setting
    * the message prefetch to one.
    */
   @Test
   public void testSendRollbackWithPrefetchOfOne() throws Exception {
      setPrefetchToOne();
      testSendRollback();
   }

   /**
    * Sends a batch of messages and and validates that the rollbacked message was redelivered. This test differs by
    * setting the message prefetch to one.
    */
   @Test
   public void testReceiveRollbackWithPrefetchOfOne() throws Exception {
      setPrefetchToOne();
      testReceiveRollback();
   }

   /**
    * Tests if the messages can still be received if the consumer is closed (session is not closed).
    *
    * @throws Exception see http://jira.codehaus.org/browse/AMQ-143
    */
   @Test
   public void testCloseConsumerBeforeCommit() throws Exception {
      TextMessage[] outbound = new TextMessage[]{session.createTextMessage("First Message"), session.createTextMessage("Second Message")};

      // sends the messages
      beginTx();
      producer.send(outbound[0]);
      producer.send(outbound[1]);
      commitTx();

      beginTx();
      TextMessage message = (TextMessage) consumer.receive(1000);
      assertEquals(outbound[0].getText(), message.getText());
      // Close the consumer before the commit. This should not cause the
      // received message
      // to rollback.
      consumer.close();
      commitTx();

      // Create a new consumer
      consumer = resourceProvider.createConsumer(session, destination);

      beginTx();
      message = (TextMessage) consumer.receive(1000);
      assertEquals(outbound[1].getText(), message.getText());
      commitTx();
   }

   @Test
   public void testChangeMutableObjectInObjectMessageThenRollback() throws Exception {
      ArrayList<String> list = new ArrayList<>();
      list.add("First");
      Message outbound = session.createObjectMessage(list);
      outbound.setStringProperty("foo", "abc");

      beginTx();
      producer.send(outbound);
      commitTx();

      beginTx();
      Message message = consumer.receive(5000);

      List<String> body = assertReceivedObjectMessageWithListBody(message);

      // now lets try mutate it
      try {
         message.setStringProperty("foo", "def");
         fail("Cannot change properties of the object!");
      } catch (JMSException e) {
         System.out.println("Caught expected exception: " + e);
         e.printStackTrace();
      }
      body.clear();
      body.add("This should never be seen!");
      rollbackTx();

      beginTx();
      message = consumer.receive(5000);
      List<String> secondBody = assertReceivedObjectMessageWithListBody(message);
      assertNotSame(secondBody, body, "Second call should return a different body");
      commitTx();
   }

   @SuppressWarnings("unchecked")
   protected List<String> assertReceivedObjectMessageWithListBody(Message message) throws JMSException {
      assertNotNull(message, "Should have received a message!");
      assertEquals("abc", message.getStringProperty("foo"), "foo header");

      assertInstanceOf(ObjectMessage.class, message, "Should be an object message but was: " + message);
      ObjectMessage objectMessage = (ObjectMessage) message;
      List<String> body = (List<String>) objectMessage.getObject();

      assertEquals(1, body.size(), "Size of list should be 1");
      assertEquals("First", body.get(0), "element 0 of list");
      return body;
   }

   /**
    * Sets the prefeftch policy to one.
    */
   protected void setPrefetchToOne() {
      ActiveMQPrefetchPolicy prefetchPolicy = getPrefetchPolicy();
      prefetchPolicy.setQueuePrefetch(1);
      prefetchPolicy.setTopicPrefetch(1);
      prefetchPolicy.setDurableTopicPrefetch(1);
      prefetchPolicy.setOptimizeDurableTopicPrefetch(1);
   }

   protected ActiveMQPrefetchPolicy getPrefetchPolicy() {
      return connection.getPrefetchPolicy();
   }

   // This test won't work with xa tx so no beginTx() has been added.
   @Test
   public void testMessageListener() throws Exception {
      // send messages
      for (int i = 0; i < MESSAGE_COUNT; i++) {
         producer.send(session.createTextMessage(MESSAGE_TEXT + i));
      }
      commitTx();
      consumer.setMessageListener(this);
      // wait receive
      waitReceiveUnack();
      assertEquals(MESSAGE_COUNT, unackMessages.size());
      // resend phase
      waitReceiveAck();
      assertEquals(MESSAGE_COUNT, ackMessages.size());
      // should no longer re-receive
      consumer.setMessageListener(null);
      assertNull(consumer.receiveNoWait());
      reconnect();
   }

   @Override
   public void onMessage(Message message) {
      if (!resendPhase) {
         unackMessages.add(message);
         if (unackMessages.size() == MESSAGE_COUNT) {
            try {
               rollbackTx();
               resendPhase = true;
            } catch (Exception e) {
               e.printStackTrace();
            }
         }
      } else {
         ackMessages.add(message);
         if (ackMessages.size() == MESSAGE_COUNT) {
            try {
               commitTx();
            } catch (Exception e) {
               e.printStackTrace();
            }
         }
      }
   }

   private void waitReceiveUnack() throws Exception {
      for (int i = 0; i < 100 && !resendPhase; i++) {
         Thread.sleep(100);
      }
      assertTrue(resendPhase);
   }

   private void waitReceiveAck() throws Exception {
      for (int i = 0; i < 100 && ackMessages.size() < MESSAGE_COUNT; i++) {
         Thread.sleep(100);
      }
      assertFalse(ackMessages.size() < MESSAGE_COUNT);
   }

   protected abstract JmsResourceProvider getJmsResourceProvider();

}

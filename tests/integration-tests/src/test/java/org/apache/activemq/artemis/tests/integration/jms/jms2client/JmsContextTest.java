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
package org.apache.activemq.artemis.tests.integration.jms.jms2client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.BytesMessage;
import javax.jms.CompletionListener;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.IllegalStateRuntimeException;
import javax.jms.InvalidDestinationRuntimeException;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSProducer;
import javax.jms.JMSRuntimeException;
import javax.jms.Message;
import javax.jms.MessageFormatRuntimeException;
import javax.jms.MessageListener;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JmsContextTest extends JMSTestBase {

   private JMSContext context;
   private final Random random = new Random();
   private Queue queue1;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      context = createContext();
      queue1 = createQueue(JmsContextTest.class.getSimpleName() + "Queue");
   }

   @Test
   public void testCreateContext() {
      assertNotNull(context);
   }

   @Test
   public void testRollbackTest() {
      JMSContext ctx = addContext(cf.createContext(JMSContext.SESSION_TRANSACTED));

      JMSProducer producer = ctx.createProducer();
      JMSConsumer cons = ctx.createConsumer(queue1);

      producer.send(queue1, context.createTextMessage("hello"));

      ctx.rollback();

      assertNull(cons.receiveNoWait());

      producer.send(queue1, context.createTextMessage("hello"));

      ctx.commit();

      assertNotNull(cons.receiveNoWait());

      ctx.commit();

      ctx.rollback();

      assertNull(cons.receiveNoWait());

      cons.close();

   }

   @Test
   public void testDupsOK() {
      JMSContext ctx = addContext(cf.createContext(JMSContext.DUPS_OK_ACKNOWLEDGE));
      assertEquals(JMSContext.DUPS_OK_ACKNOWLEDGE, ctx.getSessionMode());

      ctx.close();
      ctx = addContext(cf.createContext(JMSContext.SESSION_TRANSACTED));
      assertEquals(JMSContext.SESSION_TRANSACTED, ctx.getSessionMode());

      ctx.close();
      ctx = addContext(cf.createContext(JMSContext.CLIENT_ACKNOWLEDGE));
      assertEquals(JMSContext.CLIENT_ACKNOWLEDGE, ctx.getSessionMode());

      ctx.close();
      ctx = addContext(cf.createContext(JMSContext.AUTO_ACKNOWLEDGE));
      assertEquals(JMSContext.AUTO_ACKNOWLEDGE, ctx.getSessionMode());

   }

   @Test
   public void testReceiveBytes() throws Exception {
      JMSProducer producer = context.createProducer();

      JMSConsumer consumer = context.createConsumer(queue1);

      BytesMessage bytesSend = context.createBytesMessage();
      bytesSend.writeByte((byte) 1);
      bytesSend.writeLong(2L);
      producer.send(queue1, bytesSend);

      BytesMessage msgReceived = (BytesMessage) consumer.receiveNoWait();

      byte[] bytesArray = msgReceived.getBody(byte[].class);

      assertEquals((byte) 1, msgReceived.readByte());
      assertEquals(2L, msgReceived.readLong());

      DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(bytesArray));

      assertEquals((byte) 1, dataInputStream.readByte());
      assertEquals(2L, dataInputStream.readLong());

   }

   @Test
   public void testReceiveText() throws Exception {
      JMSProducer producer = context.createProducer();

      JMSConsumer consumer = context.createConsumer(queue1);

      String randomStr = newXID().toString();

      TextMessage sendMsg = context.createTextMessage(randomStr);
      producer.send(queue1, sendMsg);

      TextMessage receiveMsg = (TextMessage) consumer.receiveNoWait();

      assertEquals(randomStr, receiveMsg.getText());

   }

   @Test
   public void testDelay() throws Exception {
      JMSProducer producer = context.createProducer();

      JMSConsumer consumer = context.createConsumer(queue1);

      producer.setDeliveryDelay(500);

      long timeStart = System.currentTimeMillis();

      String strRandom = newXID().toString();

      producer.send(queue1, context.createTextMessage(strRandom));

      TextMessage msg = (TextMessage) consumer.receive(2500);

      assertNotNull(msg);

      long actualDelay = System.currentTimeMillis() - timeStart;
      assertTrue(actualDelay >= 500 && actualDelay < 2000, "delay is not working, actualDelay=" + actualDelay);

      assertEquals(strRandom, msg.getText());
   }

   @Test
   public void testExpire() throws Exception {
      JMSProducer producer = context.createProducer();

      producer.setTimeToLive(500);

      String strRandom = newXID().toString();

      producer.send(queue1, context.createTextMessage(strRandom));

      Thread.sleep(700);

      // Create consumer after message is expired, making it to expire at the server's
      JMSConsumer consumer = context.createConsumer(queue1);

      TextMessage msg = (TextMessage) consumer.receiveNoWait();

      // Time-to-live kicked in, so it's supposed to return null
      assertNull(msg);

      strRandom = newXID().toString();

      producer.send(queue1, context.createTextMessage(strRandom));

      Thread.sleep(700);

      // Receive second message, expiring on client
      msg = (TextMessage) consumer.receiveNoWait();

      assertNull(msg);

      strRandom = newXID().toString();

      producer.send(queue1, context.createTextMessage(strRandom));

      // will receive a message that's not expired now
      msg = (TextMessage) consumer.receiveNoWait();

      assertNotNull(msg);

      assertEquals(strRandom, msg.getText());

   }

   @Test
   public void testDeliveryMode() throws Exception {
      JMSProducer producer = context.createProducer();

      JMSConsumer consumer = context.createConsumer(queue1);

      producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

      String strRandom = newXID().toString();

      producer.send(queue1, context.createTextMessage(strRandom));

      TextMessage msg = (TextMessage) consumer.receiveNoWait();

      assertNotNull(msg);

      assertEquals(DeliveryMode.NON_PERSISTENT, msg.getJMSDeliveryMode());

   }

   @Test
   public void testInvalidMessage() {
      JMSProducer producer = context.createProducer();
      try {
         producer.send(queue1, (Message) null);
         fail("null msg");
      } catch (MessageFormatRuntimeException expected) {
         // no-op
      }
   }

   @Test
   public void testInvalidDestination() {
      JMSProducer producer = context.createProducer();
      Message msg = context.createMessage();
      try {
         producer.send((Destination) null, msg);
         fail("null Destination");
      } catch (InvalidDestinationRuntimeException expected) {
         // no-op
      }
   }

   @Test
   public void testSendStreamMessage() throws Exception {
      JmsProducerCompletionListenerTest.CountingCompletionListener cl = new JmsProducerCompletionListenerTest.CountingCompletionListener(1);
      JMSProducer producer = context.createProducer();
      producer.setAsync(cl);
      StreamMessage msg = context.createStreamMessage();
      msg.setStringProperty("name", name);
      String bprop = "booleanProp";
      String iprop = "intProp";
      msg.setBooleanProperty(bprop, true);
      msg.setIntProperty(iprop, 42);
      msg.writeBoolean(true);
      msg.writeInt(67);
      producer.send(queue1, msg);
      JMSConsumer consumer = context.createConsumer(queue1);
      Message msg2 = consumer.receive(100);
      assertNotNull(msg2);
      assertTrue(cl.completionLatch.await(1, TimeUnit.SECONDS));
      StreamMessage sm = (StreamMessage) cl.lastMessage;
      assertTrue(sm.getBooleanProperty(bprop));
      assertEquals(42, sm.getIntProperty(iprop));
      assertTrue(sm.readBoolean());
      assertEquals(67, sm.readInt());
   }

   @Test
   public void testSetClientIdLate() {
      JMSProducer producer = context.createProducer();
      Message msg = context.createMessage();
      producer.send(queue1, msg);
      try {
         context.setClientID("id");
         fail("expected exception");
      } catch (IllegalStateRuntimeException e) {
         // no op
      }
   }

   @Test
   public void testCloseSecondContextConnectionRemainsOpen() throws JMSException {
      JMSContext localContext = context.createContext(JMSContext.CLIENT_ACKNOWLEDGE);
      assertEquals(JMSContext.CLIENT_ACKNOWLEDGE, localContext.getSessionMode(), "client_ack");
      JMSProducer producer = localContext.createProducer();
      JMSConsumer consumer = localContext.createConsumer(queue1);

      final int pass = 1;
      for (int idx = 0; idx < 2; idx++) {
         Message m = localContext.createMessage();
         int intProperty = random.nextInt();
         m.setIntProperty("random", intProperty);
         assertNotNull(m);
         producer.send(queue1, m);
         m = null;
         Message msg = consumer.receive(100);
         assertNotNull(msg, "must have a msg");
         assertEquals(intProperty, msg.getIntProperty("random"));
         // In the second pass we close the connection before ack'ing
         if (idx == pass) {
            localContext.close();
         }
         /*
          * From JMSContext.close() javadoc:
          *
          * Invoking the acknowledge method of a received message from a closed connection's session must throw an
          * IllegalStateRuntimeException. Closing a closed connection must NOT throw an exception.
          */
         try {
            msg.acknowledge();
            assertEquals(0, idx, "connection should be open on pass 0. It is " + pass);
         } catch (javax.jms.IllegalStateException expected) {
            // JMSContext javadoc says we must expect an IllegalStateRuntimeException here. But Message.ack...() says it
            // must throws the non-runtime variant.
            assertEquals(pass, idx, "we only close the connection on pass " + pass);
         }
      }
   }

   @Test
   public void testInvalidSessionModesValueMinusOne() {
      assertThrows(JMSRuntimeException.class, () -> {
         context.createContext(-1);
      });
   }

   @Test
   public void testInvalidSessionModesValue4() {
      assertThrows(JMSRuntimeException.class, () -> {
         context.createContext(4);
      });
   }

   @Test
   public void testGetAnotherContextFromIt() {
      JMSContext c2 = context.createContext(Session.DUPS_OK_ACKNOWLEDGE);
      assertNotNull(c2);
      assertEquals(Session.DUPS_OK_ACKNOWLEDGE, c2.getSessionMode());
      Message m2 = c2.createMessage();
      assertNotNull(m2);
      c2.close(); // should close its session, but not its (shared) connection
      try {
         c2.createMessage();
         fail("session should be closed...");
      } catch (JMSRuntimeException expected) {
         // expected
      }
      Message m1 = context.createMessage();
      assertNotNull(m1, "connection must be open");
   }

   @Test
   public void testSetGetClientIdNewContext() {
      final String id = "123";
      JMSContext c = context;// createContext();
      c.setClientID(id);
      JMSContext c2 = addContext(c.createContext(Session.CLIENT_ACKNOWLEDGE));
      assertEquals(id, c2.getClientID());
   }

   @Test
   public void testGetClientId() {
      JMSContext context2 = addContext(context.createContext(Session.AUTO_ACKNOWLEDGE));
      final String id = "ID: " + random.nextInt();
      context.setClientID(id);
      assertEquals(id, context2.getClientID(), "id's must match because the connection is shared");
   }

   @Test
   public void testCreateConsumerWithSelector() throws JMSException {
      final String filterName = "magicIndexMessage";
      final int total = 5;
      JMSProducer producer = context.createProducer();
      JMSConsumer consumerNoSelect = context.createConsumer(queue1);
      JMSConsumer consumer = context.createConsumer(queue1, filterName + "=TRUE");
      for (int i = 0; i < total; i++) {
         Message msg = context.createTextMessage("message " + i);
         msg.setBooleanProperty(filterName, i == 3);
         producer.send(queue1, msg);
      }
      Message msg0 = consumer.receive(500);
      assertNotNull(msg0);
      msg0.acknowledge();
      assertNull(consumer.receiveNoWait(), "no more messages");
      for (int i = 0; i < total - 1; i++) {
         Message msg = consumerNoSelect.receive(100);
         assertNotNull(msg);
         msg.acknowledge();
      }
      assertNull(consumerNoSelect.receiveNoWait(), "no more messages");
   }

   @Test
   public void testContextStopAndCloseFromMessageListeners() throws Exception {
      final JMSContext context1 = context.createContext(Session.AUTO_ACKNOWLEDGE);
      JMSConsumer consumer1 = context1.createConsumer(queue1);

      final CountDownLatch latch1 = new CountDownLatch(1);

      InvalidMessageListener listener1 = new InvalidMessageListener(context1, latch1, 1);

      consumer1.setMessageListener(listener1);

      JMSProducer producer = context1.createProducer();
      Message msg = context1.createTextMessage("first message");
      producer.send(queue1, msg);

      latch1.await();

      Throwable error1 = listener1.getError();

      assertNotNull(error1);

      assertInstanceOf(IllegalStateRuntimeException.class, error1);

      context1.close();

      final JMSContext context2 = context.createContext(Session.AUTO_ACKNOWLEDGE);
      JMSConsumer consumer2 = context2.createConsumer(queue1);

      final CountDownLatch latch2 = new CountDownLatch(1);

      InvalidMessageListener listener2 = new InvalidMessageListener(context2, latch2, 2);

      consumer2.setMessageListener(listener2);

      JMSProducer producer2 = context2.createProducer();
      Message msg2 = context2.createTextMessage("second message");
      producer2.send(queue1, msg2);

      latch2.await();

      Throwable error2 = listener2.getError();

      assertNotNull(error2);

      assertInstanceOf(IllegalStateRuntimeException.class, error2);

      context2.close();
   }

   @Test
   public void recoverAckTest() throws Exception {
      // Create JMSContext with CLIENT_ACKNOWLEDGE

      try (JMSContext context = cf.createContext(JMSContext.CLIENT_ACKNOWLEDGE)) {
         int numMessages = 10;

         TextMessage textMessage = null;

         // Create JMSConsumer from JMSContext
         JMSConsumer consumer = context.createConsumer(queue1);

         // Create JMSProducer from JMSContext
         JMSProducer producer = context.createProducer();

         // send messages
         for (int i = 0; i < numMessages; i++) {
            String message = "text message " + i;
            textMessage = context.createTextMessage(message);
            textMessage.setStringProperty("COM_SUN_JMS_TESTNAME", "recoverAckTest" + i);
            producer.send(queue1, textMessage);
         }

         // receive messages but do not acknowledge
         for (int i = 0; i < numMessages; i++) {
            textMessage = (TextMessage) consumer.receive(5000);
            assertNotNull(textMessage);
         }

         context.recover();

         // receive messages a second time followed by acknowledge
         for (int i = 0; i < numMessages; i++) {
            textMessage = (TextMessage) consumer.receive(5000);
            assertNotNull(textMessage);
         }

         // Acknowledge all messages
         context.acknowledge();
      }

      // doing this check with another context / consumer to make sure it was acked.
      try (JMSContext context = cf.createContext(JMSContext.CLIENT_ACKNOWLEDGE)) {
         // Create JMSConsumer from JMSContext
         JMSConsumer consumer = context.createConsumer(queue1);

         assertNull(consumer.receiveNoWait());
      }
   }

   @Test
   public void receiveNullAckTest() throws Exception {
      // Create JMSContext with CLIENT_ACKNOWLEDGE

      try (JMSContext context = cf.createContext(JMSContext.CLIENT_ACKNOWLEDGE)) {
         int numMessages = 10;

         TextMessage textMessage = null;

         // Create JMSConsumer from JMSContext
         JMSConsumer consumer = context.createConsumer(queue1);

         // Create JMSProducer from JMSContext
         JMSProducer producer = context.createProducer();

         // send messages
         for (int i = 0; i < numMessages; i++) {
            String message = "text message " + i;
            textMessage = context.createTextMessage(message);
            textMessage.setStringProperty("COM_SUN_JMS_TESTNAME", "recoverAckTest" + i);
            producer.send(queue1, textMessage);
         }

         // receive messages but do not acknowledge
         for (int i = 0; i < numMessages; i++) {
            textMessage = (TextMessage) consumer.receive(5000);
            assertNotNull(textMessage);
         }

         assertNull(consumer.receiveNoWait());

         // Acknowledge all messages
         context.acknowledge();
      }

      // doing this check with another context / consumer to make sure it was acked.
      try (JMSContext context = cf.createContext(JMSContext.CLIENT_ACKNOWLEDGE)) {
         // Create JMSConsumer from JMSContext
         JMSConsumer consumer = context.createConsumer(queue1);

         assertNull(consumer.receiveNoWait());
      }
   }

   @Test
   public void bytesMessage() throws Exception {
      context = cf.createContext();
      try {
         JMSProducer producer = context.createProducer();
         BytesMessage bMsg = context.createBytesMessage();
         bMsg.setStringProperty("COM_SUN_JMS_TESTNAME", "sendAndRecvMsgOfEachTypeCLTest");
         bMsg.writeByte((byte) 1);
         bMsg.writeInt(22);
         CountDownLatch latch = new CountDownLatch(1);
         SimpleCompletionListener listener = new SimpleCompletionListener(latch);
         producer.setAsync(listener);
         producer.send(queue1, bMsg);
         assertTrue(latch.await(5, TimeUnit.SECONDS));
         assertEquals((byte) 1, listener.message.readByte());
         assertEquals(22, listener.message.readInt());
      } finally {
         context.close();
      }
   }

   @Test
   public void illegalStateRuntimeExceptionTests() throws Exception {
      JMSProducer producer = context.createProducer();
      JMSConsumer consumer = context.createConsumer(queue1);
      TextMessage expTextMessage = context.createTextMessage("Call commit");
      CountDownLatch latch = new CountDownLatch(1);
      JMSCOntextStopCompletionListener listener = new JMSCOntextStopCompletionListener(context, latch);
      producer.setAsync(listener);
      producer.send(queue1, expTextMessage);
      assertTrue(latch.await(5, TimeUnit.SECONDS));
      assertNull(listener.ex);
   }

   private static class SimpleCompletionListener implements CompletionListener {

      private CountDownLatch latch;
      private BytesMessage message;

      private SimpleCompletionListener(CountDownLatch latch) {
         this.latch = latch;
      }

      @Override
      public void onCompletion(Message message) {
         this.message = (BytesMessage) message;
         latch.countDown();
      }

      @Override
      public void onException(Message message, Exception exception) {
         //To change body of implemented methods use File | Settings | File Templates.
      }
   }

   private static class InvalidMessageListener implements MessageListener {

      private int id;
      private CountDownLatch latch;
      private JMSContext context;
      private volatile Throwable error;

      private InvalidMessageListener(JMSContext context, CountDownLatch latch, int id) {
         this.id = id;
         this.latch = latch;
         this.context = context;
      }

      public Throwable getError() {
         return error;
      }

      @Override
      public void onMessage(Message arg0) {
         switch (id) {
            case 1:
               stopContext();
               break;
            case 2:
               closeContext();
               break;
            default:
               break;
         }
         latch.countDown();
      }

      private void stopContext() {
         try {
            context.stop();
         } catch (Throwable t) {
            error = t;
         }
      }

      private void closeContext() {
         try {
            context.close();
         } catch (Throwable t) {
            error = t;
         }
      }

   }

   private class JMSCOntextStopCompletionListener implements CompletionListener {

      private JMSContext context;
      private CountDownLatch latch;
      private Exception ex;

      private JMSCOntextStopCompletionListener(JMSContext context, CountDownLatch latch) {
         this.context = context;
         this.latch = latch;
      }

      @Override
      public void onCompletion(Message message) {
         try {
            context.stop();
         } catch (Exception e) {
            this.ex = e;
         }
         latch.countDown();
      }

      @Override
      public void onException(Message message, Exception exception) {
      }
   }
}

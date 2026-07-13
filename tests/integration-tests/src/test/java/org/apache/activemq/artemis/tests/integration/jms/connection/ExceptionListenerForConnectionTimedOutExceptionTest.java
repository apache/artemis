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
package org.apache.activemq.artemis.tests.integration.jms.connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import org.apache.activemq.artemis.api.core.ActiveMQConnectionTimedOutException;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class ExceptionListenerForConnectionTimedOutExceptionTest extends JMSTestBase {

   private Queue queue;

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();

      queue = createQueue("TestQueue");
   }

   @Test
   @Timeout(60)
   public void testOnAcknowledge() throws Exception {
      testOnAcknowledge(false);
   }

   @Test
   @Timeout(60)
   public void testOnAcknowledgeBlockOnFailover() throws Exception {
      // this is validating a case where failover would block
      // and yet the exception should already happen asynchronously
      testOnAcknowledge(true);
   }

   public void testOnAcknowledge(boolean blockOnFailover) throws Exception {
      mayBlock.set(blockOnFailover);
      Connection sendConnection = null;
      Connection connection = null;
      AtomicReference<JMSException> exceptionOnConnection = new AtomicReference<>();

      try {
         ((ActiveMQConnectionFactory) cf).setOutgoingInterceptorList(OutBoundPacketCapture.class.getName());
         ((ActiveMQConnectionFactory) cf).setIncomingInterceptorList(SessAcknowledgeCauseResponseTimeout.class.getName());
         ((ActiveMQConnectionFactory) cf).setBlockOnAcknowledge(true);
         ((ActiveMQConnectionFactory) cf).setCallTimeout(500);

         sendConnection = cf.createConnection();

         final Session sendSession = sendConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         final MessageProducer producer = sendSession.createProducer(queue);

         TextMessage message = sendSession.createTextMessage();

         message.setText("Message");

         producer.send(message);

         connection = cf.createConnection();
         connection.start();
         connection.setExceptionListener(exceptionOnConnection::set);
         final Session consumerSession = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         final MessageConsumer messageConsumer = consumerSession.createConsumer(queue);

         TextMessage message1 = (TextMessage) messageConsumer.receive(1000);

         assertEquals("Message", message1.getText());

         message1.acknowledge();

         fail("JMSException expected");

      } catch (JMSException e) {
         if (blockOnFailover) {
            Wait.assertTrue(blocked::get);
            unblock();
         }
         assertInstanceOf(ActiveMQConnectionTimedOutException.class, e.getCause());
         //Ensure JMS Connection ExceptionListener was also invoked
         assertTrue(Wait.waitFor(() -> exceptionOnConnection.get() != null, 2000, 100));
         assertInstanceOf(ActiveMQConnectionTimedOutException.class, exceptionOnConnection.get().getCause());
      } finally {
         if (connection != null) {
            connection.close();
         }
         if (sendConnection != null) {
            sendConnection.close();
         }
      }
   }

   @Test
   @Timeout(60)
   public void testOnSend() throws Exception {
      testOnSend(false);
   }

   @Test
   @Timeout(60)
   public void testOnSendBlockOnFailover() throws Exception {
      testOnSend(true);
   }

   public void testOnSend(boolean blockOnFailover) throws Exception {
      mayBlock.set(blockOnFailover);
      Connection sendConnection = null;
      Connection connection = null;
      AtomicReference<JMSException> exceptionOnConnection = new AtomicReference<>();

      try {
         ((ActiveMQConnectionFactory) cf).setOutgoingInterceptorList(OutBoundPacketCapture.class.getName());
         ((ActiveMQConnectionFactory) cf).setIncomingInterceptorList(SessSendCauseResponseTimeout.class.getName());
         ((ActiveMQConnectionFactory) cf).setCallTimeout(500);

         sendConnection = cf.createConnection();
         sendConnection.setExceptionListener(exceptionOnConnection::set);
         final Session sendSession = sendConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         final MessageProducer producer = sendSession.createProducer(queue);

         TextMessage message = sendSession.createTextMessage();

         message.setText("Message");

         producer.send(message);

         fail("JMSException expected");

      } catch (JMSException e) {
         if (blockOnFailover) {
            Wait.assertTrue(blocked::get);
            unblock();
         }
         assertInstanceOf(ActiveMQConnectionTimedOutException.class, e.getCause());
         //Ensure JMS Connection ExceptionListener was also invoked
         assertTrue(Wait.waitFor(() -> exceptionOnConnection.get() != null, 2000, 100));
         assertInstanceOf(ActiveMQConnectionTimedOutException.class, exceptionOnConnection.get().getCause());

      } finally {
         if (connection != null) {
            connection.close();
         }
         if (sendConnection != null) {
            sendConnection.close();
         }
      }
   }

   static AtomicBoolean mayBlock = new AtomicBoolean(true);
   static AtomicBoolean blocked = new AtomicBoolean(false);

   private static void block() {
      if (!mayBlock.get()) {
         return;
      }

      blocked.set(true);

      try {
         long timeOut = System.currentTimeMillis() + 5000;
         while (mayBlock.get() && System.currentTimeMillis() < timeOut) {
            Thread.yield();
         }
      } finally {
         blocked.set(false);
      }
   }

   private static void unblock() {
      mayBlock.set(false);
   }


   static Packet lastPacketSent;

   public static class OutBoundPacketCapture implements Interceptor {

      @Override
      public boolean intercept(Packet packet, RemotingConnection connection) throws ActiveMQException {
         lastPacketSent = packet;
         return true;
      }
   }

   public static class SessAcknowledgeCauseResponseTimeout implements Interceptor {

      @Override
      public boolean intercept(Packet packet, RemotingConnection connection) throws ActiveMQException {
         // CheckForFailoverReply is ignored here, as this is simulating an issue where the server is completely not responding, the blocked call should throw an exception asynchrnously to the retry
         if (packet.getType() == PacketImpl.CONNECT_RESPONSE) {
            block();
            return true;
         }

         if (lastPacketSent.getType() == PacketImpl.SESS_ACKNOWLEDGE && packet.getType() == PacketImpl.NULL_RESPONSE) {
            return false;
         }
         return true;
      }
   }

   public static class SessSendCauseResponseTimeout implements Interceptor {

      @Override
      public boolean intercept(Packet packet, RemotingConnection connection) throws ActiveMQException {
         // CheckForFailoverReply is ignored here, as this is simulating an issue where the server is completely not responding, the blocked call should throw an exception asynchrnously to the retry
         if (packet.getType() == PacketImpl.CONNECT_RESPONSE) {
            block();
            return true;
         }

         if (lastPacketSent.getType() == PacketImpl.SESS_SEND && packet.getType() == PacketImpl.NULL_RESPONSE) {
            return false;
         }

         return true;
      }
   }
}

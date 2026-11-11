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
package org.apache.activemq.artemis.tests.integration.interceptors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ClientConsumerImpl;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.ServerSessionPacketHandler;
import org.apache.activemq.artemis.core.protocol.core.impl.ChannelImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.RemotingConnectionImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateQueueMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.MessagePacket;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionCreateConsumerMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionReceiveMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendMessage;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.api.core.RoutingType;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterceptorTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private ActiveMQServer server;

   private final SimpleString QUEUE = SimpleString.of("InterceptorTestQueue");

   private ServerLocator locator;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      server = createServer(false, true);

      server.start();

      locator = createNettyNonHALocator();
   }

   private static final String key = "fruit";

   private class MyInterceptor1 implements Interceptor {

      @Override
      public boolean intercept(final Packet packet, final RemotingConnection connection) throws ActiveMQException {
         if (packet.getType() == PacketImpl.SESS_SEND) {
            SessionSendMessage p = (SessionSendMessage) packet;

            Message sm = p.getMessage();

            sm.putStringProperty(InterceptorTest.key, "orange");
         }

         return true;
      }

   }

   private class InterceptUserOnCreateQueue implements Interceptor {

      @Override
      public boolean intercept(final Packet packet, final RemotingConnection connection) throws ActiveMQException {
         if (packet.getType() == PacketImpl.CREATE_QUEUE || packet.getType() == PacketImpl.CREATE_QUEUE_V2) {
            String userName = getUsername(packet, connection);
            CreateQueueMessage createQueue = (CreateQueueMessage) packet;
            createQueue.setFilterString(SimpleString.of("userName='" + userName + "'"));

            logger.debug("userName on createQueue = {}", userName);
         } else if (packet.getType() == PacketImpl.SESS_SEND) {
            String userName = getUsername(packet, connection);
            MessagePacket msgPacket = (MessagePacket) packet;
            msgPacket.getMessage().putStringProperty("userName", userName);

            logger.debug("userName on send = {}", userName);
         }

         return true;
      }

      public String getUsername(final Packet packet, final RemotingConnection connection) {
         RemotingConnectionImpl impl = (RemotingConnectionImpl) connection;
         ChannelImpl channel = (ChannelImpl) impl.getChannel(packet.getChannelID(), -1);
         ServerSessionPacketHandler sessionHandler = (ServerSessionPacketHandler) channel.getHandler();
         return sessionHandler.getSession().getUsername();
      }

   }

   private class InterceptUserOnCreateConsumer implements Interceptor {

      @Override
      public boolean intercept(final Packet packet, final RemotingConnection connection) throws ActiveMQException {
         if (packet.getType() == PacketImpl.SESS_CREATECONSUMER) {
            String userName = getUsername(packet, connection);
            SessionCreateConsumerMessage createQueue = (SessionCreateConsumerMessage) packet;
            createQueue.setFilterString(SimpleString.of("userName='" + userName + "'"));

            logger.debug("userName = {}", userName);
         } else if (packet.getType() == PacketImpl.SESS_SEND) {
            String userName = getUsername(packet, connection);
            MessagePacket msgPacket = (MessagePacket) packet;
            msgPacket.getMessage().putStringProperty("userName", userName);

            logger.debug("userName on send = {}", userName);
         }

         return true;
      }

      public String getUsername(final Packet packet, final RemotingConnection connection) {
         RemotingConnectionImpl impl = (RemotingConnectionImpl) connection;
         ChannelImpl channel = (ChannelImpl) impl.getChannel(packet.getChannelID(), -1);
         ServerSessionPacketHandler sessionHandler = (ServerSessionPacketHandler) channel.getHandler();
         return sessionHandler.getSession().getUsername();
      }

   }

   private class MyOutgoingInterceptor1 implements Interceptor {

      @Override
      public boolean intercept(final Packet packet, final RemotingConnection connection) throws ActiveMQException {
         if (packet.getType() == PacketImpl.SESS_RECEIVE_MSG) {
            SessionReceiveMessage p = (SessionReceiveMessage) packet;

            Message sm = p.getMessage();

            sm.putStringProperty(InterceptorTest.key, "orange");
         }

         return true;
      }

   }

   private class MyInterceptor2 implements Interceptor {

      @Override
      public boolean intercept(final Packet packet, final RemotingConnection connection) throws ActiveMQException {
         if (packet.getType() == PacketImpl.SESS_SEND) {
            return false;
         }

         return true;
      }

   }

   private class MyOutgoingInterceptor2 implements Interceptor {

      @Override
      public boolean intercept(final Packet packet, final RemotingConnection connection) throws ActiveMQException {
         if (isForceDeliveryResponse(packet)) {
            return true;
         }

         if (packet.getType() == PacketImpl.SESS_RECEIVE_MSG) {
            return false;
         }

         return true;
      }
   }

   private class MyInterceptor3 implements Interceptor {

      @Override
      public boolean intercept(final Packet packet, final RemotingConnection connection) throws ActiveMQException {
         if (packet.getType() == PacketImpl.SESS_RECEIVE_MSG) {
            SessionReceiveMessage p = (SessionReceiveMessage) packet;

            ClientMessage cm = (ClientMessage) p.getMessage();

            cm.putStringProperty(InterceptorTest.key, "orange");
         }

         return true;
      }

   }

   private class MyOutgoingInterceptor3 implements Interceptor {

      @Override
      public boolean intercept(final Packet packet, final RemotingConnection connection) throws ActiveMQException {
         if (packet.getType() == PacketImpl.SESS_SEND) {
            SessionSendMessage p = (SessionSendMessage) packet;

            ClientMessage cm = (ClientMessage) p.getMessage();

            cm.putStringProperty(InterceptorTest.key, "orange");
         }

         return true;
      }

   }

   private class MyInterceptor4 implements Interceptor {

      @Override
      public boolean intercept(final Packet packet, final RemotingConnection connection) throws ActiveMQException {
         if (isForceDeliveryResponse(packet)) {
            return true;
         }

         if (packet.getType() == PacketImpl.SESS_RECEIVE_MSG) {
            return false;
         }

         return true;
      }

   }

   private class MyOutgoingInterceptor4 implements Interceptor {

      @Override
      public boolean intercept(final Packet packet, final RemotingConnection connection) throws ActiveMQException {
         if (isForceDeliveryResponse(packet)) {
            return true;
         }

         if (packet.getType() == PacketImpl.SESS_SEND) {
            return false;
         }

         return true;
      }

   }

   private boolean isForceDeliveryResponse(final Packet packet) {
      if (packet.getType() == PacketImpl.SESS_RECEIVE_MSG) {
         SessionReceiveMessage msg = (SessionReceiveMessage) packet;
         if (msg.getMessage().containsProperty(ClientConsumerImpl.FORCED_DELIVERY_MESSAGE)) {
            return true;
         }
      }

      return false;
   }

   private class MyInterceptor5 implements Interceptor {

      private final String key;

      private final int num;

      private volatile boolean reject;

      private volatile boolean wasCalled;

      MyInterceptor5(final String key, final int num) {
         this.key = key;

         this.num = num;
      }

      public void setReject(final boolean reject) {
         this.reject = reject;
      }

      public boolean wasCalled() {
         return wasCalled;
      }

      public void setWasCalled(final boolean wasCalled) {
         this.wasCalled = wasCalled;
      }

      @Override
      public boolean intercept(final Packet packet, final RemotingConnection connection) throws ActiveMQException {
         if (packet.getType() == PacketImpl.SESS_SEND) {
            SessionSendMessage p = (SessionSendMessage) packet;

            Message sm = p.getMessage();

            sm.putIntProperty(key, num);

            wasCalled = true;

            return !reject;
         }

         return true;

      }

   }

   private class MyInterceptor6 implements Interceptor {

      private final String key;

      private final int num;

      private volatile boolean reject;

      private volatile boolean wasCalled;

      MyInterceptor6(final String key, final int num) {
         this.key = key;

         this.num = num;
      }

      public void setReject(final boolean reject) {
         this.reject = reject;
      }

      public boolean wasCalled() {
         return wasCalled;
      }

      public void setWasCalled(final boolean wasCalled) {
         this.wasCalled = wasCalled;
      }

      @Override
      public boolean intercept(final Packet packet, final RemotingConnection connection) throws ActiveMQException {

         if (isForceDeliveryResponse(packet)) {
            return true;
         }

         if (packet.getType() == PacketImpl.SESS_RECEIVE_MSG) {
            SessionReceiveMessage p = (SessionReceiveMessage) packet;

            Message sm = p.getMessage();

            sm.putIntProperty(key, num);

            wasCalled = true;

            return !reject;
         }

         return true;

      }

   }

   @Test
   public void testServerInterceptorChangeProperty() throws Exception {
      MyInterceptor1 interceptor = new MyInterceptor1();

      server.getRemotingService().addIncomingInterceptor(interceptor);

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true, true);

      session.createQueue(QueueConfiguration.of(QUEUE).setDurable(false));

      ClientProducer producer = session.createProducer(QUEUE);

      final int numMessages = 10;

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         message.putIntProperty("count", i);

         message.putStringProperty(InterceptorTest.key, "apple");

         producer.send(message);
      }

      ClientConsumer consumer = session.createConsumer(QUEUE);

      session.start();

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer.receive(1000);

         assertNotNull(message);

         assertEquals(i, message.getIntProperty("count").intValue());

         assertEquals("orange", message.getStringProperty(InterceptorTest.key));
      }

      server.getRemotingService().removeIncomingInterceptor(interceptor);

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         message.putStringProperty(InterceptorTest.key, "apple");

         producer.send(message);
      }

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer.receive(1000);

         assertEquals("apple", message.getStringProperty(InterceptorTest.key));
      }

      session.close();
   }

   // This is testing if it's possible to intercept usernames and do some real stuff as users want
   @Test
   public void testInterceptUsernameOnQueues() throws Exception {

      SimpleString ANOTHER_QUEUE = QUEUE.concat("another");
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser("dumb", "dumber");
      securityManager.getConfiguration().addUser("an", "other");

      server.getRemotingService().addIncomingInterceptor(new InterceptUserOnCreateQueue());

      locator.setBlockOnDurableSend(true);
      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession("dumb", "dumber", false, false, false, false, 0);

      ClientSession sessionAnotherUser = sf.createSession("an", "other", false, false, false, false, 0);

      session.createQueue(QueueConfiguration.of(QUEUE));

      sessionAnotherUser.createQueue(QueueConfiguration.of(ANOTHER_QUEUE).setAddress(QUEUE));

      ClientProducer prod = session.createProducer(QUEUE);

      ClientProducer prodAnother = sessionAnotherUser.createProducer(QUEUE);

      ClientMessage msg = session.createMessage(true);
      prod.send(msg);
      session.commit();

      prodAnother.send(msg);
      sessionAnotherUser.commit();

      ClientConsumer consumer = session.createConsumer(QUEUE);
      ClientConsumer consumerAnother = sessionAnotherUser.createConsumer(ANOTHER_QUEUE);

      session.start();
      sessionAnotherUser.start();

      msg = consumer.receive(1000);
      assertNotNull(msg);
      assertEquals("dumb", msg.getStringProperty("userName"));
      msg.acknowledge();
      assertNull(consumer.receiveImmediate());

      msg = consumerAnother.receive(1000);
      assertNotNull(msg);
      assertEquals("an", msg.getStringProperty("userName"));
      msg.acknowledge();
      assertNull(consumerAnother.receiveImmediate());

      session.close();
      sessionAnotherUser.close();
   }

   // This is testing if it's possible to intercept usernames and do some real stuff as users want
   @Test
   public void testInterceptUsernameOnConsumer() throws Exception {
      ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();
      securityManager.getConfiguration().addUser("dumb", "dumber");
      securityManager.getConfiguration().addUser("an", "other");

      server.getRemotingService().addIncomingInterceptor(new InterceptUserOnCreateConsumer());

      locator.setBlockOnDurableSend(true);
      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession("dumb", "dumber", false, false, false, false, 0);

      ClientSession sessionAnotherUser = sf.createSession("an", "other", false, false, false, false, 0);

      session.createQueue(QueueConfiguration.of(QUEUE));

      ClientProducer prod = session.createProducer(QUEUE);

      ClientProducer prodAnother = sessionAnotherUser.createProducer(QUEUE);

      ClientMessage msg = session.createMessage(true);
      prod.send(msg);
      session.commit();

      prodAnother.send(msg);
      sessionAnotherUser.commit();

      ClientConsumer consumer = session.createConsumer(QUEUE);
      ClientConsumer consumerAnother = sessionAnotherUser.createConsumer(QUEUE);

      session.start();
      sessionAnotherUser.start();

      msg = consumer.receive(1000);
      assertNotNull(msg);
      assertEquals("dumb", msg.getStringProperty("userName"));
      msg.acknowledge();
      assertNull(consumer.receiveImmediate());

      msg = consumerAnother.receive(1000);
      assertNotNull(msg);
      assertEquals("an", msg.getStringProperty("userName"));
      msg.acknowledge();
      assertNull(consumerAnother.receiveImmediate());

      session.close();
      sessionAnotherUser.close();
   }

   @Test
   public void testServerInterceptorRejectPacket() throws Exception {
      MyInterceptor2 interceptor = new MyInterceptor2();

      server.getRemotingService().addIncomingInterceptor(interceptor);

      locator.setBlockOnNonDurableSend(false);

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true, true);

      session.createQueue(QueueConfiguration.of(QUEUE).setDurable(false));

      ClientProducer producer = session.createProducer(QUEUE);

      final int numMessages = 10;

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         producer.send(message);
      }

      ClientConsumer consumer = session.createConsumer(QUEUE);

      session.start();

      ClientMessage message = consumer.receiveImmediate();

      assertNull(message);

      session.close();
   }

   @Test
   public void testClientInterceptorChangeProperty() throws Exception {
      ClientSessionFactory sf = createSessionFactory(locator);

      MyInterceptor3 interceptor = new MyInterceptor3();

      sf.getServerLocator().addIncomingInterceptor(interceptor);

      ClientSession session = sf.createSession(false, true, true, true);

      session.createQueue(QueueConfiguration.of(QUEUE).setDurable(false));

      ClientProducer producer = session.createProducer(QUEUE);

      final int numMessages = 10;

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         message.putStringProperty(InterceptorTest.key, "apple");

         producer.send(message);
      }

      ClientConsumer consumer = session.createConsumer(QUEUE);

      session.start();

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer.receive(1000);

         assertEquals("orange", message.getStringProperty(InterceptorTest.key));
      }

      sf.getServerLocator().removeIncomingInterceptor(interceptor);

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         message.putStringProperty(InterceptorTest.key, "apple");

         producer.send(message);
      }

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer.receive(1000);

         assertEquals("apple", message.getStringProperty(InterceptorTest.key));
      }

      session.close();
   }

   @Test
   public void testClientOutgoingInterceptorChangeProperty() throws Exception {
      ClientSessionFactory sf = createSessionFactory(locator);

      MyOutgoingInterceptor3 interceptor = new MyOutgoingInterceptor3();

      sf.getServerLocator().addOutgoingInterceptor(interceptor);

      ClientSession session = sf.createSession(false, true, true, true);

      session.createQueue(QueueConfiguration.of(QUEUE).setDurable(false));

      ClientProducer producer = session.createProducer(QUEUE);

      final int numMessages = 10;

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         message.putStringProperty(InterceptorTest.key, "apple");

         producer.send(message);
      }

      ClientConsumer consumer = session.createConsumer(QUEUE);

      session.start();

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer.receive(1000);

         assertEquals("orange", message.getStringProperty(InterceptorTest.key));
      }

      sf.getServerLocator().removeOutgoingInterceptor(interceptor);

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         message.putStringProperty(InterceptorTest.key, "apple");

         producer.send(message);
      }

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer.receive(1000);

         assertEquals("apple", message.getStringProperty(InterceptorTest.key));
      }

      session.close();
   }

   @Test
   public void testClientInterceptorRejectPacket() throws Exception {
      ClientSessionFactory sf = createSessionFactory(locator);

      MyInterceptor4 interceptor = new MyInterceptor4();

      sf.getServerLocator().addIncomingInterceptor(interceptor);

      ClientSession session = sf.createSession(false, true, true, true);

      session.createQueue(QueueConfiguration.of(QUEUE).setDurable(false));

      ClientProducer producer = session.createProducer(QUEUE);

      final int numMessages = 10;

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         producer.send(message);
      }

      ClientConsumer consumer = session.createConsumer(QUEUE);

      session.start();

      ClientMessage message = consumer.receive(100);

      assertNull(message);

      session.close();
   }

   @Test
   public void testClientOutgoingInterceptorRejectPacketOnNonBlockingSend() throws Exception {
      locator.setBlockOnNonDurableSend(false);
      ClientSessionFactory sf = createSessionFactory(locator);

      MyOutgoingInterceptor4 interceptor = new MyOutgoingInterceptor4();

      sf.getServerLocator().addOutgoingInterceptor(interceptor);

      ClientSession session = sf.createSession(false, true, true, true);

      session.createQueue(QueueConfiguration.of(QUEUE).setDurable(false));

      ClientProducer producer = session.createProducer(QUEUE);

      final int numMessages = 10;

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         producer.send(message);
      }

      ClientConsumer consumer = session.createConsumer(QUEUE);

      session.start();

      ClientMessage message = consumer.receive(100);

      assertNull(message);

      session.close();
   }

   @Test
   public void testClientOutgoingInterceptorRejectPacketOnBlockingSend() throws Exception {
      // must make the call block to exercise the right logic
      locator.setBlockOnNonDurableSend(true);
      ClientSessionFactory sf = createSessionFactory(locator);

      MyOutgoingInterceptor4 interceptor = new MyOutgoingInterceptor4();

      sf.getServerLocator().addOutgoingInterceptor(interceptor);

      ClientSession session = sf.createSession(false, true, true, true);

      session.createQueue(QueueConfiguration.of(QUEUE).setDurable(false));

      ClientProducer producer = session.createProducer(QUEUE);

      ClientMessage message = session.createMessage(false);

      try {
         producer.send(message);
         fail();
      } catch (ActiveMQException e) {
         // expected exception
         assertEquals(e.getType().getCode(), ActiveMQExceptionType.INTERCEPTOR_REJECTED_PACKET.getCode());
      }
   }

   @Test
   public void testServerMultipleInterceptors() throws Exception {
      MyInterceptor5 interceptor1 = new MyInterceptor5("a", 1);
      MyInterceptor5 interceptor2 = new MyInterceptor5("b", 2);
      MyInterceptor5 interceptor3 = new MyInterceptor5("c", 3);
      MyInterceptor5 interceptor4 = new MyInterceptor5("d", 4);

      server.getRemotingService().addIncomingInterceptor(interceptor1);
      server.getRemotingService().addIncomingInterceptor(interceptor2);
      server.getRemotingService().addIncomingInterceptor(interceptor3);
      server.getRemotingService().addIncomingInterceptor(interceptor4);

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true, true);

      session.createQueue(QueueConfiguration.of(QUEUE).setDurable(false));

      ClientProducer producer = session.createProducer(QUEUE);

      final int numMessages = 10;

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         producer.send(message);
      }

      ClientConsumer consumer = session.createConsumer(QUEUE);

      session.start();

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer.receive(1000);

         assertEquals(1, message.getIntProperty("a").intValue());
         assertEquals(2, message.getIntProperty("b").intValue());
         assertEquals(3, message.getIntProperty("c").intValue());
         assertEquals(4, message.getIntProperty("d").intValue());
      }

      server.getRemotingService().removeIncomingInterceptor(interceptor2);

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         producer.send(message);
      }

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer.receive(1000);

         assertEquals(1, message.getIntProperty("a").intValue());
         assertFalse(message.containsProperty("b"));
         assertEquals(3, message.getIntProperty("c").intValue());
         assertEquals(4, message.getIntProperty("d").intValue());

      }

      interceptor3.setReject(true);

      interceptor1.setWasCalled(false);
      interceptor2.setWasCalled(false);
      interceptor3.setWasCalled(false);
      interceptor4.setWasCalled(false);

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         producer.send(message);
      }

      ClientMessage message = consumer.receiveImmediate();

      assertNull(message);

      assertTrue(interceptor1.wasCalled());
      assertFalse(interceptor2.wasCalled());
      assertTrue(interceptor3.wasCalled());
      assertFalse(interceptor4.wasCalled());

      session.close();
   }

   @Test
   public void testClientMultipleInterceptors() throws Exception {
      MyInterceptor6 interceptor1 = new MyInterceptor6("a", 1);
      MyInterceptor6 interceptor2 = new MyInterceptor6("b", 2);
      MyInterceptor6 interceptor3 = new MyInterceptor6("c", 3);
      MyInterceptor6 interceptor4 = new MyInterceptor6("d", 4);

      ClientSessionFactory sf = createSessionFactory(locator);

      sf.getServerLocator().addIncomingInterceptor(interceptor1);
      sf.getServerLocator().addIncomingInterceptor(interceptor2);
      sf.getServerLocator().addIncomingInterceptor(interceptor3);
      sf.getServerLocator().addIncomingInterceptor(interceptor4);

      ClientSession session = sf.createSession(false, true, true, true);

      session.createQueue(QueueConfiguration.of(QUEUE).setDurable(false));

      ClientProducer producer = session.createProducer(QUEUE);

      final int numMessages = 10;

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         producer.send(message);
      }

      ClientConsumer consumer = session.createConsumer(QUEUE);

      session.start();

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer.receive(1000);

         assertEquals(1, message.getIntProperty("a").intValue());
         assertEquals(2, message.getIntProperty("b").intValue());
         assertEquals(3, message.getIntProperty("c").intValue());
         assertEquals(4, message.getIntProperty("d").intValue());
      }

      sf.getServerLocator().removeIncomingInterceptor(interceptor2);

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         producer.send(message);
      }

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer.receive(1000);

         assertEquals(1, message.getIntProperty("a").intValue());
         assertFalse(message.containsProperty("b"));
         assertEquals(3, message.getIntProperty("c").intValue());
         assertEquals(4, message.getIntProperty("d").intValue());

      }

      interceptor3.setReject(true);

      interceptor1.setWasCalled(false);
      interceptor2.setWasCalled(false);
      interceptor3.setWasCalled(false);
      interceptor4.setWasCalled(false);

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         producer.send(message);
      }

      ClientMessage message = consumer.receive(100);

      assertNull(message);

      assertTrue(interceptor1.wasCalled());
      assertFalse(interceptor2.wasCalled());
      assertTrue(interceptor3.wasCalled());
      assertFalse(interceptor4.wasCalled());

      session.close();
   }

   @Test
   public void testServerOutgoingInterceptorChangeProperty() throws Exception {
      MyOutgoingInterceptor1 interceptor = new MyOutgoingInterceptor1();

      server.getRemotingService().addOutgoingInterceptor(interceptor);

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true, true);

      session.createQueue(QueueConfiguration.of(QUEUE).setDurable(false));

      ClientProducer producer = session.createProducer(QUEUE);

      final int numMessages = 10;

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         message.putIntProperty("count", i);

         message.putStringProperty(InterceptorTest.key, "apple");

         producer.send(message);
      }

      ClientConsumer consumer = session.createConsumer(QUEUE);

      session.start();

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer.receive(1000);

         assertNotNull(message);

         assertEquals(i, message.getIntProperty("count").intValue());

         assertEquals("orange", message.getStringProperty(InterceptorTest.key));
      }

      server.getRemotingService().removeOutgoingInterceptor(interceptor);

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         message.putStringProperty(InterceptorTest.key, "apple");

         producer.send(message);
      }

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = consumer.receive(1000);

         assertEquals("apple", message.getStringProperty(InterceptorTest.key));
      }

      session.close();
   }

   @Test
   public void testServerOutgoingInterceptorRejectMessage() throws Exception {
      MyOutgoingInterceptor2 interceptor = new MyOutgoingInterceptor2();

      server.getRemotingService().addOutgoingInterceptor(interceptor);

      locator.setBlockOnNonDurableSend(false);

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true, true);

      session.createQueue(QueueConfiguration.of(QUEUE).setDurable(false));

      ClientProducer producer = session.createProducer(QUEUE);

      final int numMessages = 10;

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(false);

         producer.send(message);
      }

      ClientConsumer consumer = session.createConsumer(QUEUE);

      session.start();

      ClientMessage message = consumer.receiveImmediate();

      assertNull(message);

      session.close();
   }

   @Test
   public void testInterceptorOnURI() throws Exception {
      locator.close();

      server.createQueue(QueueConfiguration.of(QUEUE).setRoutingType(RoutingType.ANYCAST));

      String uri = "tcp://localhost:61616?incomingInterceptorList=" + Incoming.class.getCanonicalName() + "&outgoingInterceptorList=" + Outgoing.class.getName();

      logger.debug(uri);

      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(uri);

      // Serialize stuff to make sure the interceptors are on the URI
      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
      objectOutputStream.close();
      objectOutputStream.writeObject(factory);
      ByteArrayInputStream input = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
      ObjectInputStream objectInputStream = new ObjectInputStream(input);

      factory = (ActiveMQConnectionFactory) objectInputStream.readObject();

      Connection connection = factory.createConnection();
      Session session = connection.createSession();
      MessageProducer producer = session.createProducer(session.createQueue(QUEUE.toString()));

      producer.send(session.createTextMessage("HelloMessage"));

      connection.start();

      MessageConsumer consumer = session.createConsumer(session.createQueue(QUEUE.toString()));

      TextMessage msg = (TextMessage) consumer.receive(5000);
      assertNotNull(msg);

      assertEquals("HelloMessage", msg.getText());

      assertEquals("was here", msg.getStringProperty("Incoming"));

      assertEquals("sending", msg.getStringProperty("Outgoing"));

      connection.close();

      factory.close();

   }
}

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
package org.apache.activemq.artemis.tests.integration.ra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;
import javax.resource.ResourceException;
import javax.resource.spi.InvalidPropertyException;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSession.QueueQuery;
import org.apache.activemq.artemis.api.core.client.SessionFailureListener;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.ra.ActiveMQResourceAdapter;
import org.apache.activemq.artemis.ra.inflow.ActiveMQActivation;
import org.apache.activemq.artemis.ra.inflow.ActiveMQActivationSpec;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ActiveMQMessageHandlerTest extends ActiveMQRATestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Override
   public boolean useSecurity() {
      return false;
   }

   @Test
   public void testSimpleMessageReceivedOnQueue() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      CountDownLatch latch = new CountDownLatch(1);
      DummyMessageEndpoint endpoint = new DummyMessageEndpoint(latch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      qResourceAdapter.endpointActivation(endpointFactory, spec);
      ClientSession session = locator.createSessionFactory().createSession();
      ClientProducer clientProducer = session.createProducer(MDBQUEUEPREFIXED);
      ClientMessage message = session.createMessage(true);
      message.getBodyBuffer().writeString("teststring");
      clientProducer.send(message);
      session.close();
      latch.await(5, TimeUnit.SECONDS);

      assertNotNull(endpoint.lastMessage);
      assertEquals("teststring", endpoint.lastMessage.getCoreMessage().getBodyBuffer().readString());

      qResourceAdapter.endpointDeactivation(endpointFactory, spec);

      qResourceAdapter.stop();
   }

   @Test
   public void testDurableTopicSubscriptionWith1xPrefixesOnSpec() throws Exception {
      internalTestDurableTopicSubscriptionWith1xPrefixes(false, true);
   }

   @Test
   public void testDurableTopicSubscriptionWith1xPrefixesOnRA() throws Exception {
      internalTestDurableTopicSubscriptionWith1xPrefixes(true, true);
   }

   @Test
   public void testDurableTopicSubscriptionWith1xPrefixesOnSpecWithoutBrokerPrefixes() throws Exception {
      internalTestDurableTopicSubscriptionWith1xPrefixes(false, false);
   }

   @Test
   public void testDurableTopicSubscriptionWith1xPrefixesOnRAWithoutBrokerPrefixes() throws Exception {
      internalTestDurableTopicSubscriptionWith1xPrefixes(true, false);
   }

   public void internalTestDurableTopicSubscriptionWith1xPrefixes(boolean ra, boolean definePrefixesOnBroker) throws Exception {
      if (definePrefixesOnBroker) {
         server.getRemotingService().createAcceptor("test", "tcp://localhost:61617?anycastPrefix=jms.queue.;multicastPrefix=jms.topic.").start();
      }
      ActiveMQResourceAdapter qResourceAdapter = newResourceAdapter();
      if (ra) {
         qResourceAdapter.setEnable1xPrefixes(true);
      }
      MyBootstrapContext ctx = new MyBootstrapContext();
      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setSetupAttempts(1);
      spec.setSetupInterval(500L);
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Topic");
      spec.setDestination("jms.topic.MyTopic");
      if (!ra) {
         spec.setEnable1xPrefixes(true);
      }
      spec.setSubscriptionDurability("Durable");
      spec.setClientId("myClientId");
      spec.setSubscriptionName("mySubscriptionName");
      qResourceAdapter.setConnectorClassName(NETTY_CONNECTOR_FACTORY);
      String port = "61616";
      if (definePrefixesOnBroker) {
         port = "61617";
      }
      qResourceAdapter.setConnectionParameters("host=localhost;port=" + port);
      CountDownLatch latch = new CountDownLatch(1);
      DummyMessageEndpoint endpoint = new DummyMessageEndpoint(latch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      qResourceAdapter.endpointActivation(endpointFactory, spec);
      ClientSession session = locator.createSessionFactory().createSession();
      String topic = "MyTopic";
      if (!definePrefixesOnBroker) {
         topic = "jms.topic." + topic;
      }
      ClientProducer clientProducer = session.createProducer(topic);
      ClientMessage message = session.createMessage(true);
      message.getBodyBuffer().writeString("teststring");
      clientProducer.send(message);
      session.close();
      latch.await(5, TimeUnit.SECONDS);

      assertNotNull(endpoint.lastMessage);
      assertEquals("teststring", endpoint.lastMessage.getCoreMessage().getBodyBuffer().readString());

      qResourceAdapter.endpointDeactivation(endpointFactory, spec);

      qResourceAdapter.stop();
   }

   @Test
   public void testObjectMessageReceiveSerializationControl() throws Exception {
      String denyList = "org.apache.activemq.artemis.tests.integration.ra";
      String allowList = "*";
      testDeserialization(denyList, allowList, false);
   }

   @Test
   public void testObjectMessageReceiveSerializationControl1() throws Exception {
      String denyList = "some.other.pkg";
      String allowList = "org.apache.activemq.artemis.tests.integration.ra";
      testDeserialization(denyList, allowList, true);
   }

   @Test
   public void testObjectMessageReceiveSerializationControl2() throws Exception {
      String denyList = "*";
      String allowList = "org.apache.activemq.artemis.tests.integration.ra";
      testDeserialization(denyList, allowList, false);
   }

   @Test
   public void testObjectMessageReceiveSerializationControl3() throws Exception {
      String denyList = "org.apache.activemq.artemis.tests";
      String allowList = "org.apache.activemq.artemis.tests.integration.ra";
      testDeserialization(denyList, allowList, false);
   }

   @Test
   public void testObjectMessageReceiveSerializationControl4() throws Exception {
      String denyList = null;
      String allowList = "some.other.pkg";
      testDeserialization(denyList, allowList, false);
   }

   @Test
   public void testObjectMessageReceiveSerializationControl5() throws Exception {
      String denyList = null;
      String allowList = null;
      testDeserialization(denyList, allowList, true);
   }

   private void testDeserialization(String denyList, String allowList, boolean shouldSucceed) throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = newResourceAdapter();
      qResourceAdapter.setDeserializationDenyList(denyList);
      qResourceAdapter.setDeserializationAllowList(allowList);

      MyBootstrapContext ctx = new MyBootstrapContext();
      qResourceAdapter.start(ctx);

      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      CountDownLatch latch = new CountDownLatch(1);
      DummyMessageEndpoint endpoint = new DummyMessageEndpoint(latch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      qResourceAdapter.endpointActivation(endpointFactory, spec);

      //send using jms
      ActiveMQConnectionFactory jmsFactory = new ActiveMQConnectionFactory("vm://0");
      Connection connection = jmsFactory.createConnection();

      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue jmsQueue = session.createQueue(MDBQUEUE);
         ObjectMessage objMsg = session.createObjectMessage();
         objMsg.setObject(new DummySerializable());
         MessageProducer producer = session.createProducer(jmsQueue);
         producer.send(objMsg);
      } finally {
         connection.close();
      }

      latch.await(5, TimeUnit.SECONDS);

      assertNotNull(endpoint.lastMessage);

      ObjectMessage objMsg = (ObjectMessage) endpoint.lastMessage;

      try {
         Object obj = objMsg.getObject();
         assertTrue(shouldSucceed, "deserialization should fail but got: " + obj);
         assertInstanceOf(DummySerializable.class, obj);
      } catch (JMSException e) {
         assertFalse(shouldSucceed, "got unexpected exception: " + e);
      }

      qResourceAdapter.endpointDeactivation(endpointFactory, spec);

      qResourceAdapter.stop();
   }

   @Test
   public void testSimpleMessageReceivedOnQueueManyMessages() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      CountDownLatch latch = new CountDownLatch(15);
      MultipleEndpoints endpoint = new MultipleEndpoints(latch, null, false);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      qResourceAdapter.endpointActivation(endpointFactory, spec);
      ClientSession session = locator.createSessionFactory().createSession();
      ClientProducer clientProducer = session.createProducer(MDBQUEUEPREFIXED);
      for (int i = 0; i < 15; i++) {
         ClientMessage message = session.createMessage(true);
         message.getBodyBuffer().writeString("teststring" + i);
         clientProducer.send(message);
      }
      session.close();
      latch.await(5, TimeUnit.SECONDS);

      qResourceAdapter.endpointDeactivation(endpointFactory, spec);

      qResourceAdapter.stop();
   }

   @Test
   public void testSimpleMessageReceivedOnQueueManyMessagesAndInterrupt() throws Exception {
      final int SIZE = 14;
      ActiveMQResourceAdapter qResourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      CountDownLatch latch = new CountDownLatch(SIZE);
      CountDownLatch latchDone = new CountDownLatch(SIZE);
      MultipleEndpoints endpoint = new MultipleEndpoints(latch, latchDone, true);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      qResourceAdapter.endpointActivation(endpointFactory, spec);
      ClientSession session = locator.createSessionFactory().createSession();
      ClientProducer clientProducer = session.createProducer(MDBQUEUEPREFIXED);
      for (int i = 0; i < SIZE; i++) {
         ClientMessage message = session.createMessage(true);
         message.getBodyBuffer().writeString("teststring" + i);
         clientProducer.send(message);
      }
      session.close();
      assertTrue(latch.await(5, TimeUnit.SECONDS));

      qResourceAdapter.endpointDeactivation(endpointFactory, spec);

      latchDone.await(5, TimeUnit.SECONDS);

      assertEquals(SIZE, endpoint.messages.intValue());
      assertEquals(0, endpoint.interrupted.intValue());

      qResourceAdapter.stop();
   }

   @Test
   public void testSimpleMessageReceivedOnQueueManyMessagesAndInterruptTimeout() throws Exception {
      final int SIZE = 14;
      ActiveMQResourceAdapter qResourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setCallTimeout(500L);
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      CountDownLatch latch = new CountDownLatch(SIZE);
      CountDownLatch latchDone = new CountDownLatch(SIZE);
      MultipleEndpoints endpoint = new MultipleEndpoints(latch, latchDone, true);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      qResourceAdapter.endpointActivation(endpointFactory, spec);
      ClientSession session = locator.createSessionFactory().createSession();
      ClientProducer clientProducer = session.createProducer(MDBQUEUEPREFIXED);
      for (int i = 0; i < SIZE; i++) {
         ClientMessage message = session.createMessage(true);
         message.getBodyBuffer().writeString("teststring" + i);
         clientProducer.send(message);
      }
      session.close();
      assertTrue(latch.await(5, TimeUnit.SECONDS));

      qResourceAdapter.endpointDeactivation(endpointFactory, spec);

      latchDone.await(5, TimeUnit.SECONDS);

      assertEquals(SIZE, endpoint.messages.intValue());
      //half onmessage interrupted
      assertEquals(SIZE / 2, endpoint.interrupted.intValue());

      qResourceAdapter.stop();
   }

   @Override
   protected ActiveMQResourceAdapter newResourceAdapter() {
      ActiveMQResourceAdapter qResourceAdapter = new ActiveMQResourceAdapter();
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      return qResourceAdapter;
   }

   @Test
   public void testServerShutdownAndReconnect() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = newResourceAdapter();
      qResourceAdapter.setReconnectAttempts(-1);
      qResourceAdapter.setCallTimeout(500L);
      qResourceAdapter.setRetryInterval(500L);
      MyBootstrapContext ctx = new MyBootstrapContext();
      qResourceAdapter.start(ctx);
      // This is just to register a listener
      final CountDownLatch failedLatch = new CountDownLatch(1);
      ClientSessionFactoryInternal factoryListener = (ClientSessionFactoryInternal) qResourceAdapter.getDefaultActiveMQConnectionFactory().getServerLocator().createSessionFactory();
      factoryListener.addFailureListener(new SessionFailureListener() {

         @Override
         public void connectionFailed(ActiveMQException exception, boolean failedOver) {
         }

         @Override
         public void connectionFailed(ActiveMQException exception, boolean failedOver, String scaleDownTargetNodeID) {
            connectionFailed(exception, failedOver);
         }

         @Override
         public void beforeReconnect(ActiveMQException exception) {
            failedLatch.countDown();
         }
      });
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);
      CountDownLatch latch = new CountDownLatch(1);
      DummyMessageEndpoint endpoint = new DummyMessageEndpoint(latch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      qResourceAdapter.endpointActivation(endpointFactory, spec);
      ClientSession session = locator.createSessionFactory().createSession();
      ClientProducer clientProducer = session.createProducer(MDBQUEUEPREFIXED);
      ClientMessage message = session.createMessage(true);
      message.getBodyBuffer().writeString("teststring");
      clientProducer.send(message);
      session.close();
      latch.await(5, TimeUnit.SECONDS);

      assertNotNull(endpoint.lastMessage);
      assertEquals("teststring", endpoint.lastMessage.getCoreMessage().getBodyBuffer().readString());

      server.stop();

      assertTrue(failedLatch.await(5, TimeUnit.SECONDS));

      qResourceAdapter.endpointDeactivation(endpointFactory, spec);

      qResourceAdapter.stop();
   }

   @Test
   public void testInvalidAckMode() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      try {
         spec.setAcknowledgeMode("CLIENT_ACKNOWLEDGE");
         fail("should throw exception");
      } catch (java.lang.IllegalArgumentException e) {
         //pass
      }
      qResourceAdapter.stop();
   }

   @Test
   public void testSimpleMessageReceivedOnQueueInLocalTX() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = newResourceAdapter();
      qResourceAdapter.setUseLocalTx(true);
      MyBootstrapContext ctx = new MyBootstrapContext();
      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      CountDownLatch latch = new CountDownLatch(1);
      ExceptionDummyMessageEndpoint endpoint = new ExceptionDummyMessageEndpoint(latch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      qResourceAdapter.endpointActivation(endpointFactory, spec);
      ClientSession session = locator.createSessionFactory().createSession();
      ClientProducer clientProducer = session.createProducer(MDBQUEUEPREFIXED);
      ClientMessage message = session.createMessage(true);
      message.getBodyBuffer().writeString("teststring");
      clientProducer.send(message);
      latch.await(5, TimeUnit.SECONDS);

      assertNull(endpoint.lastMessage);

      latch = new CountDownLatch(1);
      endpoint.reset(latch);
      clientProducer.send(message);
      session.close();
      latch.await(5, TimeUnit.SECONDS);

      assertNotNull(endpoint.lastMessage);
      assertEquals("teststring", endpoint.lastMessage.getCoreMessage().getBodyBuffer().readString());

      qResourceAdapter.endpointDeactivation(endpointFactory, spec);
      qResourceAdapter.stop();
   }

   @Test
   public void testSimpleMessageReceivedOnQueueWithSelector() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);
      spec.setMessageSelector("color='red'");
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      CountDownLatch latch = new CountDownLatch(1);
      DummyMessageEndpoint endpoint = new DummyMessageEndpoint(latch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      qResourceAdapter.endpointActivation(endpointFactory, spec);
      ClientSession session = locator.createSessionFactory().createSession();
      ClientProducer clientProducer = session.createProducer(MDBQUEUEPREFIXED);
      ClientMessage message = session.createMessage(true);
      message.getBodyBuffer().writeString("blue");
      message.putStringProperty("color", "blue");
      clientProducer.send(message);
      message = session.createMessage(true);
      message.getBodyBuffer().writeString("red");
      message.putStringProperty("color", "red");
      clientProducer.send(message);
      session.close();
      latch.await(5, TimeUnit.SECONDS);

      assertNotNull(endpoint.lastMessage);
      assertEquals("red", endpoint.lastMessage.getCoreMessage().getBodyBuffer().readString());

      qResourceAdapter.endpointDeactivation(endpointFactory, spec);
      qResourceAdapter.stop();
   }

   @Test
   public void testEndpointDeactivated() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      CountDownLatch latch = new CountDownLatch(1);
      DummyMessageEndpoint endpoint = new DummyMessageEndpoint(latch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      qResourceAdapter.endpointActivation(endpointFactory, spec);
      Binding binding = server.getPostOffice().getBinding(MDBQUEUEPREFIXEDSIMPLE);
      assertEquals(15, ((LocalQueueBinding) binding).getQueue().getConsumerCount());
      qResourceAdapter.endpointDeactivation(endpointFactory, spec);
      assertEquals(0, ((LocalQueueBinding) binding).getQueue().getConsumerCount());
      assertTrue(endpoint.released);
      qResourceAdapter.stop();
   }

   @Test
   public void testMaxSessions() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setMaxSession(1);
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Queue");
      spec.setDestination(MDBQUEUE);
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      CountDownLatch latch = new CountDownLatch(1);
      DummyMessageEndpoint endpoint = new DummyMessageEndpoint(latch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      qResourceAdapter.endpointActivation(endpointFactory, spec);
      Binding binding = server.getPostOffice().getBinding(MDBQUEUEPREFIXEDSIMPLE);
      assertEquals(1, ((LocalQueueBinding) binding).getQueue().getConsumerCount());
      qResourceAdapter.endpointDeactivation(endpointFactory, spec);
      qResourceAdapter.stop();
   }

   @Test
   public void testSimpleTopic() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Topic");
      spec.setDestination("mdbTopic");
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      CountDownLatch latch = new CountDownLatch(1);
      DummyMessageEndpoint endpoint = new DummyMessageEndpoint(latch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      qResourceAdapter.endpointActivation(endpointFactory, spec);
      ClientSession session = locator.createSessionFactory().createSession();
      ClientProducer clientProducer = session.createProducer("mdbTopic");
      ClientMessage message = session.createMessage(true);
      message.getBodyBuffer().writeString("test");
      clientProducer.send(message);

      latch.await(5, TimeUnit.SECONDS);

      assertNotNull(endpoint.lastMessage);
      assertEquals("test", endpoint.lastMessage.getCoreMessage().getBodyBuffer().readString());

      qResourceAdapter.endpointDeactivation(endpointFactory, spec);
      qResourceAdapter.stop();
   }

   @Test
   public void testDurableSubscription() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Topic");
      spec.setDestination("mdbTopic");
      spec.setSubscriptionDurability("Durable");
      spec.setSubscriptionName("durable-mdb");
      spec.setClientID("id-1");
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      CountDownLatch latch = new CountDownLatch(1);
      DummyMessageEndpoint endpoint = new DummyMessageEndpoint(latch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      qResourceAdapter.endpointActivation(endpointFactory, spec);
      ClientSession session = locator.createSessionFactory().createSession();
      ClientProducer clientProducer = session.createProducer("mdbTopic");
      ClientMessage message = session.createMessage(true);
      message.getBodyBuffer().writeString("1");
      clientProducer.send(message);

      latch.await(5, TimeUnit.SECONDS);

      assertNotNull(endpoint.lastMessage);
      assertEquals("1", endpoint.lastMessage.getCoreMessage().getBodyBuffer().readString());

      qResourceAdapter.endpointDeactivation(endpointFactory, spec);

      message = session.createMessage(true);
      message.getBodyBuffer().writeString("2");
      clientProducer.send(message);

      latch = new CountDownLatch(1);
      endpoint = new DummyMessageEndpoint(latch);
      endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      qResourceAdapter.endpointActivation(endpointFactory, spec);
      latch.await(5, TimeUnit.SECONDS);

      assertNotNull(endpoint.lastMessage);
      assertEquals("2", endpoint.lastMessage.getCoreMessage().getBodyBuffer().readString());
      latch = new CountDownLatch(1);
      endpoint.reset(latch);
      message = session.createMessage(true);
      message.getBodyBuffer().writeString("3");
      clientProducer.send(message);
      latch.await(5, TimeUnit.SECONDS);

      assertNotNull(endpoint.lastMessage);
      assertEquals("3", endpoint.lastMessage.getCoreMessage().getBodyBuffer().readString());
      qResourceAdapter.endpointDeactivation(endpointFactory, spec);
      qResourceAdapter.stop();
   }

   @Test
   public void testNonDurableSubscription() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Topic");
      spec.setDestination("mdbTopic");
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      CountDownLatch latch = new CountDownLatch(1);
      DummyMessageEndpoint endpoint = new DummyMessageEndpoint(latch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      qResourceAdapter.endpointActivation(endpointFactory, spec);
      ClientSession session = locator.createSessionFactory().createSession();
      ClientProducer clientProducer = session.createProducer("mdbTopic");
      ClientMessage message = session.createMessage(true);
      message.getBodyBuffer().writeString("1");
      clientProducer.send(message);

      latch.await(5, TimeUnit.SECONDS);

      assertNotNull(endpoint.lastMessage);
      assertEquals("1", endpoint.lastMessage.getCoreMessage().getBodyBuffer().readString());

      qResourceAdapter.endpointDeactivation(endpointFactory, spec);

      message = session.createMessage(true);
      message.getBodyBuffer().writeString("2");
      clientProducer.send(message);

      latch = new CountDownLatch(1);
      endpoint = new DummyMessageEndpoint(latch);
      endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      qResourceAdapter.endpointActivation(endpointFactory, spec);
      message = session.createMessage(true);
      message.getBodyBuffer().writeString("3");
      clientProducer.send(message);
      latch.await(5, TimeUnit.SECONDS);

      assertNotNull(endpoint.lastMessage);
      assertEquals("3", endpoint.lastMessage.getCoreMessage().getBodyBuffer().readString());
      qResourceAdapter.endpointDeactivation(endpointFactory, spec);
      qResourceAdapter.stop();
   }

   @Test
   public void testNonDurableSubscriptionDeleteAfterCrash() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Topic");
      spec.setDestination("mdbTopic");
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      CountDownLatch latch = new CountDownLatch(1);
      DummyMessageEndpoint endpoint = new DummyMessageEndpoint(latch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      qResourceAdapter.endpointActivation(endpointFactory, spec);

      ClientSession session = locator.createSessionFactory().createSession();
      ClientProducer clientProducer = session.createProducer("mdbTopic");
      ClientMessage message = session.createMessage(true);
      message.getBodyBuffer().writeString("1");
      clientProducer.send(message);

      latch.await(5, TimeUnit.SECONDS);

      assertNotNull(endpoint.lastMessage);
      assertEquals("1", endpoint.lastMessage.getCoreMessage().getBodyBuffer().readString());

      ActiveMQActivation activation = lookupActivation(qResourceAdapter);

      SimpleString tempQueueName = activation.getTopicTemporaryQueue();

      QueueQuery query = session.queueQuery(tempQueueName);
      assertTrue(query.isExists());

      //this should be enough to simulate the crash
      qResourceAdapter.getDefaultActiveMQConnectionFactory().close();
      qResourceAdapter.stop();

      query = session.queueQuery(tempQueueName);

      assertFalse(query.isExists());
   }

   @Test
   public void testSelectorChangedWithTopic() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = newResourceAdapter();

      MyBootstrapContext ctx = new MyBootstrapContext();

      qResourceAdapter.start(ctx);

      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Topic");
      spec.setDestination("mdbTopic");
      spec.setSubscriptionDurability("Durable");
      spec.setSubscriptionName("durable-mdb");
      spec.setClientID("id-1");
      spec.setMessageSelector("foo='bar'");

      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);

      CountDownLatch latch = new CountDownLatch(1);

      DummyMessageEndpoint endpoint = new DummyMessageEndpoint(latch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      qResourceAdapter.endpointActivation(endpointFactory, spec);
      ClientSession session = locator.createSessionFactory().createSession();
      ClientProducer clientProducer = session.createProducer("mdbTopic");
      ClientMessage message = session.createMessage(true);
      message.getBodyBuffer().writeString("1");
      message.putStringProperty("foo", "bar");
      clientProducer.send(message);

      latch.await(5, TimeUnit.SECONDS);

      assertNotNull(endpoint.lastMessage);
      assertEquals("1", endpoint.lastMessage.getCoreMessage().getBodyBuffer().readString());

      qResourceAdapter.endpointDeactivation(endpointFactory, spec);

      message = session.createMessage(true);
      message.getBodyBuffer().writeString("2");
      message.putStringProperty("foo", "bar");
      clientProducer.send(message);

      latch = new CountDownLatch(1);
      endpoint = new DummyMessageEndpoint(latch);
      //change the selector forcing the queue to be recreated
      spec.setMessageSelector("foo='abar'");
      endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      qResourceAdapter.endpointActivation(endpointFactory, spec);
      message = session.createMessage(true);
      message.getBodyBuffer().writeString("3");
      message.putStringProperty("foo", "abar");
      clientProducer.send(message);
      latch.await(5, TimeUnit.SECONDS);

      assertNotNull(endpoint.lastMessage);
      assertEquals("3", endpoint.lastMessage.getCoreMessage().getBodyBuffer().readString());
      qResourceAdapter.endpointDeactivation(endpointFactory, spec);
      qResourceAdapter.stop();
   }

   @Test
   public void testSharedSubscription() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      qResourceAdapter.start(ctx);

      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Topic");
      spec.setDestination("mdbTopic");
      spec.setSubscriptionDurability("Durable");
      spec.setSubscriptionName("durable-mdb");
      spec.setClientID("id-1");
      spec.setSetupAttempts(1);
      spec.setShareSubscriptions(true);
      spec.setMaxSession(1);

      ActiveMQActivationSpec spec2 = new ActiveMQActivationSpec();
      spec2.setResourceAdapter(qResourceAdapter);
      spec2.setUseJNDI(false);
      spec2.setDestinationType("javax.jms.Topic");
      spec2.setDestination("mdbTopic");
      spec2.setSubscriptionDurability("Durable");
      spec2.setSubscriptionName("durable-mdb");
      spec2.setClientID("id-1");
      spec2.setSetupAttempts(1);
      spec2.setShareSubscriptions(true);
      spec2.setMaxSession(1);

      CountDownLatch latch = new CountDownLatch(5);
      DummyMessageEndpoint endpoint = new DummyMessageEndpoint(latch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      qResourceAdapter.endpointActivation(endpointFactory, spec);

      CountDownLatch latch2 = new CountDownLatch(5);
      DummyMessageEndpoint endpoint2 = new DummyMessageEndpoint(latch2);
      DummyMessageEndpointFactory endpointFactory2 = new DummyMessageEndpointFactory(endpoint2, false);
      qResourceAdapter.endpointActivation(endpointFactory2, spec2);

      ClientSession session = locator.createSessionFactory().createSession();
      ClientProducer clientProducer = session.createProducer("mdbTopic");

      for (int i = 0; i < 10; i++) {
         ClientMessage message = session.createMessage(true);
         message.getBodyBuffer().writeString("" + i);
         clientProducer.send(message);
      }
      session.commit();

      assertTrue(latch.await(5, TimeUnit.SECONDS));
      assertTrue(latch2.await(5, TimeUnit.SECONDS));

      assertNotNull(endpoint.lastMessage);
      assertNotNull(endpoint2.lastMessage);

      qResourceAdapter.endpointDeactivation(endpointFactory, spec);
      qResourceAdapter.endpointDeactivation(endpointFactory2, spec2);
      qResourceAdapter.stop();

   }

   @Test
   public void testNullSubscriptionName() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      qResourceAdapter.start(ctx);

      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestination("mdbTopic");
      spec.setSubscriptionDurability("Durable");
      spec.setClientID("id-1");
      spec.setSetupAttempts(1);
      spec.setShareSubscriptions(true);
      spec.setMaxSession(1);

      CountDownLatch latch = new CountDownLatch(5);
      DummyMessageEndpoint endpoint = new DummyMessageEndpoint(latch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      try {
         qResourceAdapter.endpointActivation(endpointFactory, spec);
         fail();
      } catch (Exception e) {
         assertInstanceOf(InvalidPropertyException.class, e);
         assertEquals("subscriptionName", ((InvalidPropertyException) e).getInvalidPropertyDescriptors()[0].getName());
      }
   }

   @Test
   public void testBadDestinationType() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      qResourceAdapter.start(ctx);

      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("badDestinationType");
      spec.setDestination("mdbTopic");
      spec.setSetupAttempts(1);
      spec.setShareSubscriptions(true);
      spec.setMaxSession(1);

      CountDownLatch latch = new CountDownLatch(5);
      DummyMessageEndpoint endpoint = new DummyMessageEndpoint(latch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      try {
         qResourceAdapter.endpointActivation(endpointFactory, spec);
         fail();
      } catch (Exception e) {
         assertInstanceOf(InvalidPropertyException.class, e);
         assertEquals("destinationType", ((InvalidPropertyException) e).getInvalidPropertyDescriptors()[0].getName());
      }
   }

   @Test
   public void testSelectorNotChangedWithTopic() throws Exception {
      ActiveMQResourceAdapter qResourceAdapter = newResourceAdapter();
      MyBootstrapContext ctx = new MyBootstrapContext();
      qResourceAdapter.start(ctx);
      ActiveMQActivationSpec spec = new ActiveMQActivationSpec();
      spec.setResourceAdapter(qResourceAdapter);
      spec.setUseJNDI(false);
      spec.setDestinationType("javax.jms.Topic");
      spec.setDestination("mdbTopic");
      spec.setSubscriptionDurability("Durable");
      spec.setSubscriptionName("durable-mdb");
      spec.setClientID("id-1");
      spec.setMessageSelector("foo='bar'");
      qResourceAdapter.setConnectorClassName(INVM_CONNECTOR_FACTORY);
      CountDownLatch latch = new CountDownLatch(1);
      DummyMessageEndpoint endpoint = new DummyMessageEndpoint(latch);
      DummyMessageEndpointFactory endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      qResourceAdapter.endpointActivation(endpointFactory, spec);
      ClientSession session = locator.createSessionFactory().createSession();
      ClientProducer clientProducer = session.createProducer("mdbTopic");
      ClientMessage message = session.createMessage(true);
      message.getBodyBuffer().writeString("1");
      message.putStringProperty("foo", "bar");
      clientProducer.send(message);

      latch.await(5, TimeUnit.SECONDS);

      assertNotNull(endpoint.lastMessage);
      assertEquals("1", endpoint.lastMessage.getCoreMessage().getBodyBuffer().readString());

      qResourceAdapter.endpointDeactivation(endpointFactory, spec);

      message = session.createMessage(true);
      message.getBodyBuffer().writeString("2");
      message.putStringProperty("foo", "bar");
      clientProducer.send(message);

      latch = new CountDownLatch(1);
      endpoint = new DummyMessageEndpoint(latch);
      endpointFactory = new DummyMessageEndpointFactory(endpoint, false);
      qResourceAdapter.endpointActivation(endpointFactory, spec);
      latch.await(5, TimeUnit.SECONDS);

      assertNotNull(endpoint.lastMessage);
      assertEquals("2", endpoint.lastMessage.getCoreMessage().getBodyBuffer().readString());
      qResourceAdapter.endpointDeactivation(endpointFactory, spec);
      qResourceAdapter.stop();

   }

   class ExceptionDummyMessageEndpoint extends DummyMessageEndpoint {

      boolean throwException = true;

      ExceptionDummyMessageEndpoint(CountDownLatch latch) {
         super(latch);
      }

      @Override
      public void onMessage(Message message) {
         if (throwException) {
            throwException = false;
            throw new IllegalStateException("boo!");
         }
         super.onMessage(message);
      }
   }

   class MultipleEndpoints extends DummyMessageEndpoint {

      private final CountDownLatch latch;
      private final CountDownLatch latchDone;
      private final boolean pause;
      AtomicInteger messages = new AtomicInteger(0);
      AtomicInteger interrupted = new AtomicInteger(0);

      MultipleEndpoints(CountDownLatch latch, CountDownLatch latchDone, boolean pause) {
         super(latch);
         this.latch = latch;
         this.latchDone = latchDone;
         this.pause = pause;
      }

      @Override
      public void beforeDelivery(Method method) throws NoSuchMethodException, ResourceException {

      }

      @Override
      public void afterDelivery() throws ResourceException {

      }

      @Override
      public void release() {

      }

      @Override
      public void onMessage(Message message) {
         try {
            latch.countDown();
            if (pause && messages.getAndIncrement() % 2 == 0) {
               try {
                  logger.debug("pausing for 2 secs");
                  Thread.sleep(2000);
               } catch (InterruptedException e) {
                  interrupted.incrementAndGet();
               }
            }
         } finally {
            if (latchDone != null) {
               latchDone.countDown();
            }
         }
      }
   }

   static class DummySerializable implements Serializable {

   }
}

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
package org.apache.activemq.artemis.tests.integration.jms;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.InitialContext;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.BroadcastGroupConfiguration;
import org.apache.activemq.artemis.api.core.DiscoveryGroupConfiguration;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.UDPBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.ha.SharedStorePrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.integration.jms.serializables.TestClass1;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.ObjectInputStreamWithClassLoader;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

public class ActiveMQConnectionFactoryTest extends ActiveMQTestBase {

   private final String groupAddress = getUDPDiscoveryAddress();

   private final int groupPort = getUDPDiscoveryPort();

   private ActiveMQServer liveService;

   private TransportConfiguration primaryTC;

   @Test
   public void testDefaultConstructor() throws Exception {
      ActiveMQConnectionFactory cf = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF);
      assertFactoryParams(cf, null, null, null, ActiveMQClient.DEFAULT_CLIENT_FAILURE_CHECK_PERIOD, ActiveMQClient.DEFAULT_CONNECTION_TTL, ActiveMQClient.DEFAULT_CALL_TIMEOUT, ActiveMQClient.DEFAULT_CALL_FAILOVER_TIMEOUT, ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE, ActiveMQClient.DEFAULT_CONSUMER_WINDOW_SIZE, ActiveMQClient.DEFAULT_CONSUMER_MAX_RATE, ActiveMQClient.DEFAULT_CONFIRMATION_WINDOW_SIZE, ActiveMQClient.DEFAULT_PRODUCER_MAX_RATE, ActiveMQClient.DEFAULT_BLOCK_ON_ACKNOWLEDGE, ActiveMQClient.DEFAULT_BLOCK_ON_DURABLE_SEND, ActiveMQClient.DEFAULT_BLOCK_ON_NON_DURABLE_SEND, ActiveMQClient.DEFAULT_AUTO_GROUP, ActiveMQClient.DEFAULT_PRE_ACKNOWLEDGE, ActiveMQClient.DEFAULT_CONNECTION_LOAD_BALANCING_POLICY_CLASS_NAME, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE, ActiveMQClient.DEFAULT_DISCOVERY_INITIAL_WAIT_TIMEOUT, ActiveMQClient.DEFAULT_USE_GLOBAL_POOLS, ActiveMQClient.DEFAULT_SCHEDULED_THREAD_POOL_MAX_SIZE, ActiveMQClient.DEFAULT_THREAD_POOL_MAX_SIZE, ActiveMQClient.DEFAULT_RETRY_INTERVAL, ActiveMQClient.DEFAULT_RETRY_INTERVAL_MULTIPLIER, ActiveMQClient.DEFAULT_RECONNECT_ATTEMPTS);
      Connection conn = null;

      try {
         conn = cf.createConnection();

         conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         fail("Should throw exception");
      } catch (JMSException e) {
         // Ok
      }
      if (conn != null) {
         conn.close();
      }

      testSettersThrowException(cf);
   }

   @Test
   public void testDefaultConstructorAndSetConnectorPairs() throws Exception {
      ActiveMQConnectionFactory cf = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, primaryTC);

      assertFactoryParams(cf, new TransportConfiguration[]{primaryTC}, null, null, ActiveMQClient.DEFAULT_CLIENT_FAILURE_CHECK_PERIOD, ActiveMQClient.DEFAULT_CONNECTION_TTL, ActiveMQClient.DEFAULT_CALL_TIMEOUT, ActiveMQClient.DEFAULT_CALL_FAILOVER_TIMEOUT, ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE, ActiveMQClient.DEFAULT_CONSUMER_WINDOW_SIZE, ActiveMQClient.DEFAULT_CONSUMER_MAX_RATE, ActiveMQClient.DEFAULT_CONFIRMATION_WINDOW_SIZE, ActiveMQClient.DEFAULT_PRODUCER_MAX_RATE, ActiveMQClient.DEFAULT_BLOCK_ON_ACKNOWLEDGE, ActiveMQClient.DEFAULT_BLOCK_ON_DURABLE_SEND, ActiveMQClient.DEFAULT_BLOCK_ON_NON_DURABLE_SEND, ActiveMQClient.DEFAULT_AUTO_GROUP, ActiveMQClient.DEFAULT_PRE_ACKNOWLEDGE, ActiveMQClient.DEFAULT_CONNECTION_LOAD_BALANCING_POLICY_CLASS_NAME, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE, ActiveMQClient.DEFAULT_DISCOVERY_INITIAL_WAIT_TIMEOUT, ActiveMQClient.DEFAULT_USE_GLOBAL_POOLS, ActiveMQClient.DEFAULT_SCHEDULED_THREAD_POOL_MAX_SIZE, ActiveMQClient.DEFAULT_THREAD_POOL_MAX_SIZE, ActiveMQClient.DEFAULT_RETRY_INTERVAL, ActiveMQClient.DEFAULT_RETRY_INTERVAL_MULTIPLIER, ActiveMQClient.DEFAULT_RECONNECT_ATTEMPTS);

      Connection conn = cf.createConnection();

      conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      testSettersThrowException(cf);

      conn.close();
   }

   @Test
   public void testDiscoveryConstructor() throws Exception {
      DiscoveryGroupConfiguration groupConfiguration = new DiscoveryGroupConfiguration().setBroadcastEndpointFactory(new UDPBroadcastEndpointFactory().setGroupAddress(groupAddress).setGroupPort(groupPort));
      ActiveMQConnectionFactory cf = ActiveMQJMSClient.createConnectionFactoryWithoutHA(groupConfiguration, JMSFactoryType.CF);
      assertFactoryParams(cf, null, groupConfiguration, null, ActiveMQClient.DEFAULT_CLIENT_FAILURE_CHECK_PERIOD, ActiveMQClient.DEFAULT_CONNECTION_TTL, ActiveMQClient.DEFAULT_CALL_TIMEOUT, ActiveMQClient.DEFAULT_CALL_FAILOVER_TIMEOUT, ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE, ActiveMQClient.DEFAULT_CONSUMER_WINDOW_SIZE, ActiveMQClient.DEFAULT_CONSUMER_MAX_RATE, ActiveMQClient.DEFAULT_CONFIRMATION_WINDOW_SIZE, ActiveMQClient.DEFAULT_PRODUCER_MAX_RATE, ActiveMQClient.DEFAULT_BLOCK_ON_ACKNOWLEDGE, ActiveMQClient.DEFAULT_BLOCK_ON_DURABLE_SEND, ActiveMQClient.DEFAULT_BLOCK_ON_NON_DURABLE_SEND, ActiveMQClient.DEFAULT_AUTO_GROUP, ActiveMQClient.DEFAULT_PRE_ACKNOWLEDGE, ActiveMQClient.DEFAULT_CONNECTION_LOAD_BALANCING_POLICY_CLASS_NAME, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE, ActiveMQClient.DEFAULT_DISCOVERY_INITIAL_WAIT_TIMEOUT, ActiveMQClient.DEFAULT_USE_GLOBAL_POOLS, ActiveMQClient.DEFAULT_SCHEDULED_THREAD_POOL_MAX_SIZE, ActiveMQClient.DEFAULT_THREAD_POOL_MAX_SIZE, ActiveMQClient.DEFAULT_RETRY_INTERVAL, ActiveMQClient.DEFAULT_RETRY_INTERVAL_MULTIPLIER, ActiveMQClient.DEFAULT_RECONNECT_ATTEMPTS);
      Connection conn = cf.createConnection();

      conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      testSettersThrowException(cf);

      conn.close();
   }

   @Test
   public void testStaticConnectorListConstructor() throws Exception {
      ActiveMQConnectionFactory cf = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, primaryTC);
      assertFactoryParams(cf, new TransportConfiguration[]{primaryTC}, null, null, ActiveMQClient.DEFAULT_CLIENT_FAILURE_CHECK_PERIOD, ActiveMQClient.DEFAULT_CONNECTION_TTL, ActiveMQClient.DEFAULT_CALL_TIMEOUT, ActiveMQClient.DEFAULT_CALL_FAILOVER_TIMEOUT, ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE, ActiveMQClient.DEFAULT_CONSUMER_WINDOW_SIZE, ActiveMQClient.DEFAULT_CONSUMER_MAX_RATE, ActiveMQClient.DEFAULT_CONFIRMATION_WINDOW_SIZE, ActiveMQClient.DEFAULT_PRODUCER_MAX_RATE, ActiveMQClient.DEFAULT_BLOCK_ON_ACKNOWLEDGE, ActiveMQClient.DEFAULT_BLOCK_ON_DURABLE_SEND, ActiveMQClient.DEFAULT_BLOCK_ON_NON_DURABLE_SEND, ActiveMQClient.DEFAULT_AUTO_GROUP, ActiveMQClient.DEFAULT_PRE_ACKNOWLEDGE, ActiveMQClient.DEFAULT_CONNECTION_LOAD_BALANCING_POLICY_CLASS_NAME, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE, ActiveMQClient.DEFAULT_DISCOVERY_INITIAL_WAIT_TIMEOUT, ActiveMQClient.DEFAULT_USE_GLOBAL_POOLS, ActiveMQClient.DEFAULT_SCHEDULED_THREAD_POOL_MAX_SIZE, ActiveMQClient.DEFAULT_THREAD_POOL_MAX_SIZE, ActiveMQClient.DEFAULT_RETRY_INTERVAL, ActiveMQClient.DEFAULT_RETRY_INTERVAL_MULTIPLIER, ActiveMQClient.DEFAULT_RECONNECT_ATTEMPTS);
      Connection conn = cf.createConnection();

      conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      testSettersThrowException(cf);

      conn.close();

   }

   @Test
   public void testStaticConnectorPrimaryConstructor() throws Exception {
      ActiveMQConnectionFactory cf = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, primaryTC);
      assertFactoryParams(cf, new TransportConfiguration[]{primaryTC}, null, null, ActiveMQClient.DEFAULT_CLIENT_FAILURE_CHECK_PERIOD, ActiveMQClient.DEFAULT_CONNECTION_TTL, ActiveMQClient.DEFAULT_CALL_TIMEOUT, ActiveMQClient.DEFAULT_CALL_FAILOVER_TIMEOUT, ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE, ActiveMQClient.DEFAULT_CONSUMER_WINDOW_SIZE, ActiveMQClient.DEFAULT_CONSUMER_MAX_RATE, ActiveMQClient.DEFAULT_CONFIRMATION_WINDOW_SIZE, ActiveMQClient.DEFAULT_PRODUCER_MAX_RATE, ActiveMQClient.DEFAULT_BLOCK_ON_ACKNOWLEDGE, ActiveMQClient.DEFAULT_BLOCK_ON_DURABLE_SEND, ActiveMQClient.DEFAULT_BLOCK_ON_NON_DURABLE_SEND, ActiveMQClient.DEFAULT_AUTO_GROUP, ActiveMQClient.DEFAULT_PRE_ACKNOWLEDGE, ActiveMQClient.DEFAULT_CONNECTION_LOAD_BALANCING_POLICY_CLASS_NAME, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE, ActiveMQClient.DEFAULT_DISCOVERY_INITIAL_WAIT_TIMEOUT, ActiveMQClient.DEFAULT_USE_GLOBAL_POOLS, ActiveMQClient.DEFAULT_SCHEDULED_THREAD_POOL_MAX_SIZE, ActiveMQClient.DEFAULT_THREAD_POOL_MAX_SIZE, ActiveMQClient.DEFAULT_RETRY_INTERVAL, ActiveMQClient.DEFAULT_RETRY_INTERVAL_MULTIPLIER, ActiveMQClient.DEFAULT_RECONNECT_ATTEMPTS);
      Connection conn = cf.createConnection();

      conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      testSettersThrowException(cf);

      cf.close();

      conn.close();
   }

   @Test
   public void testGettersAndSetters() {
      ActiveMQConnectionFactory cf = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, primaryTC);

      long clientFailureCheckPeriod = RandomUtil.randomPositiveLong();
      long connectionTTL = RandomUtil.randomPositiveLong();
      long callTimeout = RandomUtil.randomPositiveLong();
      int minLargeMessageSize = RandomUtil.randomPositiveInt();
      int consumerWindowSize = RandomUtil.randomPositiveInt();
      int consumerMaxRate = RandomUtil.randomPositiveInt();
      int confirmationWindowSize = RandomUtil.randomPositiveInt();
      int producerMaxRate = RandomUtil.randomPositiveInt();
      boolean blockOnAcknowledge = RandomUtil.randomBoolean();
      boolean blockOnDurableSend = RandomUtil.randomBoolean();
      boolean blockOnNonDurableSend = RandomUtil.randomBoolean();
      boolean autoGroup = RandomUtil.randomBoolean();
      boolean preAcknowledge = RandomUtil.randomBoolean();
      String loadBalancingPolicyClassName = RandomUtil.randomUUIDString();
      boolean useGlobalPools = RandomUtil.randomBoolean();
      int scheduledThreadPoolMaxSize = RandomUtil.randomPositiveInt();
      int threadPoolMaxSize = RandomUtil.randomPositiveInt();
      long retryInterval = RandomUtil.randomPositiveLong();
      double retryIntervalMultiplier = RandomUtil.randomDouble();
      int reconnectAttempts = RandomUtil.randomPositiveInt();
      cf.setClientFailureCheckPeriod(clientFailureCheckPeriod);
      cf.setConnectionTTL(connectionTTL);
      cf.setCallTimeout(callTimeout);
      cf.setMinLargeMessageSize(minLargeMessageSize);
      cf.setConsumerWindowSize(consumerWindowSize);
      cf.setConsumerMaxRate(consumerMaxRate);
      cf.setConfirmationWindowSize(confirmationWindowSize);
      cf.setProducerMaxRate(producerMaxRate);
      cf.setBlockOnAcknowledge(blockOnAcknowledge);
      cf.setBlockOnDurableSend(blockOnDurableSend);
      cf.setBlockOnNonDurableSend(blockOnNonDurableSend);
      cf.setAutoGroup(autoGroup);
      cf.setPreAcknowledge(preAcknowledge);
      cf.setConnectionLoadBalancingPolicyClassName(loadBalancingPolicyClassName);
      cf.setUseGlobalPools(useGlobalPools);
      cf.setScheduledThreadPoolMaxSize(scheduledThreadPoolMaxSize);
      cf.setThreadPoolMaxSize(threadPoolMaxSize);
      cf.setRetryInterval(retryInterval);
      cf.setRetryIntervalMultiplier(retryIntervalMultiplier);
      cf.setReconnectAttempts(reconnectAttempts);
      assertEquals(clientFailureCheckPeriod, cf.getClientFailureCheckPeriod());
      assertEquals(connectionTTL, cf.getConnectionTTL());
      assertEquals(callTimeout, cf.getCallTimeout());
      assertEquals(minLargeMessageSize, cf.getMinLargeMessageSize());
      assertEquals(consumerWindowSize, cf.getConsumerWindowSize());
      assertEquals(consumerMaxRate, cf.getConsumerMaxRate());
      assertEquals(confirmationWindowSize, cf.getConfirmationWindowSize());
      assertEquals(producerMaxRate, cf.getProducerMaxRate());
      assertEquals(blockOnAcknowledge, cf.isBlockOnAcknowledge());
      assertEquals(blockOnDurableSend, cf.isBlockOnDurableSend());
      assertEquals(blockOnNonDurableSend, cf.isBlockOnNonDurableSend());
      assertEquals(autoGroup, cf.isAutoGroup());
      assertEquals(preAcknowledge, cf.isPreAcknowledge());
      assertEquals(loadBalancingPolicyClassName, cf.getConnectionLoadBalancingPolicyClassName());
      assertEquals(useGlobalPools, cf.isUseGlobalPools());
      assertEquals(scheduledThreadPoolMaxSize, cf.getScheduledThreadPoolMaxSize());
      assertEquals(threadPoolMaxSize, cf.getThreadPoolMaxSize());
      assertEquals(retryInterval, cf.getRetryInterval());
      assertEquals(retryIntervalMultiplier, cf.getRetryIntervalMultiplier(), 0.0001);
      assertEquals(reconnectAttempts, cf.getReconnectAttempts());

      cf.close();
   }

   @Test
   public void testDeserializationOptions() throws Exception {
      testDeserializationOptions(false, false);
   }

   @Test
   public void testDeserializationOptionsJndi() throws Exception {
      testDeserializationOptions(true, false);
   }

   @Test
   public void testDeserializationOptionsBrowser() throws Exception {
      testDeserializationOptions(false, true);
   }

   @Test
   public void testDeserializationOptionsJndiBrowser() throws Exception {
      testDeserializationOptions(true, true);
   }

   private void testDeserializationOptions(boolean useJndi, boolean useBrowser) throws Exception {
      String qname = "SerialTestQueue";
      liveService.createQueue(QueueConfiguration.of(qname).setRoutingType(RoutingType.ANYCAST));

      //default ok
      String denyList = null;
      String allowList = null;
      Object obj = receiveObjectMessage(denyList, allowList, qname, new TestClass1(), useJndi, useBrowser);
      assertInstanceOf(TestClass1.class, obj, "Object is " + obj);

      //not in the allow list
      denyList = "java.lang";
      allowList = "some.other.package1";
      obj = receiveObjectMessage(denyList, allowList, qname, new TestClass1(), useJndi, useBrowser);
      assertInstanceOf(JMSException.class, obj, "Object is " + obj);
      //but String always trusted
      obj = receiveObjectMessage(denyList, allowList, qname, new String("hello"), useJndi, useBrowser);
      assertEquals("hello", obj, "java.lang.String always trusted ");

      //in the denylist
      denyList = "org.apache.activemq.artemis.tests.integration.jms.serializables";
      allowList = "org.apache.activemq.artemis.tests.integration.jms.serializables";
      obj = receiveObjectMessage(denyList, allowList, qname, new TestClass1(), useJndi, useBrowser);
      assertInstanceOf(JMSException.class, obj, "Object is " + obj);

      //deny list parent package
      denyList = "org.apache.activemq.artemis";
      allowList = "org.apache.activemq.artemis.tests.integration.jms.serializables";
      obj = receiveObjectMessage(denyList, allowList, qname, new TestClass1(), useJndi, useBrowser);
      assertInstanceOf(JMSException.class, obj, "Object is " + obj);

      //in allow list
      denyList = "some.other.package";
      allowList = "org.apache.activemq.artemis.tests.integration.jms.serializables";
      obj = receiveObjectMessage(denyList, allowList, qname, new TestClass1(), useJndi, useBrowser);
      assertInstanceOf(TestClass1.class, obj, "Object is " + obj);

      //parent in allow list
      denyList = "some.other.package";
      allowList = "org.apache.activemq.artemis.tests.integration.jms";
      obj = receiveObjectMessage(denyList, allowList, qname, new TestClass1(), useJndi, useBrowser);
      assertInstanceOf(TestClass1.class, obj, "Object is " + obj);

      //sub package in allow list
      denyList = "some.other.package";
      allowList = "org.apache.activemq.artemis.tests.integration.jms.serializables.pkg1";
      obj = receiveObjectMessage(denyList, allowList, qname, new TestClass1(), useJndi, useBrowser);
      assertInstanceOf(JMSException.class, obj, "Object is " + obj);

      //wild card allow list but deny listed
      denyList = "org.apache.activemq.artemis.tests.integration.jms.serializables";
      allowList = "*";
      obj = receiveObjectMessage(denyList, allowList, qname, new TestClass1(), useJndi, useBrowser);
      assertInstanceOf(JMSException.class, obj, "Object is " + obj);

      //wild card allow list and not deny listed
      denyList = "some.other.package";
      allowList = "*";
      obj = receiveObjectMessage(denyList, allowList, qname, new TestClass1(), useJndi, useBrowser);
      assertInstanceOf(TestClass1.class, obj, "Object is " + obj);

      //wild card deny list
      denyList = "*";
      allowList = "*";
      obj = receiveObjectMessage(denyList, allowList, qname, new TestClass1(), useJndi, useBrowser);
      assertInstanceOf(JMSException.class, obj, "Object is " + obj);
   }

   @Test
   public void testDeprecatedSystemPropertyBlackWhiteListDefault() throws Exception {
      System.setProperty(ObjectInputStreamWithClassLoader.BLACKLIST_PROPERTY, "*");
      System.setProperty(ObjectInputStreamWithClassLoader.WHITELIST_PROPERTY, "some.other.package");

      String qname = "SerialTestQueue";
      liveService.createQueue(QueueConfiguration.of(qname).setRoutingType(RoutingType.ANYCAST));

      try {
         String denyList = null;
         String allowList = null;
         Object obj = receiveObjectMessage(denyList, allowList, qname, new TestClass1(), false, false);
         assertInstanceOf(JMSException.class, obj, "Object is " + obj);
         //but String always trusted
         obj = receiveObjectMessage(denyList, allowList, qname, new String("hello"), false, false);
         assertEquals("hello", obj, "java.lang.String always trusted " + obj);

         //override
         denyList = "some.other.package";
         allowList = "org.apache.activemq.artemis.tests.integration";
         obj = receiveObjectMessage(denyList, allowList, qname, new TestClass1(), false, false);
         assertInstanceOf(TestClass1.class, obj, "Object is " + obj);
         //but String always trusted
         obj = receiveObjectMessage(denyList, allowList, qname, new String("hello"), false, false);
         assertEquals("hello", obj, "java.lang.String always trusted " + obj);
      } finally {
         System.clearProperty(ObjectInputStreamWithClassLoader.BLACKLIST_PROPERTY);
         System.clearProperty(ObjectInputStreamWithClassLoader.WHITELIST_PROPERTY);
      }
   }

   @Test
   public void testSystemPropertyDenyAllowListDefault() throws Exception {
      System.setProperty(ObjectInputStreamWithClassLoader.DENYLIST_PROPERTY, "*");
      System.setProperty(ObjectInputStreamWithClassLoader.ALLOWLIST_PROPERTY, "some.other.package");

      String qname = "SerialTestQueue";
      liveService.createQueue(QueueConfiguration.of(qname).setRoutingType(RoutingType.ANYCAST));

      try {
         String denyList = null;
         String allowList = null;
         Object obj = receiveObjectMessage(denyList, allowList, qname, new TestClass1(), false, false);
         assertInstanceOf(JMSException.class, obj, "Object is " + obj);
         //but String always trusted
         obj = receiveObjectMessage(denyList, allowList, qname, new String("hello"), false, false);
         assertEquals("hello", obj, "java.lang.String always trusted " + obj);

         //override
         denyList = "some.other.package";
         allowList = "org.apache.activemq.artemis.tests.integration";
         obj = receiveObjectMessage(denyList, allowList, qname, new TestClass1(), false, false);
         assertInstanceOf(TestClass1.class, obj, "Object is " + obj);
         //but String always trusted
         obj = receiveObjectMessage(denyList, allowList, qname, new String("hello"), false, false);
         assertEquals("hello", obj, "java.lang.String always trusted " + obj);
      } finally {
         System.clearProperty(ObjectInputStreamWithClassLoader.DENYLIST_PROPERTY);
         System.clearProperty(ObjectInputStreamWithClassLoader.ALLOWLIST_PROPERTY);
      }
   }

   private Object receiveObjectMessage(String denyList,
                                       String allowList,
                                       String qname,
                                       Serializable obj,
                                       boolean useJndi,
                                       boolean useBrowser) throws Exception {
      sendObjectMessage(qname, obj);

      StringBuilder query = new StringBuilder("");
      if (denyList != null) {
         query.append("?");
         query.append("deserializationDenyList=");
         query.append(denyList);

         if (allowList != null) {
            query.append("&");
            query.append("deserializationAllowList=");
            query.append(allowList);
         }
      } else {
         if (allowList != null) {
            query.append("?deserializationAllowList=");
            query.append(allowList);
         }
      }

      ActiveMQConnectionFactory factory = null;
      if (useJndi) {
         Hashtable<String, Object> props = new Hashtable<>();
         props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
         props.put("connectionFactory.VmConnectionFactory", "vm://0" + query);
         Context ctx = new InitialContext(props);
         factory = (ActiveMQConnectionFactory) ctx.lookup("VmConnectionFactory");
      } else {
         factory = new ActiveMQConnectionFactory("vm://0" + query);
      }

      try (Connection connection = factory.createConnection()) {
         connection.start();
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(qname);
         Object result = null;
         if (useBrowser) {
            QueueBrowser browser = session.createBrowser(queue);
            ObjectMessage objMessage = (ObjectMessage) browser.getEnumeration().nextElement();
            //drain message before triggering deserialization
            MessageConsumer consumer = session.createConsumer(queue);
            consumer.receive(5000);
            result = objMessage.getObject();
         } else {
            MessageConsumer consumer = session.createConsumer(queue);
            ObjectMessage objMessage = (ObjectMessage) consumer.receive(5000);
            assertNotNull(objMessage);
            result = objMessage.getObject();
         }
         return result;
      } catch (Exception e) {
         return e;
      }
   }

   private void sendObjectMessage(String qname, Serializable obj) throws Exception {
      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("vm://0");
      Connection connection = factory.createConnection();
      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue q = session.createQueue(qname);
         MessageProducer producer = session.createProducer(q);
         ObjectMessage objMessage = session.createObjectMessage();
         objMessage.setObject(obj);
         producer.send(objMessage);
      } finally {
         connection.close();
      }
   }

   private void testSettersThrowException(final ActiveMQConnectionFactory cf) {

      String clientID = RandomUtil.randomUUIDString();
      long clientFailureCheckPeriod = RandomUtil.randomPositiveLong();
      long connectionTTL = RandomUtil.randomPositiveLong();
      long callTimeout = RandomUtil.randomPositiveLong();
      int minLargeMessageSize = RandomUtil.randomPositiveInt();
      int consumerWindowSize = RandomUtil.randomPositiveInt();
      int consumerMaxRate = RandomUtil.randomPositiveInt();
      int confirmationWindowSize = RandomUtil.randomPositiveInt();
      int producerMaxRate = RandomUtil.randomPositiveInt();
      boolean blockOnAcknowledge = RandomUtil.randomBoolean();
      boolean blockOnDurableSend = RandomUtil.randomBoolean();
      boolean blockOnNonDurableSend = RandomUtil.randomBoolean();
      boolean autoGroup = RandomUtil.randomBoolean();
      boolean preAcknowledge = RandomUtil.randomBoolean();
      String loadBalancingPolicyClassName = RandomUtil.randomUUIDString();
      int dupsOKBatchSize = RandomUtil.randomPositiveInt();
      int transactionBatchSize = RandomUtil.randomPositiveInt();
      boolean useGlobalPools = RandomUtil.randomBoolean();
      int scheduledThreadPoolMaxSize = RandomUtil.randomPositiveInt();
      int threadPoolMaxSize = RandomUtil.randomPositiveInt();
      long retryInterval = RandomUtil.randomPositiveLong();
      double retryIntervalMultiplier = RandomUtil.randomDouble();
      int reconnectAttempts = RandomUtil.randomPositiveInt();

      try {
         cf.setClientID(clientID);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.setClientFailureCheckPeriod(clientFailureCheckPeriod);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.setConnectionTTL(connectionTTL);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.setCallTimeout(callTimeout);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.setMinLargeMessageSize(minLargeMessageSize);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.setConsumerWindowSize(consumerWindowSize);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.setConsumerMaxRate(consumerMaxRate);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.setConfirmationWindowSize(confirmationWindowSize);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.setProducerMaxRate(producerMaxRate);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.setBlockOnAcknowledge(blockOnAcknowledge);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.setBlockOnDurableSend(blockOnDurableSend);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.setBlockOnNonDurableSend(blockOnNonDurableSend);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.setAutoGroup(autoGroup);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.setPreAcknowledge(preAcknowledge);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.setConnectionLoadBalancingPolicyClassName(loadBalancingPolicyClassName);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.setDupsOKBatchSize(dupsOKBatchSize);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.setTransactionBatchSize(transactionBatchSize);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.setUseGlobalPools(useGlobalPools);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.setScheduledThreadPoolMaxSize(scheduledThreadPoolMaxSize);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.setThreadPoolMaxSize(threadPoolMaxSize);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.setRetryInterval(retryInterval);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.setRetryIntervalMultiplier(retryIntervalMultiplier);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }
      try {
         cf.setReconnectAttempts(reconnectAttempts);
         fail("Should throw exception");
      } catch (IllegalStateException e) {
         // OK
      }

      cf.getStaticConnectors();
      cf.getClientID();
      cf.getClientFailureCheckPeriod();
      cf.getConnectionTTL();
      cf.getCallTimeout();
      cf.getMinLargeMessageSize();
      cf.getConsumerWindowSize();
      cf.getConsumerMaxRate();
      cf.getConfirmationWindowSize();
      cf.getProducerMaxRate();
      cf.isBlockOnAcknowledge();
      cf.isBlockOnDurableSend();
      cf.isBlockOnNonDurableSend();
      cf.isAutoGroup();
      cf.isPreAcknowledge();
      cf.getConnectionLoadBalancingPolicyClassName();
      cf.getDupsOKBatchSize();
      cf.getTransactionBatchSize();
      cf.isUseGlobalPools();
      cf.getScheduledThreadPoolMaxSize();
      cf.getThreadPoolMaxSize();
      cf.getRetryInterval();
      cf.getRetryIntervalMultiplier();
      cf.getReconnectAttempts();

      cf.close();
   }

   private void assertFactoryParams(final ActiveMQConnectionFactory cf,
                                    final TransportConfiguration[] staticConnectors,
                                    final DiscoveryGroupConfiguration discoveryGroupConfiguration,
                                    final String clientID,
                                    final long clientFailureCheckPeriod,
                                    final long connectionTTL,
                                    final long callTimeout,
                                    final long callFailoverTimeout,
                                    final int minLargeMessageSize,
                                    final int consumerWindowSize,
                                    final int consumerMaxRate,
                                    final int confirmationWindowSize,
                                    final int producerMaxRate,
                                    final boolean blockOnAcknowledge,
                                    final boolean blockOnDurableSend,
                                    final boolean blockOnNonDurableSend,
                                    final boolean autoGroup,
                                    final boolean preAcknowledge,
                                    final String loadBalancingPolicyClassName,
                                    final int dupsOKBatchSize,
                                    final int transactionBatchSize,
                                    final long initialWaitTimeout,
                                    final boolean useGlobalPools,
                                    final int scheduledThreadPoolMaxSize,
                                    final int threadPoolMaxSize,
                                    final long retryInterval,
                                    final double retryIntervalMultiplier,
                                    final int reconnectAttempts) {
      TransportConfiguration[] cfStaticConnectors = cf.getStaticConnectors();
      if (staticConnectors == null) {
         assertNull(staticConnectors);
      } else {
         assertEquals(staticConnectors.length, cfStaticConnectors.length);

         for (int i = 0; i < staticConnectors.length; i++) {
            assertEquals(staticConnectors[i], cfStaticConnectors[i]);
         }
      }
      assertEquals(cf.getClientID(), clientID);
      assertEquals(cf.getClientFailureCheckPeriod(), clientFailureCheckPeriod);
      assertEquals(cf.getConnectionTTL(), connectionTTL);
      assertEquals(cf.getCallTimeout(), callTimeout);
      assertEquals(cf.getCallFailoverTimeout(), callFailoverTimeout);
      assertEquals(cf.getMinLargeMessageSize(), minLargeMessageSize);
      assertEquals(cf.getConsumerWindowSize(), consumerWindowSize);
      assertEquals(cf.getConsumerMaxRate(), consumerMaxRate);
      assertEquals(cf.getConfirmationWindowSize(), confirmationWindowSize);
      assertEquals(cf.getProducerMaxRate(), producerMaxRate);
      assertEquals(cf.isBlockOnAcknowledge(), blockOnAcknowledge);
      assertEquals(cf.isBlockOnDurableSend(), blockOnDurableSend);
      assertEquals(cf.isBlockOnNonDurableSend(), blockOnNonDurableSend);
      assertEquals(cf.isAutoGroup(), autoGroup);
      assertEquals(cf.isPreAcknowledge(), preAcknowledge);
      assertEquals(cf.getConnectionLoadBalancingPolicyClassName(), loadBalancingPolicyClassName);
      assertEquals(cf.getDupsOKBatchSize(), dupsOKBatchSize);
      assertEquals(cf.getTransactionBatchSize(), transactionBatchSize);
      assertEquals(cf.isUseGlobalPools(), useGlobalPools);
      assertEquals(cf.getScheduledThreadPoolMaxSize(), scheduledThreadPoolMaxSize);
      assertEquals(cf.getThreadPoolMaxSize(), threadPoolMaxSize);
      assertEquals(cf.getRetryInterval(), retryInterval);
      assertEquals(cf.getRetryIntervalMultiplier(), retryIntervalMultiplier, 0.00001);
      assertEquals(cf.getReconnectAttempts(), reconnectAttempts);
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      enableDiscoveryForTest();
      super.setUp();

      startServer();
   }

   private void startServer() throws Exception {
      primaryTC = new TransportConfiguration(INVM_CONNECTOR_FACTORY);
      Map<String, TransportConfiguration> connectors = new HashMap<>();
      connectors.put(primaryTC.getName(), primaryTC);
      List<String> connectorNames = new ArrayList<>();
      connectorNames.add(primaryTC.getName());

      Configuration primaryConf = createBasicConfig().addAcceptorConfiguration(new TransportConfiguration(INVM_ACCEPTOR_FACTORY)).setConnectorConfigurations(connectors).setHAPolicyConfiguration(new SharedStorePrimaryPolicyConfiguration());

      final long broadcastPeriod = 250;

      final String bcGroupName = "bc1";

      final int localBindPort = 5432;

      BroadcastGroupConfiguration bcConfig1 = new BroadcastGroupConfiguration().setName(bcGroupName).setBroadcastPeriod(broadcastPeriod).setConnectorInfos(connectorNames).setEndpointFactory(new UDPBroadcastEndpointFactory().setGroupAddress(groupAddress).setGroupPort(groupPort).setLocalBindPort(localBindPort));

      List<BroadcastGroupConfiguration> bcConfigs1 = new ArrayList<>();
      bcConfigs1.add(bcConfig1);
      primaryConf.setBroadcastGroupConfigurations(bcConfigs1);

      liveService = addServer(ActiveMQServers.newActiveMQServer(primaryConf, false));
      liveService.start();
   }

}

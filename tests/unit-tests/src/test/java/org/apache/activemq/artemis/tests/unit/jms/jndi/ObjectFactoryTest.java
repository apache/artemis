/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.unit.jms.jndi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.naming.Reference;
import javax.naming.StringRefAddr;

import java.net.URI;
import java.util.Map;

import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import org.apache.activemq.artemis.jndi.JNDIReferenceFactory;
import org.apache.activemq.artemis.utils.RandomUtil;

import org.apache.activemq.artemis.utils.uri.URISupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class ObjectFactoryTest {

   @Test
   @Timeout(1)
   public void testConnectionFactory() throws Exception {
      // Create sample connection factory
      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("vm://0");

      String clientID = RandomUtil.randomUUIDString();
      String user = RandomUtil.randomUUIDString();
      String password = RandomUtil.randomUUIDString();
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
      factory.setClientID(clientID);
      factory.setUser(user);
      factory.setPassword(password);
      factory.setClientFailureCheckPeriod(clientFailureCheckPeriod);
      factory.setConnectionTTL(connectionTTL);
      factory.setCallTimeout(callTimeout);
      factory.setMinLargeMessageSize(minLargeMessageSize);
      factory.setConsumerWindowSize(consumerWindowSize);
      factory.setConsumerMaxRate(consumerMaxRate);
      factory.setConfirmationWindowSize(confirmationWindowSize);
      factory.setProducerMaxRate(producerMaxRate);
      factory.setBlockOnAcknowledge(blockOnAcknowledge);
      factory.setBlockOnDurableSend(blockOnDurableSend);
      factory.setBlockOnNonDurableSend(blockOnNonDurableSend);
      factory.setAutoGroup(autoGroup);
      factory.setPreAcknowledge(preAcknowledge);
      factory.setConnectionLoadBalancingPolicyClassName(loadBalancingPolicyClassName);
      factory.setUseGlobalPools(useGlobalPools);
      factory.setScheduledThreadPoolMaxSize(scheduledThreadPoolMaxSize);
      factory.setThreadPoolMaxSize(threadPoolMaxSize);
      factory.setRetryInterval(retryInterval);
      factory.setRetryIntervalMultiplier(retryIntervalMultiplier);
      factory.setReconnectAttempts(reconnectAttempts);


      // Create reference
      Reference ref = JNDIReferenceFactory.createReference(factory.getClass().getName(), factory);

      // Get object created based on reference
      ActiveMQConnectionFactory temp;
      JNDIReferenceFactory refFactory = new JNDIReferenceFactory();
      temp = (ActiveMQConnectionFactory)refFactory.getObjectInstance(ref, null, null, null);

      // Check settings
      assertEquals(clientID, temp.getClientID());
      assertEquals(user, temp.getUser());
      assertEquals(password, temp.getPassword());
      assertEquals(clientFailureCheckPeriod, temp.getClientFailureCheckPeriod());
      assertEquals(connectionTTL, temp.getConnectionTTL());
      assertEquals(callTimeout, temp.getCallTimeout());
      assertEquals(minLargeMessageSize, temp.getMinLargeMessageSize());
      assertEquals(consumerWindowSize, temp.getConsumerWindowSize());
      assertEquals(consumerMaxRate, temp.getConsumerMaxRate());
      assertEquals(confirmationWindowSize, temp.getConfirmationWindowSize());
      assertEquals(producerMaxRate, temp.getProducerMaxRate());
      assertEquals(blockOnAcknowledge, temp.isBlockOnAcknowledge());
      assertEquals(blockOnDurableSend, temp.isBlockOnDurableSend());
      assertEquals(blockOnNonDurableSend, temp.isBlockOnNonDurableSend());
      assertEquals(autoGroup, temp.isAutoGroup());
      assertEquals(preAcknowledge, temp.isPreAcknowledge());
      assertEquals(loadBalancingPolicyClassName, temp.getConnectionLoadBalancingPolicyClassName());
      assertEquals(useGlobalPools, temp.isUseGlobalPools());
      assertEquals(scheduledThreadPoolMaxSize, temp.getScheduledThreadPoolMaxSize());
      assertEquals(threadPoolMaxSize, temp.getThreadPoolMaxSize());
      assertEquals(retryInterval, temp.getRetryInterval());
      assertEquals(retryIntervalMultiplier, temp.getRetryIntervalMultiplier(), 0.0001);
      assertEquals(reconnectAttempts, temp.getReconnectAttempts());

   }

   @Test
   @Timeout(1)
   public void testDestination() throws Exception {
      // Create sample destination
      ActiveMQDestination dest = (ActiveMQDestination) ActiveMQJMSClient.createQueue(RandomUtil.randomUUIDString());

      // Create reference
      Reference ref = JNDIReferenceFactory.createReference(dest.getClass().getName(), dest);

      // Get object created based on reference
      ActiveMQDestination temp;
      JNDIReferenceFactory refFactory = new JNDIReferenceFactory();
      temp = (ActiveMQDestination)refFactory.getObjectInstance(ref, null, null, null);

      // Check settings
      assertEquals(dest.getAddress(), temp.getAddress());
   }

   @Test
   public void testJndiSslParameters() throws Exception {
      Reference reference = new Reference(ActiveMQConnectionFactory.class.getName(), JNDIReferenceFactory.class.getName(), null);
      reference.add(new StringRefAddr("brokerURL", "(tcp://localhost:61616,tcp://localhost:5545,tcp://localhost:5555)?sslEnabled=false&trustStorePath=nopath"));
      reference.add(new StringRefAddr(TransportConstants.SSL_ENABLED_PROP_NAME, "true"));
      reference.add(new StringRefAddr(TransportConstants.TRUSTSTORE_PATH_PROP_NAME, "/path/to/trustStore"));
      reference.add(new StringRefAddr(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME, "trustStorePassword"));
      reference.add(new StringRefAddr(TransportConstants.KEYSTORE_PATH_PROP_NAME, "/path/to/keyStore"));
      reference.add(new StringRefAddr(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME, "keyStorePassword"));
      reference.add(new StringRefAddr("doesnotexist", "somevalue"));

      JNDIReferenceFactory referenceFactory = new JNDIReferenceFactory();
      ActiveMQConnectionFactory cf = (ActiveMQConnectionFactory)referenceFactory.getObjectInstance(reference, null, null, null);

      URI uri = cf.toURI();
      Map<String, String> params = URISupport.parseParameters(uri);

      assertEquals("true", params.get(TransportConstants.SSL_ENABLED_PROP_NAME));
      assertEquals("/path/to/trustStore", params.get(TransportConstants.TRUSTSTORE_PATH_PROP_NAME));
      assertEquals("trustStorePassword", params.get(TransportConstants.TRUSTSTORE_PASSWORD_PROP_NAME));
      assertEquals("/path/to/keyStore", params.get(TransportConstants.KEYSTORE_PATH_PROP_NAME));
      assertEquals("keyStorePassword", params.get(TransportConstants.KEYSTORE_PASSWORD_PROP_NAME));
      assertNull(params.get("doesnotexist"));
   }
}

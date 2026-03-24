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
package org.apache.activemq.artemis.tests.extras.jms.bridge;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.transaction.Transaction;
import javax.transaction.TransactionManager;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSConstants;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import static org.apache.activemq.artemis.core.settings.impl.AddressSettings.DEFAULT_MAX_DELIVERY_ATTEMPTS;
import org.apache.activemq.artemis.jms.bridge.ConnectionFactoryFactory;
import org.apache.activemq.artemis.jms.bridge.QualityOfServiceMode;
import org.apache.activemq.artemis.jms.bridge.impl.JMSBridgeImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQXAConnectionFactory;
import org.apache.activemq.artemis.service.extensions.ServiceUtils;
import org.apache.activemq.artemis.tests.integration.ra.DummyTransactionManager;
import org.apache.activemq.artemis.utils.DefaultSensitiveStringCodec;
import org.jboss.logging.Logger;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

public class JMSBridgeTest extends BridgeTestBase {

   private static final Logger log = Logger.getLogger(JMSBridgeTest.class);

   @Rule
   public Timeout timeout = new Timeout(120000);

   // MaxBatchSize but no MaxBatchTime

   @Test
   public void testNoMaxBatchTime_AtMostOnce_P() throws Exception {
      testNoMaxBatchTime(QualityOfServiceMode.AT_MOST_ONCE, true);
   }

   @Test
   public void testNoMaxBatchTime_DuplicatesOk_P() throws Exception {
      testNoMaxBatchTime(QualityOfServiceMode.DUPLICATES_OK, true);
   }

   @Test
   public void testNoMaxBatchTime_OnceAndOnlyOnce_P() throws Exception {
      testNoMaxBatchTime(QualityOfServiceMode.ONCE_AND_ONLY_ONCE, true);
   }

   @Test
   public void testNoMaxBatchTime_AtMostOnce_NP() throws Exception {
      testNoMaxBatchTime(QualityOfServiceMode.AT_MOST_ONCE, false);
   }

   @Test
   public void testNoMaxBatchTime_DuplicatesOk_NP() throws Exception {
      testNoMaxBatchTime(QualityOfServiceMode.DUPLICATES_OK, false);
   }

   @Test
   public void testNoMaxBatchTime_OnceAndOnlyOnce_NP() throws Exception {
      testNoMaxBatchTime(QualityOfServiceMode.ONCE_AND_ONLY_ONCE, false);
   }

   // Same server

   // MaxBatchSize but no MaxBatchTime

   @Test
   public void testNoMaxBatchTimeSameServer_AtMostOnce_P() throws Exception {
      testNoMaxBatchTimeSameServer(QualityOfServiceMode.AT_MOST_ONCE, true);
   }

   @Test
   public void testNoMaxBatchTimeSameServer_DuplicatesOk_P() throws Exception {
      testNoMaxBatchTimeSameServer(QualityOfServiceMode.DUPLICATES_OK, true);
   }

   @Test
   public void testNoMaxBatchTimeSameServer_OnceAndOnlyOnce_P() throws Exception {
      testNoMaxBatchTimeSameServer(QualityOfServiceMode.ONCE_AND_ONLY_ONCE, true);
   }

   @Test
   public void testNoMaxBatchTimeSameServer_AtMostOnce_NP() throws Exception {
      testNoMaxBatchTimeSameServer(QualityOfServiceMode.AT_MOST_ONCE, false);
   }

   @Test
   public void testNoMaxBatchTimeSameServer_DuplicatesOk_NP() throws Exception {
      testNoMaxBatchTimeSameServer(QualityOfServiceMode.DUPLICATES_OK, false);
   }

   @Test
   public void testNoMaxBatchTimeSameServer_OnceAndOnlyOnce_NP() throws Exception {
      testNoMaxBatchTimeSameServer(QualityOfServiceMode.ONCE_AND_ONLY_ONCE, false);
   }

   // MaxBatchTime but no MaxBatchSize

   @Test
   public void testMaxBatchTime_AtMostOnce_P() throws Exception {
      testMaxBatchTime(QualityOfServiceMode.AT_MOST_ONCE, true);
   }

   @Test
   public void testMaxBatchTime_DuplicatesOk_P() throws Exception {
      testMaxBatchTime(QualityOfServiceMode.DUPLICATES_OK, true);
   }

   @Test
   public void testMaxBatchTime_OnceAndOnlyOnce_P() throws Exception {
      testMaxBatchTime(QualityOfServiceMode.ONCE_AND_ONLY_ONCE, true);
   }

   @Test
   public void testMaxBatchTime_AtMostOnce_NP() throws Exception {
      testMaxBatchTime(QualityOfServiceMode.AT_MOST_ONCE, false);
   }

   @Test
   public void testMaxBatchTime_DuplicatesOk_NP() throws Exception {
      testMaxBatchTime(QualityOfServiceMode.DUPLICATES_OK, false);
   }

   @Test
   public void testMaxBatchTime_OnceAndOnlyOnce_NP() throws Exception {
      testMaxBatchTime(QualityOfServiceMode.ONCE_AND_ONLY_ONCE, false);
   }

   // Same server

   // MaxBatchTime but no MaxBatchSize

   @Test
   public void testMaxBatchTimeSameServer_AtMostOnce_P() throws Exception {
      testMaxBatchTimeSameServer(QualityOfServiceMode.AT_MOST_ONCE, true);
   }

   @Test
   public void testMaxBatchTimeSameServer_DuplicatesOk_P() throws Exception {
      testMaxBatchTimeSameServer(QualityOfServiceMode.DUPLICATES_OK, true);
   }

   @Test
   public void testMaxBatchTimeSameServer_OnceAndOnlyOnce_P() throws Exception {
      testMaxBatchTimeSameServer(QualityOfServiceMode.ONCE_AND_ONLY_ONCE, true);
   }

   @Test
   public void testMaxBatchTimeSameServer_AtMostOnce_NP() throws Exception {
      testMaxBatchTimeSameServer(QualityOfServiceMode.AT_MOST_ONCE, false);
   }

   @Test
   public void testMaxBatchTimeSameServer_DuplicatesOk_NP() throws Exception {
      testMaxBatchTimeSameServer(QualityOfServiceMode.DUPLICATES_OK, false);
   }

   @Test
   public void testMaxBatchTimeSameServer_OnceAndOnlyOnce_NP() throws Exception {
      testMaxBatchTimeSameServer(QualityOfServiceMode.ONCE_AND_ONLY_ONCE, false);
   }

   // Stress with batch size of 50

   @Test
   public void testStress_AtMostOnce_P_50() throws Exception {
      testStress(QualityOfServiceMode.AT_MOST_ONCE, true, 50);
   }

   @Test
   public void testStress_DuplicatesOk_P_50() throws Exception {
      testStress(QualityOfServiceMode.DUPLICATES_OK, true, 50);
   }

   @Test
   public void testStress_OnceAndOnlyOnce_P_50() throws Exception {
      testStress(QualityOfServiceMode.ONCE_AND_ONLY_ONCE, true, 50);
   }

   @Test
   public void testStress_AtMostOnce_NP_50() throws Exception {
      testStress(QualityOfServiceMode.AT_MOST_ONCE, false, 50);
   }

   @Test
   public void testStress_DuplicatesOk_NP_50() throws Exception {
      testStress(QualityOfServiceMode.DUPLICATES_OK, false, 50);
   }

   @Test
   public void testStress_OnceAndOnlyOnce_NP_50() throws Exception {
      testStress(QualityOfServiceMode.ONCE_AND_ONLY_ONCE, false, 50);
   }

   // Stress with batch size of 1

   @Test
   public void testStress_AtMostOnce_P_1() throws Exception {
      testStress(QualityOfServiceMode.AT_MOST_ONCE, true, 1);
   }

   @Test
   public void testStress_DuplicatesOk_P_1() throws Exception {
      testStress(QualityOfServiceMode.DUPLICATES_OK, true, 1);
   }

   @Test
   public void testStress_OnceAndOnlyOnce_P_1() throws Exception {
      testStress(QualityOfServiceMode.ONCE_AND_ONLY_ONCE, true, 1);
   }

   @Test
   public void testStress_AtMostOnce_NP_1() throws Exception {
      testStress(QualityOfServiceMode.AT_MOST_ONCE, false, 1);
   }

   @Test
   public void testStress_DuplicatesOk_NP_1() throws Exception {
      testStress(QualityOfServiceMode.DUPLICATES_OK, false, 1);
   }

   @Test
   public void testStress_OnceAndOnlyOnce_NP_1() throws Exception {
      testStress(QualityOfServiceMode.ONCE_AND_ONLY_ONCE, false, 1);
   }

   // Max batch time

   @Test
   public void testStressMaxBatchTime_OnceAndOnlyOnce_NP() throws Exception {
      testStressBatchTime(QualityOfServiceMode.ONCE_AND_ONLY_ONCE, false, 200);
   }

   @Test
   public void testStressMaxBatchTime_OnceAndOnlyOnce_P() throws Exception {
      testStressBatchTime(QualityOfServiceMode.ONCE_AND_ONLY_ONCE, true, 200);
   }

   // Stress on same server

   // Stress with batch size of 50

   @Test
   public void testStressSameServer_AtMostOnce_P_50() throws Exception {
      testStressSameServer(QualityOfServiceMode.AT_MOST_ONCE, true, 50);
   }

   @Test
   public void testStressSameServer_DuplicatesOk_P_50() throws Exception {
      testStressSameServer(QualityOfServiceMode.DUPLICATES_OK, true, 50);
   }

   @Test
   public void testStressSameServer_OnceAndOnlyOnce_P_50() throws Exception {
      testStress(QualityOfServiceMode.ONCE_AND_ONLY_ONCE, true, 50);
   }

   @Test
   public void testStressSameServer_AtMostOnce_NP_50() throws Exception {
      testStressSameServer(QualityOfServiceMode.AT_MOST_ONCE, false, 50);
   }

   @Test
   public void testStressSameServer_DuplicatesOk_NP_50() throws Exception {
      testStressSameServer(QualityOfServiceMode.DUPLICATES_OK, false, 50);
   }

   @Test
   public void testStressSameServer_OnceAndOnlyOnce_NP_50() throws Exception {
      testStressSameServer(QualityOfServiceMode.ONCE_AND_ONLY_ONCE, false, 50);
   }

   // Stress with batch size of 1

   @Test
   public void testStressSameServer_AtMostOnce_P_1() throws Exception {
      testStressSameServer(QualityOfServiceMode.AT_MOST_ONCE, true, 1);
   }

   @Test
   public void testStressSameServer_DuplicatesOk_P_1() throws Exception {
      testStressSameServer(QualityOfServiceMode.DUPLICATES_OK, true, 1);
   }

   @Test
   public void testStressSameServer_OnceAndOnlyOnce_P_1() throws Exception {
      testStressSameServer(QualityOfServiceMode.ONCE_AND_ONLY_ONCE, true, 1);
   }

   @Test
   public void testStressSameServer_AtMostOnce_NP_1() throws Exception {
      testStressSameServer(QualityOfServiceMode.AT_MOST_ONCE, false, 1);
   }

   @Test
   public void testStressSameServer_DuplicatesOk_NP_1() throws Exception {
      testStressSameServer(QualityOfServiceMode.DUPLICATES_OK, false, 1);
   }

   @Test
   public void testStressSameServer_OnceAndOnlyOnce_NP_1() throws Exception {
      testStressSameServer(QualityOfServiceMode.ONCE_AND_ONLY_ONCE, false, 1);
   }

   @Test
   public void testStartBridgeFirst() throws Exception {
      //stop the source server, we want to start the bridge first
      server0.stop();
      JMSBridgeImpl bridge = null;

      ConnectionFactoryFactory factInUse0 = cff0;
      ConnectionFactoryFactory factInUse1 = cff1;
      try {
         final int NUM_MESSAGES = 10;

         bridge = new JMSBridgeImpl(factInUse0, factInUse1, sourceQueueFactory, targetQueueFactory, null, null, null, null, null, 5000, 10, QualityOfServiceMode.AT_MOST_ONCE, NUM_MESSAGES, -1, null, null, false).setBridgeName("test-bridge");

         bridge.start();

         //now start the server
         server0.start();

         // Send half the messages

         sendMessages(cf0, sourceQueue, 0, NUM_MESSAGES / 2, false, false);

         // Verify none are received

         checkEmpty(targetQueue, 1);

         // Send the other half

         sendMessages(cf0, sourceQueue, NUM_MESSAGES / 2, NUM_MESSAGES / 2, false, false);

         // This should now be receivable

         checkAllMessageReceivedInOrder(cf1, targetQueue, 0, NUM_MESSAGES, false);

         // Send another batch with one more than batch size

         sendMessages(cf0, sourceQueue, 0, NUM_MESSAGES + 1, false, false);

         // Make sure only batch size are received

         checkAllMessageReceivedInOrder(cf1, targetQueue, 0, NUM_MESSAGES, false);

         // Final batch

         sendMessages(cf0, sourceQueue, 0, NUM_MESSAGES - 1, false, false);

         checkAllMessageReceivedInOrder(cf1, targetQueue, NUM_MESSAGES, 1, false);

         checkAllMessageReceivedInOrder(cf1, targetQueue, 0, NUM_MESSAGES - 1, false);

      } finally {
         if (bridge != null) {
            instanceLog.debug("Stopping bridge");
            bridge.stop();
         }
      }
   }

   @Test
   public void testParams() throws Exception {
      JMSBridgeImpl bridge = null;

      QualityOfServiceMode qosMode = QualityOfServiceMode.AT_MOST_ONCE;

      int batchSize = 10;

      int maxBatchTime = -1;

      String sourceUsername = null;

      String sourcePassword = null;

      String destUsername = null;

      String destPassword = null;

      String selector = null;

      long failureRetryInterval = 5000;

      int maxRetries = 10;

      String subName = null;

      String clientID = null;

      try {
         bridge = new JMSBridgeImpl(null, cff1, sourceQueueFactory, targetQueueFactory, sourceUsername, sourcePassword, destUsername, destPassword, selector, failureRetryInterval, maxRetries, qosMode, batchSize, maxBatchTime, subName, clientID, false).setBridgeName("test-bridge");
         fail("expected exception");
      } catch (IllegalArgumentException e) {
         // Ok
      } finally {
         stopComponent(bridge);
      }

      try {
         bridge = new JMSBridgeImpl(cff0, null, sourceQueueFactory, targetQueueFactory, sourceUsername, sourcePassword, destUsername, destPassword, selector, failureRetryInterval, maxRetries, qosMode, batchSize, maxBatchTime, subName, clientID, false).setBridgeName("test-bridge");
         fail("expected exception");
      } catch (IllegalArgumentException e) {
         // Ok
      } finally {
         stopComponent(bridge);
      }

      try {
         bridge = new JMSBridgeImpl(cff0, cff1, null, targetQueueFactory, sourceUsername, sourcePassword, destUsername, destPassword, selector, failureRetryInterval, maxRetries, qosMode, batchSize, maxBatchTime, subName, clientID, false).setBridgeName("test-bridge");
         fail("expected exception");
      } catch (IllegalArgumentException e) {
         // Ok
      } finally {
         stopComponent(bridge);
      }

      try {
         bridge = new JMSBridgeImpl(cff0, cff1, sourceQueueFactory, null, sourceUsername, sourcePassword, destUsername, destPassword, selector, failureRetryInterval, maxRetries, qosMode, batchSize, maxBatchTime, subName, clientID, false).setBridgeName("test-bridge");
         fail("expected exception");
      } catch (IllegalArgumentException e) {
         // Ok
      } finally {
         stopComponent(bridge);
      }

      try {
         bridge = new JMSBridgeImpl(cff0, cff1, sourceQueueFactory, targetQueueFactory, sourceUsername, sourcePassword, destUsername, destPassword, selector, -2, maxRetries, qosMode, batchSize, maxBatchTime, subName, clientID, false).setBridgeName("test-bridge");
         fail("expected exception");
      } catch (IllegalArgumentException e) {
         // Ok
      } finally {
         stopComponent(bridge);
      }

      try {
         bridge = new JMSBridgeImpl(cff0, cff1, sourceQueueFactory, targetQueueFactory, sourceUsername, sourcePassword, destUsername, destPassword, selector, -1, 10, qosMode, batchSize, maxBatchTime, subName, clientID, false).setBridgeName("test-bridge");
         fail("expected exception");
      } catch (IllegalArgumentException e) {
         // Ok
      } finally {
         stopComponent(bridge);
      }

      try {
         bridge = new JMSBridgeImpl(cff0, cff1, sourceQueueFactory, null, sourceUsername, sourcePassword, destUsername, destPassword, selector, failureRetryInterval, maxRetries, qosMode, 0, maxBatchTime, subName, clientID, false).setBridgeName("test-bridge");
         fail("expected exception");
      } catch (IllegalArgumentException e) {
         // Ok
      } finally {
         stopComponent(bridge);
      }

      try {
         bridge = new JMSBridgeImpl(cff0, cff1, sourceQueueFactory, null, sourceUsername, sourcePassword, destUsername, destPassword, selector, failureRetryInterval, maxRetries, qosMode, batchSize, -2, subName, clientID, false).setBridgeName("test-bridge");
         fail("expected exception");
      } catch (IllegalArgumentException e) {
         // Ok
      } finally {
         stopComponent(bridge);
      }
   }

   @Test
   public void testStartStopStart() throws Exception {
      JMSBridgeImpl bridge = null;

      Connection connSource = null;

      Connection connTarget = null;

      try {
         final int NUM_MESSAGES = 10;

         bridge = new JMSBridgeImpl(cff0, cff1, sourceQueueFactory, targetQueueFactory, null, null, null, null, null, 5000, 10, QualityOfServiceMode.AT_MOST_ONCE, 1, -1, null, null, false).setBridgeName("test-bridge");

         bridge.start();

         bridge.stop();

         bridge.start();

         connSource = cf0.createConnection();

         Session sessSend = connSource.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod = sessSend.createProducer(sourceQueue);

         for (int i = 0; i < NUM_MESSAGES; i++) {
            TextMessage tm = sessSend.createTextMessage("message" + i);
            prod.send(tm);
         }

         connTarget = cf1.createConnection();
         Session sessRec = connTarget.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageConsumer cons = sessRec.createConsumer(targetQueue);

         connTarget.start();

         for (int i = 0; i < NUM_MESSAGES; i++) {
            TextMessage tm = (TextMessage) cons.receive(10000);
            Assert.assertNotNull(tm);
            Assert.assertEquals("message" + i, tm.getText());
         }

         Message m = cons.receiveNoWait();
         Assert.assertNull(m);
      } finally {
         if (connSource != null) {
            connSource.close();
         }

         if (connTarget != null) {
            connTarget.close();
         }

         if (bridge != null) {
            bridge.stop();
         }

         removeAllMessages(sourceQueue.getQueueName(), 0);
      }
   }

   @Test
   public void testSelector() throws Exception {
      JMSBridgeImpl bridge = null;

      Connection connSource = null;

      Connection connTarget = null;

      try {
         final int NUM_MESSAGES = 10;

         String selector = "vegetable='radish'";

         bridge = new JMSBridgeImpl(cff0, cff1, sourceQueueFactory, targetQueueFactory, null, null, null, null, selector, 5000, 10, QualityOfServiceMode.AT_MOST_ONCE, 1, -1, null, null, false).setBridgeName("test-bridge");

         bridge.start();

         connSource = cf0.createConnection();

         Session sessSend = connSource.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod = sessSend.createProducer(sourceQueue);

         for (int i = 0; i < NUM_MESSAGES; i++) {
            TextMessage tm = sessSend.createTextMessage("message" + i);

            if (i >= NUM_MESSAGES / 2) {
               tm.setStringProperty("vegetable", "radish");
            } else {
               tm.setStringProperty("vegetable", "cauliflower");
            }

            prod.send(tm);
         }

         connTarget = cf1.createConnection();

         Session sessRec = connTarget.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer cons = sessRec.createConsumer(targetQueue);

         connTarget.start();

         for (int i = NUM_MESSAGES / 2; i < NUM_MESSAGES; i++) {
            TextMessage tm = (TextMessage) cons.receive(10000);

            Assert.assertNotNull(tm);

            Assert.assertEquals("message" + i, tm.getText());
         }

         Message m = cons.receiveNoWait();

         Assert.assertNull(m);

      } finally {
         if (connSource != null) {
            connSource.close();
         }

         if (connTarget != null) {
            connTarget.close();
         }

         if (bridge != null) {
            bridge.stop();
         }

         removeAllMessages(sourceQueue.getQueueName(), 0);
      }
   }

   @Test
   public void testMaskPassword() throws Exception {
      JMSBridgeImpl bridge = null;

      Connection connSource = null;

      Connection connTarget = null;

      DefaultSensitiveStringCodec codec = new DefaultSensitiveStringCodec();
      String mask = (String) codec.encode("guest");

      try {
         final int NUM_MESSAGES = 10;

         bridge = new JMSBridgeImpl(cff0, cff1, sourceQueueFactory, targetQueueFactory, "guest", mask, "guest", mask, null, 5000, 10, QualityOfServiceMode.AT_MOST_ONCE, 1, -1, null, null, false).setBridgeName("test-bridge");

         bridge.setUseMaskedPassword(true);

         bridge.start();

         connSource = cf0.createConnection();

         Session sessSend = connSource.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod = sessSend.createProducer(sourceQueue);

         for (int i = 0; i < NUM_MESSAGES; i++) {
            TextMessage tm = sessSend.createTextMessage("message" + i);

            prod.send(tm);
         }

         connTarget = cf1.createConnection();

         Session sessRec = connTarget.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer cons = sessRec.createConsumer(targetQueue);

         connTarget.start();

         for (int i = 0; i < NUM_MESSAGES; i++) {
            TextMessage tm = (TextMessage) cons.receive(10000);

            Assert.assertNotNull(tm);

            Assert.assertEquals("message" + i, tm.getText());
         }

         Message m = cons.receiveNoWait();

         Assert.assertNull(m);

      } finally {
         if (connSource != null) {
            connSource.close();
         }

         if (connTarget != null) {
            connTarget.close();
         }

         if (bridge != null) {
            bridge.stop();
         }

         removeAllMessages(sourceQueue.getQueueName(), 0);
      }
   }

   @Test
   public void testPasswordCodec() throws Exception {
      JMSBridgeImpl bridge = null;

      Connection connSource = null;

      Connection connTarget = null;

      DefaultSensitiveStringCodec codec = new DefaultSensitiveStringCodec();
      Map<String, String> prop = new HashMap<>();
      prop.put("key", "bridgekey");
      codec.init(prop);

      String mask = (String) codec.encode("guest");

      try {
         final int NUM_MESSAGES = 10;

         bridge = new JMSBridgeImpl(cff0, cff1, sourceQueueFactory, targetQueueFactory, "guest", mask, "guest", mask, null, 5000, 10, QualityOfServiceMode.AT_MOST_ONCE, 1, -1, null, null, false).setBridgeName("test-bridge");

         bridge.setUseMaskedPassword(true);
         bridge.setPasswordCodec(codec.getClass().getName() + ";key=bridgekey");

         bridge.start();

         connSource = cf0.createConnection();

         Session sessSend = connSource.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod = sessSend.createProducer(sourceQueue);

         for (int i = 0; i < NUM_MESSAGES; i++) {
            TextMessage tm = sessSend.createTextMessage("message" + i);

            prod.send(tm);
         }

         connTarget = cf1.createConnection();

         Session sessRec = connTarget.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer cons = sessRec.createConsumer(targetQueue);

         connTarget.start();

         for (int i = 0; i < NUM_MESSAGES; i++) {
            TextMessage tm = (TextMessage) cons.receive(10000);

            Assert.assertNotNull(tm);

            Assert.assertEquals("message" + i, tm.getText());
         }

         Message m = cons.receiveNoWait();

         Assert.assertNull(m);

      } finally {
         if (connSource != null) {
            connSource.close();
         }

         if (connTarget != null) {
            connTarget.close();
         }

         if (bridge != null) {
            bridge.stop();
         }

         removeAllMessages(sourceQueue.getQueueName(), 0);
      }
   }

   @Test
   public void testStartBridgeWithJTATransactionAlreadyRunningLargeMessage() throws Exception {
      internalTestStartBridgeWithJTATransactionAlreadyRunning(true);
   }

   @Test
   public void testStartBridgeWithJTATransactionAlreadyRunningRegularMessage() throws Exception {
      internalTestStartBridgeWithJTATransactionAlreadyRunning(false);
   }

   public void internalTestStartBridgeWithJTATransactionAlreadyRunning(final boolean largeMessage) throws Exception {
      JMSBridgeImpl bridge = null;

      Transaction toResume = null;

      Transaction started = null;

      TransactionManager mgr = newTransactionManager();

      final int NUM_MESSAGES = 10;

      try {

         toResume = mgr.suspend();

         mgr.begin();

         started = mgr.getTransaction();

         bridge = new JMSBridgeImpl(cff0, cff1, sourceTopicFactory, targetQueueFactory, null, null, null, null, null, 5000, 10, QualityOfServiceMode.AT_MOST_ONCE, 1, -1, null, null, false).setBridgeName("test-bridge");
         bridge.start();

         sendMessages(cf0, sourceTopic, 0, NUM_MESSAGES, false, largeMessage);

         checkAllMessageReceivedInOrder(cf1, targetQueue, 0, NUM_MESSAGES, largeMessage);
         Assert.assertEquals(0L, bridge.getAbortedMessageCount());
         Assert.assertEquals("We didn't get the correct number processed messages", NUM_MESSAGES, bridge.getMessageCount());
      } finally {
         if (started != null) {
            try {
               started.rollback();
            } catch (Exception e) {
               log.error("Failed to rollback", e);
            }
         }

         if (toResume != null) {
            try {
               mgr.resume(toResume);
            } catch (Exception e) {
               log.error("Failed to resume", e);
            }
         }
         if (bridge != null) {
            bridge.stop();
         }
      }
   }

   @Test
   public void testAbortedMessages() throws Exception {
      JMSBridgeImpl bridge = null;

      final int NUM_MESSAGES = 20;
      final int MAX_BATCH_SIZE = 1;
      final int RETRY = 2;
      final int LIMIT = 2;
      final int FAILURES = (NUM_MESSAGES - LIMIT) * DEFAULT_MAX_DELIVERY_ATTEMPTS;
      FailingTransactionManager transactionManager = new FailingTransactionManager(newTransactionManager(), LIMIT);
      try {
         bridge = new JMSBridgeImpl(cff0, cff1, sourceQueueFactory, targetQueueFactory, null, null, null, null, null, 5000, RETRY, QualityOfServiceMode.ONCE_AND_ONLY_ONCE, MAX_BATCH_SIZE, -1, null, null, false).setBridgeName("test-bridge");
         bridge.setTransactionManager(transactionManager);
         bridge.start();
         sendMessages(cf0, sourceQueue, 0, NUM_MESSAGES, false, false);
         try (Connection conn = cf1.createConnection()) {
            conn.start();
            Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageConsumer cons = sess.createConsumer(targetQueue);
            // Consume the messages
            for (int i = 0; i <= LIMIT; i++) {
               Message tm = cons.receive(3000);
               if (tm != null) {
                  Assert.assertNotNull("Message " + i + " is null", tm);
                  Assert.assertEquals("message" + i, ((TextMessage) tm).getText());
               }
            }
         }
         Assert.assertEquals("We didn't get the correct number failures", FAILURES, transactionManager.getFailures());
         Assert.assertEquals("We didn't get the correct number of aborted messages", FAILURES, bridge.getAbortedMessageCount());
         Assert.assertEquals("We didn't get the correct number of processed messages", FAILURES + LIMIT, bridge.getMessageCount());
      } finally {
         if (bridge != null) {
            bridge.stop();
         }
      }
      Assert.assertEquals("We didn't get the correct number failures", FAILURES, transactionManager.getFailures());
      Assert.assertEquals("We didn't get the correct number of aborted messages", FAILURES, bridge.getAbortedMessageCount());
      Assert.assertEquals("We didn't get the correct number of processed messages", FAILURES + LIMIT, bridge.getMessageCount());
   }

   @Test
   public void testNonDurableSubscriberLargeMessage() throws Exception {
      internalTestNonDurableSubscriber(true, 1);
   }

   @Test
   public void testNonDurableSubscriberRegularMessage() throws Exception {
      internalTestNonDurableSubscriber(false, 1);
   }

   public void internalTestNonDurableSubscriber(final boolean largeMessage, final int batchSize) throws Exception {
      JMSBridgeImpl bridge = null;

      try {
         final int NUM_MESSAGES = 10;

         bridge = new JMSBridgeImpl(cff0, cff1, sourceTopicFactory, targetQueueFactory, null, null, null, null, null, 5000, 10, QualityOfServiceMode.AT_MOST_ONCE, batchSize, -1, null, null, false).setBridgeName("test-bridge");

         bridge.start();

         sendMessages(cf0, sourceTopic, 0, NUM_MESSAGES, false, largeMessage);

         checkAllMessageReceivedInOrder(cf1, targetQueue, 0, NUM_MESSAGES, largeMessage);
         Assert.assertEquals(0L, bridge.getAbortedMessageCount());
         Assert.assertEquals("We didn't get the correct number processed messages", NUM_MESSAGES, bridge.getMessageCount());
      } finally {
         if (bridge != null) {
            bridge.stop();
         }
      }
   }

   @Test
   public void testDurableSubscriberLargeMessage() throws Exception {
      internalTestDurableSubscriber(true, 1);
   }

   @Test
   public void testDurableSubscriberRegularMessage() throws Exception {
      internalTestDurableSubscriber(false, 1);
   }

   public void internalTestDurableSubscriber(final boolean largeMessage, final int batchSize) throws Exception {
      JMSBridgeImpl bridge = null;

      try {
         final int NUM_MESSAGES = 10;

         bridge = new JMSBridgeImpl(cff0, cff1, sourceTopicFactory, targetQueueFactory, null, null, null, null, null, 5000, 10, QualityOfServiceMode.AT_MOST_ONCE, batchSize, -1, "subTest", "clientid123", false).setBridgeName("test-bridge");

         bridge.start();

         sendMessages(cf0, sourceTopic, 0, NUM_MESSAGES, true, largeMessage);

         checkAllMessageReceivedInOrder(cf1, targetQueue, 0, NUM_MESSAGES, largeMessage);
         Assert.assertEquals(0L, bridge.getAbortedMessageCount());
         Assert.assertEquals("We didn't get the correct number processed messages", NUM_MESSAGES, bridge.getMessageCount());
      } finally {
         if (bridge != null) {
            bridge.stop();
         }

         // Now unsubscribe
         Connection conn = cf0.createConnection();
         conn.setClientID("clientid123");
         Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         sess.unsubscribe("subTest");
         conn.close();
      }
   }

   @Test
   public void testMessageIDInHeaderOn() throws Exception {
      messageIDInHeader(true);
   }

   @Test
   public void testMessageIDInHeaderOff() throws Exception {
      messageIDInHeader(false);
   }

   private void messageIDInHeader(final boolean on) throws Exception {
      JMSBridgeImpl bridge = null;

      Connection connSource = null;

      Connection connTarget = null;

      try {
         final int NUM_MESSAGES = 10;

         bridge = new JMSBridgeImpl(cff0, cff1, sourceQueueFactory, targetQueueFactory, null, null, null, null, null, 5000, 10, QualityOfServiceMode.AT_MOST_ONCE, 1, -1, null, null, on);

         bridge.start();

         connSource = cf0.createConnection();

         connTarget = cf1.createConnection();

         log.trace("Sending " + NUM_MESSAGES + " messages");

         List<String> ids1 = new ArrayList<>();

         Session sessSource = connSource.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod = sessSource.createProducer(sourceQueue);

         for (int i = 0; i < NUM_MESSAGES; i++) {
            TextMessage tm = sessSource.createTextMessage("message" + i);

            // We add some properties to make sure they get passed through ok
            tm.setStringProperty("wib", "uhuh");
            tm.setBooleanProperty("cheese", true);
            tm.setIntProperty("Sausages", 23);
            tm.setByteProperty("bacon", (byte) 12);
            tm.setDoubleProperty("toast", 17261762.12121d);
            tm.setFloatProperty("orange", 1212.1212f);
            tm.setLongProperty("blurg", 817217827L);
            tm.setShortProperty("stst", (short) 26363);

            //Set some JMS headers too

            //And also set a core props
            ((ActiveMQMessage) tm).getCoreMessage().putBytesProperty("bytes", new byte[]{1, 2, 3});

            // We add some JMSX ones too

            tm.setStringProperty("JMSXGroupID", "mygroup543");

            prod.send(tm);

            ids1.add(tm.getJMSMessageID());
         }

         log.trace("Sent the first messages");

         Session sessTarget = connTarget.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer cons = sessTarget.createConsumer(targetQueue);

         connTarget.start();

         List<TextMessage> msgs = new ArrayList<>();

         for (int i = 0; i < NUM_MESSAGES; i++) {
            TextMessage tm = (TextMessage) cons.receive(5000);

            Assert.assertNotNull(tm);

            Assert.assertEquals("message" + i, tm.getText());

            Assert.assertEquals("uhuh", tm.getStringProperty("wib"));
            Assert.assertTrue(tm.getBooleanProperty("cheese"));
            Assert.assertEquals(23, tm.getIntProperty("Sausages"));
            assertEquals((byte) 12, tm.getByteProperty("bacon"));
            assertEquals(17261762.12121d, tm.getDoubleProperty("toast"), 0.000000001);
            assertEquals(1212.1212f, tm.getFloatProperty("orange"), 0.000001);
            assertEquals(817217827L, tm.getLongProperty("blurg"));
            assertEquals((short) 26363, tm.getShortProperty("stst"));

            assertEqualsByteArrays(new byte[]{1, 2, 3}, ((ActiveMQMessage) tm).getCoreMessage().getBytesProperty("bytes"));

            Assert.assertEquals("mygroup543", tm.getStringProperty("JMSXGroupID"));

            if (on) {
               String header = tm.getStringProperty(ActiveMQJMSConstants.AMQ_MESSAGING_BRIDGE_MESSAGE_ID_LIST);

               Assert.assertNotNull(header);

               Assert.assertEquals(ids1.get(i), header);

               msgs.add(tm);
            }
         }

         if (on) {
            // Now we send them again back to the source

            Iterator<TextMessage> iter = msgs.iterator();

            List<String> ids2 = new ArrayList<>();

            while (iter.hasNext()) {
               Message msg = iter.next();

               prod.send(msg);

               ids2.add(msg.getJMSMessageID());
            }

            // And consume them again

            for (int i = 0; i < NUM_MESSAGES; i++) {
               TextMessage tm = (TextMessage) cons.receive(5000);

               Assert.assertNotNull(tm);

               Assert.assertEquals("message" + i, tm.getText());

               Assert.assertEquals("uhuh", tm.getStringProperty("wib"));
               Assert.assertTrue(tm.getBooleanProperty("cheese"));
               Assert.assertEquals(23, tm.getIntProperty("Sausages"));
               assertEquals((byte) 12, tm.getByteProperty("bacon"));
               assertEquals(17261762.12121d, tm.getDoubleProperty("toast"), 0.000001);
               assertEquals(1212.1212f, tm.getFloatProperty("orange"), 0.0000001);
               assertEquals(817217827L, tm.getLongProperty("blurg"));
               assertEquals((short) 26363, tm.getShortProperty("stst"));

               assertEqualsByteArrays(new byte[]{1, 2, 3}, ((ActiveMQMessage) tm).getCoreMessage().getBytesProperty("bytes"));

               Assert.assertEquals("mygroup543", tm.getStringProperty("JMSXGroupID"));

               String header = tm.getStringProperty(ActiveMQJMSConstants.AMQ_MESSAGING_BRIDGE_MESSAGE_ID_LIST);

               Assert.assertNotNull(header);

               Assert.assertEquals(ids1.get(i) + "," + ids2.get(i), header);
            }
         }

      } finally {
         if (bridge != null) {
            bridge.stop();
         }

         if (connSource != null) {
            connSource.close();
         }

         if (connTarget != null) {
            connTarget.close();
         }
      }
   }

   @Test
   public void testPropertiesPreservedPOn() throws Exception {
      propertiesPreserved(true, true);
   }

   @Test
   public void testPropertiesPreservedNPoff() throws Exception {
      propertiesPreserved(false, true);
   }

   @Test
   public void testPropertiesPreservedNPOn() throws Exception {
      propertiesPreserved(false, true);
   }

   @Test
   public void testPropertiesPreservedPoff() throws Exception {
      propertiesPreserved(true, true);
   }

   private void propertiesPreserved(final boolean persistent, final boolean messageIDInHeader) throws Exception {
      JMSBridgeImpl bridge = null;

      Connection connSource = null;

      Connection connTarget = null;

      try {
         final int NUM_MESSAGES = 10;

         bridge = new JMSBridgeImpl(cff0, cff1, sourceQueueFactory, targetQueueFactory, null, null, null, null, null, 5000, 10, QualityOfServiceMode.AT_MOST_ONCE, 1, -1, null, null, messageIDInHeader).setBridgeName("test-bridge");

         bridge.start();

         connSource = cf0.createConnection();

         connTarget = cf1.createConnection();

         log.trace("Sending " + NUM_MESSAGES + " messages");

         Session sessSource = connSource.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Session sessTarget = connTarget.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer cons = sessTarget.createConsumer(targetQueue);

         connTarget.start();

         MessageProducer prod = sessSource.createProducer(sourceQueue);

         prod.setDeliveryMode(persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);

         TextMessage tm = sessSource.createTextMessage("blahmessage");

         prod.setPriority(7);

         prod.setTimeToLive(1 * 60 * 60 * 1000);

         prod.send(tm);

         long expiration = tm.getJMSExpiration();

         Assert.assertEquals(persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT, tm.getJMSDeliveryMode());

         tm = (TextMessage) cons.receive(1000);

         Assert.assertNotNull(tm);

         Assert.assertEquals("blahmessage", tm.getText());

         Assert.assertEquals(persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT, tm.getJMSDeliveryMode());

         Assert.assertEquals(7, tm.getJMSPriority());

         Assert.assertTrue(Math.abs(expiration - tm.getJMSExpiration()) < 100);

         Message m = cons.receive(5000);

         Assert.assertNull(m);

         // Now do one with expiration = 0

         tm = sessSource.createTextMessage("blahmessage2");

         prod.setPriority(7);

         prod.setTimeToLive(0);

         prod.send(tm);

         Assert.assertEquals(persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT, tm.getJMSDeliveryMode());

         tm = (TextMessage) cons.receive(1000);

         Assert.assertNotNull(tm);

         Assert.assertEquals("blahmessage2", tm.getText());

         Assert.assertEquals(persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT, tm.getJMSDeliveryMode());

         Assert.assertEquals(7, tm.getJMSPriority());

         Assert.assertEquals(0, tm.getJMSExpiration());

         m = cons.receive(500);

         Assert.assertNull(m);

         tm = sessSource.createTextMessage("blahmessage3");

         final boolean myBool = false;
         final byte myByte = (byte) 23;
         final double myDouble = 17625765d;
         final float myFloat = 87127.23f;
         final int myInt = 123;
         final long myLong = 81728712;
         final short myShort = (short) 88;
         final String myString = "ojweodewj";
         final String myJMSX = "aardvark";

         tm.setBooleanProperty("mybool", myBool);
         tm.setByteProperty("mybyte", myByte);
         tm.setDoubleProperty("mydouble", myDouble);
         tm.setFloatProperty("myfloat", myFloat);
         tm.setIntProperty("myint", myInt);
         tm.setLongProperty("mylong", myLong);
         tm.setShortProperty("myshort", myShort);
         tm.setStringProperty("mystring", myString);

         tm.setStringProperty("JMSXMyNaughtyJMSXProperty", myJMSX);

         prod.send(tm);

         tm = (TextMessage) cons.receive(5000);

         Assert.assertNotNull(tm);

         Assert.assertEquals("blahmessage3", tm.getText());

         Assert.assertEquals(myBool, tm.getBooleanProperty("mybool"));
         Assert.assertEquals(myByte, tm.getByteProperty("mybyte"));
         Assert.assertEquals(myDouble, tm.getDoubleProperty("mydouble"), 0.000001);
         Assert.assertEquals(myFloat, tm.getFloatProperty("myfloat"), 0.000001);
         Assert.assertEquals(myInt, tm.getIntProperty("myint"));
         Assert.assertEquals(myLong, tm.getLongProperty("mylong"));
         Assert.assertEquals(myShort, tm.getShortProperty("myshort"));
         Assert.assertEquals(myString, tm.getStringProperty("mystring"));
         Assert.assertEquals(myJMSX, tm.getStringProperty("JMSXMyNaughtyJMSXProperty"));

         m = cons.receive(5000);

      } finally {
         if (bridge != null) {
            bridge.stop();
         }

         if (connSource != null) {
            connSource.close();
         }

         if (connTarget != null) {
            connTarget.close();
         }
      }
   }

   @Test
   public void testNoMessageIDInHeader() throws Exception {
      JMSBridgeImpl bridge = null;

      Connection connSource = null;

      Connection connTarget = null;

      try {
         final int NUM_MESSAGES = 10;

         bridge = new JMSBridgeImpl(cff0, cff1, sourceQueueFactory, targetQueueFactory, null, null, null, null, null, 5000, 10, QualityOfServiceMode.AT_MOST_ONCE, 1, -1, null, null, false).setBridgeName("test-bridge");

         bridge.start();

         connSource = cf0.createConnection();

         connTarget = cf1.createConnection();

         log.trace("Sending " + NUM_MESSAGES + " messages");

         Session sessSource = connSource.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod = sessSource.createProducer(sourceQueue);

         for (int i = 0; i < NUM_MESSAGES; i++) {
            TextMessage tm = sessSource.createTextMessage("message" + i);

            // We add some headers to make sure they get passed through ok
            tm.setStringProperty("wib", "uhuh");
            tm.setBooleanProperty("cheese", true);
            tm.setIntProperty("Sausages", 23);

            prod.send(tm);
         }

         JMSBridgeTest.log.trace("Sent the first messages");

         Session sessTarget = connTarget.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageConsumer cons = sessTarget.createConsumer(targetQueue);

         connTarget.start();

         for (int i = 0; i < NUM_MESSAGES; i++) {
            TextMessage tm = (TextMessage) cons.receive(5000);

            Assert.assertNotNull(tm);

            Assert.assertEquals("message" + i, tm.getText());

            Assert.assertEquals("uhuh", tm.getStringProperty("wib"));
            Assert.assertTrue(tm.getBooleanProperty("cheese"));
            Assert.assertEquals(23, tm.getIntProperty("Sausages"));

            String header = tm.getStringProperty(ActiveMQJMSConstants.AMQ_MESSAGING_BRIDGE_MESSAGE_ID_LIST);

            Assert.assertNull(header);
         }
      } finally {
         if (bridge != null) {
            bridge.stop();
         }

         if (connSource != null) {
            connSource.close();
         }

         if (connTarget != null) {
            connTarget.close();
         }
      }
   }

   @Test
   public void testCrashDestStopBridge() throws Exception {
      cff1xa = new ConnectionFactoryFactory() {
         @Override
         public Object createConnectionFactory() throws Exception {
            ActiveMQXAConnectionFactory cf = (ActiveMQXAConnectionFactory) ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.XA_CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY, params1));

            cf.setReconnectAttempts(-1);
            cf.setCallFailoverTimeout(-1);
            cf.setCallTimeout(10000);
            cf.setBlockOnNonDurableSend(true);
            cf.setBlockOnDurableSend(true);
            cf.setCacheLargeMessagesClient(true);

            return cf;
         }

      };

      JMSBridgeImpl bridge = new JMSBridgeImpl(cff0xa, cff1xa, sourceQueueFactory, targetQueueFactory, null, null, null, null, null, 1000, -1, QualityOfServiceMode.ONCE_AND_ONLY_ONCE, 10, 5000, null, null, false).setBridgeName("test-bridge");
      addActiveMQComponent(bridge);
      bridge.setTransactionManager(newTransactionManager());

      bridge.start();

      // Now crash the dest server

      instanceLog.debug("About to crash server");

      server1.stop();

      // Now stop the bridge while the failover is happening

      instanceLog.debug("About to stop the bridge");

      bridge.stop();

      // Shutdown the source server

      server0.stop();
   }


   private void testStress(final QualityOfServiceMode qosMode,
                           final boolean persistent,
                           final int batchSize) throws Exception {
      Connection connSource = null;

      JMSBridgeImpl bridge = null;

      Thread t = null;

      ConnectionFactoryFactory factInUse0 = cff0;
      ConnectionFactoryFactory factInUse1 = cff1;
      if (qosMode.equals(QualityOfServiceMode.ONCE_AND_ONLY_ONCE)) {
         factInUse0 = cff0xa;
         factInUse1 = cff1xa;
         ServiceUtils.setTransactionManager(newTransactionManager());
      }

      try {
         bridge = new JMSBridgeImpl(factInUse0, factInUse1, sourceQueueFactory, targetQueueFactory, null, null, null, null, null, 5000, 10, qosMode, batchSize, -1, null, null, false).setBridgeName("test-bridge");

         bridge.start();

         connSource = cf0.createConnection();

         Session sessSend = connSource.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod = sessSend.createProducer(sourceQueue);

         final int NUM_MESSAGES = 250;

         StressSender sender = new StressSender();
         sender.sess = sessSend;
         sender.prod = prod;
         sender.numMessages = NUM_MESSAGES;
         prod.setDeliveryMode(persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);

         t = new Thread(sender);

         t.start();

         checkAllMessageReceivedInOrder(cf1, targetQueue, 0, NUM_MESSAGES, false);

         t.join();

         if (sender.ex != null) {
            // An error occurred during the send
            throw sender.ex;
         }

      } finally {
         if (t != null) {
            t.join(10000);
         }

         if (connSource != null) {
            try {
               connSource.close();
            } catch (Exception e) {
               log.error("Failed to close connection", e);
            }
         }

         if (bridge != null) {
            bridge.stop();
         }
      }
   }

   private void testStressBatchTime(final QualityOfServiceMode qosMode,
                                    final boolean persistent,
                                    final int maxBatchTime) throws Exception {
      Connection connSource = null;

      JMSBridgeImpl bridge = null;

      Thread t = null;

      ConnectionFactoryFactory factInUse0 = cff0;
      ConnectionFactoryFactory factInUse1 = cff1;
      if (qosMode.equals(QualityOfServiceMode.ONCE_AND_ONLY_ONCE)) {
         factInUse0 = cff0xa;
         factInUse1 = cff1xa;
         ServiceUtils.setTransactionManager(newTransactionManager());
      }

      try {
         bridge = new JMSBridgeImpl(factInUse0, factInUse1, sourceQueueFactory, targetQueueFactory, null, null, null, null, null, 5000, 10, qosMode, 2, maxBatchTime, null, null, false).setBridgeName("test-bridge");

         bridge.start();

         connSource = cf0.createConnection();

         Session sessSend = connSource.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod = sessSend.createProducer(sourceQueue);

         final int NUM_MESSAGES = 500;

         StressSender sender = new StressSender();
         sender.sess = sessSend;
         sender.prod = prod;
         sender.numMessages = NUM_MESSAGES;
         prod.setDeliveryMode(persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);

         t = new Thread(sender);

         t.start();

         checkAllMessageReceivedInOrder(cf1, targetQueue, 0, NUM_MESSAGES, false);

         t.join();

         if (sender.ex != null) {
            // An error occurred during the send
            throw sender.ex;
         }

      } finally {
         if (t != null) {
            t.join(10000);
         }

         if (connSource != null) {
            try {
               connSource.close();
            } catch (Exception e) {
               log.error("Failed to close connection", e);
            }
         }

         if (bridge != null) {
            bridge.stop();
         }
      }
   }

   // Both source and destination on same rm
   private void testStressSameServer(final QualityOfServiceMode qosMode,
                                     final boolean persistent,
                                     final int batchSize) throws Exception {
      Connection connSource = null;

      JMSBridgeImpl bridge = null;

      Thread t = null;

      ConnectionFactoryFactory factInUse0 = cff0;
      if (qosMode.equals(QualityOfServiceMode.ONCE_AND_ONLY_ONCE)) {
         factInUse0 = cff0xa;
      }

      try {
         bridge = new JMSBridgeImpl(factInUse0, factInUse0, sourceQueueFactory, localTargetQueueFactory, null, null, null, null, null, 5000, 10, qosMode, batchSize, -1, null, null, false).setBridgeName("test-bridge");

         bridge.start();

         connSource = cf0.createConnection();

         Session sessSend = connSource.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod = sessSend.createProducer(sourceQueue);

         final int NUM_MESSAGES = 200;

         StressSender sender = new StressSender();
         sender.sess = sessSend;
         sender.prod = prod;
         sender.numMessages = NUM_MESSAGES;
         prod.setDeliveryMode(persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);

         t = new Thread(sender);

         t.start();

         checkAllMessageReceivedInOrder(cf0, localTargetQueue, 0, NUM_MESSAGES, false);

         t.join();

         if (sender.ex != null) {
            // An error occurred during the send
            throw sender.ex;
         }

      } finally {
         if (t != null) {
            t.join(10000);
         }

         if (connSource != null) {
            try {
               connSource.close();
            } catch (Exception e) {
               log.error("Failed to close connection", e);
            }
         }

         if (bridge != null) {
            bridge.stop();
         }
      }
   }

   private void testNoMaxBatchTime(final QualityOfServiceMode qosMode, final boolean persistent) throws Exception {
      JMSBridgeImpl bridge = null;

      ConnectionFactoryFactory factInUse0 = cff0;
      ConnectionFactoryFactory factInUse1 = cff1;
      if (qosMode.equals(QualityOfServiceMode.ONCE_AND_ONLY_ONCE)) {
         factInUse0 = cff0xa;
         factInUse1 = cff1xa;
         ServiceUtils.setTransactionManager(newTransactionManager());
      }
      try {
         final int NUM_MESSAGES = 10;

         bridge = new JMSBridgeImpl(factInUse0, factInUse1, sourceQueueFactory, targetQueueFactory, null, null, null, null, null, 5000, 10, qosMode, NUM_MESSAGES, -1, null, null, false).setBridgeName("test-bridge");

         bridge.start();

         // Send half the messages

         sendMessages(cf0, sourceQueue, 0, NUM_MESSAGES / 2, persistent, false);

         // Verify none are received

         checkEmpty(targetQueue, 1);

         // Send the other half

         sendMessages(cf0, sourceQueue, NUM_MESSAGES / 2, NUM_MESSAGES / 2, persistent, false);

         // This should now be receivable

         checkAllMessageReceivedInOrder(cf1, targetQueue, 0, NUM_MESSAGES, false);

         // Send another batch with one more than batch size

         sendMessages(cf0, sourceQueue, 0, NUM_MESSAGES + 1, persistent, false);

         // Make sure only batch size are received

         checkAllMessageReceivedInOrder(cf1, targetQueue, 0, NUM_MESSAGES, false);

         // Final batch

         sendMessages(cf0, sourceQueue, 0, NUM_MESSAGES - 1, persistent, false);

         checkAllMessageReceivedInOrder(cf1, targetQueue, NUM_MESSAGES, 1, false);

         checkAllMessageReceivedInOrder(cf1, targetQueue, 0, NUM_MESSAGES - 1, false);
      } finally {
         if (bridge != null) {
            instanceLog.debug("Stopping bridge");
            bridge.stop();
         }
      }
   }

   private void testNoMaxBatchTimeSameServer(final QualityOfServiceMode qosMode,
                                             final boolean persistent) throws Exception {
      JMSBridgeImpl bridge = null;

      ConnectionFactoryFactory factInUse0 = cff0;
      if (qosMode.equals(QualityOfServiceMode.ONCE_AND_ONLY_ONCE)) {
         factInUse0 = cff0xa;
         ServiceUtils.setTransactionManager(newTransactionManager());
      }

      try {
         final int NUM_MESSAGES = 10;

         bridge = new JMSBridgeImpl(factInUse0, factInUse0, sourceQueueFactory, localTargetQueueFactory, null, null, null, null, null, 500, 10, qosMode, NUM_MESSAGES, -1, null, null, false).setBridgeName("test-bridge");

         bridge.start();

         sendMessages(cf0, sourceQueue, 0, NUM_MESSAGES / 2, persistent, false);

         checkEmpty(targetQueue, 1);

         // Send the other half

         sendMessages(cf0, sourceQueue, NUM_MESSAGES / 2, NUM_MESSAGES / 2, persistent, false);

         // This should now be receivable

         checkAllMessageReceivedInOrder(cf0, localTargetQueue, 0, NUM_MESSAGES, false);

         checkEmpty(localTargetQueue, 0);

         checkEmpty(sourceQueue, 0);

         // Send another batch with one more than batch size

         sendMessages(cf0, sourceQueue, 0, NUM_MESSAGES + 1, persistent, false);

         // Make sure only batch size are received

         checkAllMessageReceivedInOrder(cf0, localTargetQueue, 0, NUM_MESSAGES, false);

         // Final batch

         sendMessages(cf0, sourceQueue, 0, NUM_MESSAGES - 1, persistent, false);

         checkAllMessageReceivedInOrder(cf0, localTargetQueue, NUM_MESSAGES, 1, false);

         checkAllMessageReceivedInOrder(cf0, localTargetQueue, 0, NUM_MESSAGES - 1, false);
      } finally {
         if (bridge != null) {
            bridge.stop();
         }
      }
   }

   private void testMaxBatchTime(final QualityOfServiceMode qosMode, final boolean persistent) throws Exception {
      JMSBridgeImpl bridge = null;

      ConnectionFactoryFactory factInUse0 = cff0;
      ConnectionFactoryFactory factInUse1 = cff1;
      if (qosMode.equals(QualityOfServiceMode.ONCE_AND_ONLY_ONCE)) {
         factInUse0 = cff0xa;
         factInUse1 = cff1xa;
         ServiceUtils.setTransactionManager(newTransactionManager());
      }

      try {
         final long MAX_BATCH_TIME = 300;

         final int MAX_BATCH_SIZE = 100000; // something big so it won't reach it

         bridge = new JMSBridgeImpl(factInUse0, factInUse1, sourceQueueFactory, targetQueueFactory, null, null, null, null, null, 3000, 10, qosMode, MAX_BATCH_SIZE, MAX_BATCH_TIME, null, null, false).setBridgeName("test-bridge");

         bridge.start();

         final int NUM_MESSAGES = 10;

         // Send some message

         sendMessages(cf0, sourceQueue, 0, NUM_MESSAGES, persistent, false);

         // Verify none are received

         checkEmpty(targetQueue, 1);

         // Messages should now be receivable

         checkAllMessageReceivedInOrder(cf1, targetQueue, 0, NUM_MESSAGES, false);
      } finally {
         if (bridge != null) {
            bridge.stop();
         }
      }
   }

   private void testMaxBatchTimeSameServer(final QualityOfServiceMode qosMode,
                                           final boolean persistent) throws Exception {
      JMSBridgeImpl bridge = null;

      ConnectionFactoryFactory factInUse0 = cff0;
      if (qosMode.equals(QualityOfServiceMode.ONCE_AND_ONLY_ONCE)) {
         factInUse0 = cff0xa;
         ServiceUtils.setTransactionManager(newTransactionManager());
      }

      try {
         final long MAX_BATCH_TIME = 300;

         final int MAX_BATCH_SIZE = 100000; // something big so it won't reach it

         bridge = new JMSBridgeImpl(factInUse0, factInUse0, sourceQueueFactory, localTargetQueueFactory, null, null, null, null, null, 3000, 10, qosMode, MAX_BATCH_SIZE, MAX_BATCH_TIME, null, null, false);

         bridge.start();

         final int NUM_MESSAGES = 10;

         // Send some message

         // Send some message

         sendMessages(cf0, sourceQueue, 0, NUM_MESSAGES, persistent, false);

         // Verify none are received

         checkEmpty(localTargetQueue, 0);

         // Messages should now be receivable

         checkAllMessageReceivedInOrder(cf0, localTargetQueue, 0, NUM_MESSAGES, false);
      } finally {
         if (bridge != null) {
            bridge.stop();
         }
      }
   }

   @Test
   public void testSetTMClass() throws Exception {
      TransactionManagerLocatorImpl.setTransactionManager(new DummyTransactionManager());

      JMSBridgeImpl bridge = null;
      try {
         bridge = new JMSBridgeImpl(cff0, cff0, sourceQueueFactory, localTargetQueueFactory, null, null, null, null, null, 3000, 10, QualityOfServiceMode.ONCE_AND_ONLY_ONCE, 10000, 3000, null, null, false).setBridgeName("test-bridge");
         bridge.start();
      } finally {
         if (bridge != null) {
            bridge.stop();
         }
      }
   }

   @Test
   public void testMBeanServer() throws Exception {

      MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
      ObjectName objectName = new ObjectName("example.jmsbridge:service=JMSBridge");

      JMSBridgeImpl bridge = new JMSBridgeImpl(cff0, cff0, sourceQueueFactory, localTargetQueueFactory, null, null, null, null, null, 5000, 10, QualityOfServiceMode.AT_MOST_ONCE, 1, -1, null, null, false, mbeanServer, objectName.getCanonicalName()).setBridgeName("test-bridge");

      Assert.assertTrue(mbeanServer.isRegistered(objectName));

      bridge.destroy();

      Assert.assertFalse(mbeanServer.isRegistered(objectName));
   }

   @Test
   public void testDuplicateMessagesWhenBridgeStops() throws Exception {
      final int NUM_MESSAGES = 500;

      JMSBridgeImpl bridge = new JMSBridgeImpl(cff0xa, cff1xa, sourceQueueFactory, targetQueueFactory, null, null,
         null, null, null, 1000, 10,
         QualityOfServiceMode.ONCE_AND_ONLY_ONCE, 10, 100, null, "ClientId123",
         true)
         .setBridgeName("test-bridge");
      bridge.setTransactionManager(getNewTm());
      createQueue(targetQueue.getQueueName(), 1);

      final List<TextMessage> sentMessages = new ArrayList<>();
      final List<TextMessage> receivedMessages = new ArrayList<>();

      instanceLog.debug("Starting bridge " + bridge);
      bridge.start();
      waitForComponent(bridge, 15);

      Thread producerThread = new Thread(() -> {
         Connection conn = null;
         Session session = null;
         int counter = 0;
         try {
            conn = cf0.createConnection();
            session = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
            MessageProducer producer = session.createProducer(sourceQueue);
            TextMessage msg = null;

            while (counter < NUM_MESSAGES) {
               msg = session.createTextMessage("message" + counter);
               msg.setIntProperty("count", counter);
               producer.send(msg);
               sentMessages.add(msg);
               instanceLog.debug("Sent message with property counter: " + counter + ", messageId:" + msg.getJMSMessageID()
                  + ((msg.getStringProperty("_AMQ_DUPL_ID") != null) ? ", _AMQ_DUPL_ID=" + msg.getStringProperty("_AMQ_DUPL_ID") : ""));
               counter++;
               Thread.sleep(200);
            }

            producer.close();
         } catch (InterruptedException | JMSException e) {
            log.error("Error while producing messages: ", e);
         } finally {
            try {
               if (session != null) {
                  session.close();
               }

               if (conn != null) {
                  conn.close();
               }
            } catch (JMSException e) {
               log.error("Error cleaning up the producer thread! ", e);
            }
         }
      });

      Thread consumerThread = new Thread(() -> {
         Connection conn = null;
         Session session = null;
         try {
            conn = cf1.createConnection();
            conn.start();

            session = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);

            MessageConsumer consumer = session.createConsumer(targetQueue);
            TextMessage msg = null;

            boolean running = true;
            while (running) {
               msg = (TextMessage) consumer.receive(5000);
               if (msg != null) {
                  msg.acknowledge();
                  receivedMessages.add(msg);
                  instanceLog.debug("Received message with messageId: " + msg.getJMSMessageID() +
                     " and property counter " + msg.getIntProperty("count"));
               } else {
                  running = false;
               }
            }

         } catch (JMSException e) {
            log.error("Error while consuming messages: ", e);
         } finally {
            try {
               if (session != null) {
                  session.close();
               }

               if (conn != null) {
                  conn.close();
               }
            } catch (JMSException e) {
               log.error("Error cleaning up the consumer thread! ", e);
            }
         }
      });

      instanceLog.debug("Starting producer thread...");
      producerThread.start();

      Assert.assertTrue(waitForMessages(server1, targetQueue.getQueueName(), NUM_MESSAGES / 100, 250000));

      instanceLog.debug("Stopping bridge " + bridge);
      bridge.stop();
      Thread.sleep(5000);

      instanceLog.debug("Starting bridge " + bridge + " again");
      bridge.start();
      waitForComponent(bridge, 15);

      Assert.assertTrue(waitForMessages(server1, targetQueue.getQueueName(), NUM_MESSAGES, 300000));



      instanceLog.debug("Starting consumer thread...");
      consumerThread.start();

      instanceLog.debug("Waiting for the consumer thread to die...");
      consumerThread.join();

      instanceLog.debug("Waiting for the producer thread to die...");
      producerThread.join();

      bridge.stop();

      server1.stop();
      server0.stop();

      Assert.assertEquals("Number of sent messages is different from received messages", sentMessages.size(), receivedMessages.size());
   }

   public TransactionManager getNewTm() {
      return newTransactionManager();
   }

   private static long countMessagesInQueue(ActiveMQServer server, String queueName) {
      QueueControl queueControl = (QueueControl) server.getManagementService().getResource(ResourceNames.QUEUE + queueName);
      Assert.assertNotNull(queueControl);
      long count = -1;
      int numberOfTries = 0;
      int maxNumberOfTries = 10;
      while (count == -1 && numberOfTries < maxNumberOfTries) {
         try {
            numberOfTries++;
            count = queueControl.countMessages();
            break;
         } catch (Exception ex) {
            if (numberOfTries > maxNumberOfTries - 1) {
               throw new RuntimeException("countMessagesInQueue() failed for queue:" + queueName
                  + " and server: " + server + ". Number of tries: " + numberOfTries, ex);
            }
            try {
               Thread.sleep(2000);
            } catch (InterruptedException e) {
               log.error(e.getMessage(), e);
            }
         }
      }
      log.debug("Number of messages in queue " + queueName + " on server: " + server + " is: " + count);
      return count;
   }

   private static boolean waitForMessages(ActiveMQServer server, String queueName, long numberOfMessages, long timeout) throws Exception {

      long startTime = System.currentTimeMillis();

      long count = 0;
      while ((count = countMessagesInQueue(server, queueName)) < numberOfMessages) {
         log.debug("Total number of messages in queue: " + queueName + " on server " + server + " is " + count);
         Thread.sleep(5000);
         if (System.currentTimeMillis() - startTime > timeout) {
            log.warn(numberOfMessages + " not on server " + server + " in timeout " + timeout + "ms.");
            return false;
         }
      }
      return true;

   }

   private static class StressSender implements Runnable {

      int numMessages;

      Session sess;

      MessageProducer prod;

      Exception ex;

      @Override
      public void run() {
         try {
            for (int i = 0; i < numMessages; i++) {
               TextMessage tm = sess.createTextMessage("message" + i);

               prod.send(tm);

               JMSBridgeTest.log.trace("Sent message " + i);
            }
         } catch (Exception e) {
            JMSBridgeTest.log.error("Failed to send", e);
            ex = e;
         }
      }

   }
}

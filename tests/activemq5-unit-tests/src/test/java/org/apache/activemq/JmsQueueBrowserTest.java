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
package org.apache.activemq;

import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.Enumeration;

import junit.framework.Test;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.region.policy.PolicyEntry;
import org.apache.activemq.broker.region.policy.PolicyMap;
import org.apache.activemq.command.ActiveMQQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JmsQueueBrowserTest extends JmsTestSupport {

   private static final Logger LOG = LoggerFactory.getLogger(ActiveMQXAConnectionFactoryTest.class);
   public boolean isUseCache = false;

   public static Test suite() throws Exception {
      return suite(JmsQueueBrowserTest.class);
   }

   /**
    * Tests the queue browser. Browses the messages then the consumer tries to receive them. The messages should still
    * be in the queue even when it was browsed.
    */
   public void testReceiveBrowseReceive() throws Exception {
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      ActiveMQQueue destination = new ActiveMQQueue("TEST");
      MessageProducer producer = session.createProducer(destination);
      MessageConsumer consumer = session.createConsumer(destination);
      connection.start();

      Message[] outbound = new Message[]{session.createTextMessage("First Message"), session.createTextMessage("Second Message"), session.createTextMessage("Third Message")};

      // lets consume any outstanding messages from previous test runs
      while (consumer.receive(1000) != null) {
      }

      producer.send(outbound[0]);
      producer.send(outbound[1]);
      producer.send(outbound[2]);

      // Get the first.
      assertEquals(outbound[0], consumer.receive(1000));
      consumer.close();

      QueueBrowser browser = session.createBrowser(destination);
      Enumeration<?> enumeration = browser.getEnumeration();

      // browse the second
      assertTrue("should have received the second message", enumeration.hasMoreElements());
      assertEquals(outbound[1], enumeration.nextElement());

      // browse the third.
      assertTrue("Should have received the third message", enumeration.hasMoreElements());
      assertEquals(outbound[2], enumeration.nextElement());

      // There should be no more.
      boolean tooMany = false;
      while (enumeration.hasMoreElements()) {
         LOG.info("Got extra message: " + ((TextMessage) enumeration.nextElement()).getText());
         tooMany = true;
      }
      assertFalse(tooMany);
      browser.close();

      // Re-open the consumer.
      consumer = session.createConsumer(destination);
      // Receive the second.
      assertEquals(outbound[1], consumer.receive(1000));
      // Receive the third.
      assertEquals(outbound[2], consumer.receive(1000));
      consumer.close();
   }

   public void initCombosForTestBatchSendBrowseReceive() {
      addCombinationValues("isUseCache", new Boolean[]{Boolean.TRUE, Boolean.FALSE});
   }


   public void testLargeNumberOfMessages() throws Exception {
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      ActiveMQQueue destination = new ActiveMQQueue("TEST");
      connection.start();

      MessageProducer producer = session.createProducer(destination);

      int numberOfMessages = 4096;

      for (int i = 0; i < numberOfMessages; i++) {
         producer.send(session.createTextMessage("Message: " + i));
      }

      QueueBrowser browser = session.createBrowser(destination);
      Enumeration<?> enumeration = browser.getEnumeration();

      assertTrue(enumeration.hasMoreElements());

      int numberBrowsed = 0;

      while (enumeration.hasMoreElements()) {
         Message browsed = (Message) enumeration.nextElement();

         if (LOG.isDebugEnabled()) {
            LOG.debug("Browsed Message [{}]", browsed.getJMSMessageID());
         }

         numberBrowsed++;
      }

      System.out.println("Number browsed:  " + numberBrowsed);
      assertEquals(numberOfMessages, numberBrowsed);
      browser.close();
      producer.close();
   }

   @Override
   protected BrokerService createBroker() throws Exception {
      BrokerService brokerService = super.createBroker();
      PolicyMap policyMap = new PolicyMap();
      PolicyEntry policyEntry = new PolicyEntry();
      policyEntry.setUseCache(isUseCache);
      policyEntry.setMaxBrowsePageSize(4096);
      policyMap.setDefaultEntry(policyEntry);
      brokerService.setDestinationPolicy(policyMap);
      return brokerService;
   }
}

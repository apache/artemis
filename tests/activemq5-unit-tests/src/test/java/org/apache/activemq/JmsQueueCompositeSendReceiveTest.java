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

import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Topic;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorImpl;
import org.apache.activemq.artemiswrapper.ArtemisBrokerHelper;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.test.JmsTopicSendReceiveTest;
import org.junit.Ignore;

@Ignore
public class JmsQueueCompositeSendReceiveTest extends JmsTopicSendReceiveTest {

   private static final org.apache.commons.logging.Log LOG = org.apache.commons.logging.LogFactory.getLog(JmsQueueCompositeSendReceiveTest.class);

   /**
    * Sets a test to have a queue destination and non-persistent delivery mode.
    */
   @Override
   protected void setUp() throws Exception {
      topic = false;
      deliveryMode = DeliveryMode.NON_PERSISTENT;
      super.setUp();
      ActiveMQDestination dest1 = (ActiveMQDestination) session.createQueue("FOO.BAR.HUMBUG2");
      ActiveMQDestination dest2 = (ActiveMQDestination) session.createQueue("TEST");
      ArtemisBrokerHelper.makeSureDestinationExists(dest1);
      ArtemisBrokerHelper.makeSureDestinationExists(dest2);
   }

   @Override
   protected String getConsumerSubject() {
      return "FOO.BAR.HUMBUG";
   }

   @Override
   protected String getProducerSubject() {
      return "FOO.BAR.HUMBUG,FOO.BAR.HUMBUG2";
   }

   @Override
   public void testSendReceive() throws Exception {
      super.testSendReceive();
      messages.clear();
      Destination consumerDestination = consumeSession.createQueue("FOO.BAR.HUMBUG2");
      LOG.info("Created  consumer destination: " + consumerDestination + " of type: " + consumerDestination.getClass());
      MessageConsumer consumer = null;
      if (durable) {
         LOG.info("Creating durable consumer");
         consumer = consumeSession.createDurableSubscriber((Topic) consumerDestination, getName());
      } else {
         consumer = consumeSession.createConsumer(consumerDestination);
      }
      consumer.setMessageListener(this);

      assertMessagesAreReceived();
      LOG.info("" + data.length + " messages(s) received, closing down connections");
   }

   public void testDuplicate() throws Exception {
      ActiveMQDestination queue = (ActiveMQDestination) session.createQueue("TEST,TEST");
      for (int i = 0; i < data.length; i++) {
         Message message = createMessage(i);
         configureMessage(message);
         if (verbose) {
            LOG.info("About to send a message: " + message + " with text: " + data[i]);
         }
         producer.send(queue, message);
      }

      Thread.sleep(200); // wait for messages to be queue;

      try (ServerLocator locator = ServerLocatorImpl.newLocator("tcp://localhost:61616");
           ClientSessionFactory factory = locator.createSessionFactory();
           ClientSession session = factory.createSession()) {
         ClientSession.QueueQuery query = session.queueQuery(SimpleString.of("TEST"));
         assertNotNull(query);
         assertEquals(data.length, query.getMessageCount());
      }
   }
}

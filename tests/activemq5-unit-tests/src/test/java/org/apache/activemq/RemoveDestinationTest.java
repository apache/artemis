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

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import java.net.URI;
import java.util.Iterator;
import java.util.Set;

import org.apache.activemq.advisory.DestinationSource;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.broker.BrokerFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.artemiswrapper.ArtemisBrokerWrapper;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ActiveMQTopic;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@Ignore
public class RemoveDestinationTest {

   private static final String TCP_BROKER_URL = "tcp://localhost:61616?create=false";
   private static final String BROKER_URL = "broker:tcp://localhost:61616?broker.persistent=false&broker.useJmx=true";

   BrokerService broker;

   @Before
   public void setUp() throws Exception {
      BrokerService.disableWrapper = true;
      broker = BrokerFactory.createBroker(new URI(BROKER_URL));
      broker.start();
      broker.waitUntilStarted();
   }

   @After
   public void tearDown() throws Exception {
      BrokerService.disableWrapper = false;
      broker.stop();
      broker.waitUntilStopped();
      broker = null;
   }

   private Connection createConnection(final boolean start) throws JMSException {
      ConnectionFactory cf = new ActiveMQConnectionFactory(TCP_BROKER_URL);
      Connection conn = cf.createConnection();
      if (start) {
         conn.start();
      }
      return conn;
   }

   @Test
   public void testRemoveDestinationWithoutSubscriber() throws Exception {

      ActiveMQConnection amqConnection = (ActiveMQConnection) createConnection(true);
      DestinationSource destinationSource = amqConnection.getDestinationSource();
      Session session = amqConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Topic topic = session.createTopic("TEST.FOO");
      MessageProducer producer = session.createProducer(topic);
      MessageConsumer consumer = session.createConsumer(topic);

      TextMessage msg = session.createTextMessage("Hellow World");
      producer.send(msg);
      assertNotNull(consumer.receive(5000));
      Thread.sleep(1000);

      ActiveMQTopic amqTopic = (ActiveMQTopic) topic;
      assertTrue(destinationSource.getTopics().contains(amqTopic));

      consumer.close();
      producer.close();
      session.close();

      Thread.sleep(3000);
      amqConnection.destroyDestination((ActiveMQDestination) topic);
      Thread.sleep(3000);
      assertFalse(destinationSource.getTopics().contains(amqTopic));
   }

   @Test
   public void testRemoveDestinationWithSubscriber() throws Exception {
      ActiveMQConnection amqConnection = (ActiveMQConnection) createConnection(true);
      DestinationSource destinationSource = amqConnection.getDestinationSource();

      Session session = amqConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Topic topic = session.createTopic("TEST.FOO");
      MessageProducer producer = session.createProducer(topic);
      MessageConsumer consumer = session.createConsumer(topic);

      TextMessage msg = session.createTextMessage("Hellow World");
      producer.send(msg);
      assertNotNull(consumer.receive(5000));
      Thread.sleep(1000);

      ActiveMQTopic amqTopic = (ActiveMQTopic) topic;

      assertTrue(destinationPresentInAdminView(amqTopic));
      assertTrue(destinationSource.getTopics().contains(amqTopic));

      // This line generates a broker error since the consumer is still active.
      try {
         amqConnection.destroyDestination((ActiveMQDestination) topic);
         fail("expect exception on destroy if comsumer present");
      } catch (JMSException expected) {
         assertTrue(expected.getMessage().indexOf(amqTopic.getTopicName()) != -1);
      }

      Thread.sleep(3000);

      assertTrue(destinationSource.getTopics().contains(amqTopic));
      assertTrue(destinationPresentInAdminView(amqTopic));

      consumer.close();
      producer.close();
      session.close();

      Thread.sleep(3000);

      // The destination will not be removed with this call, but if you remove
      // the call above that generates the error it will.
      amqConnection.destroyDestination(amqTopic);
      Thread.sleep(3000);
      assertFalse(destinationSource.getTopics().contains(amqTopic));
      assertFalse(destinationPresentInAdminView(amqTopic));
   }

   private boolean destinationPresentInAdminView(ActiveMQTopic amqTopic) throws Exception {
      boolean found = false;
      ArtemisBrokerWrapper wrapper = (ArtemisBrokerWrapper) broker.getBroker();
      PostOffice po = wrapper.getServer().getPostOffice();
      Set<SimpleString> addressSet = po.getAddresses();
      Iterator<SimpleString> iter = addressSet.iterator();
      String addressToFind = amqTopic.getPhysicalName();
      while (iter.hasNext()) {
         if (addressToFind.equals(iter.next().toString())) {
            found = true;
            break;
         }
      }
      return found;
   }
}

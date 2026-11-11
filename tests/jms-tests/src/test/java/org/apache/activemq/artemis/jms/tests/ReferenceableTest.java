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
package org.apache.activemq.artemis.jms.tests;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.spi.ObjectFactory;
import java.io.Serializable;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQQueue;
import org.apache.activemq.artemis.jms.client.ActiveMQTopic;
import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.Test;

/**
 * A ReferenceableTest.
 * <p>
 * All administered objects should be referenceable and serializable as per spec 4.2
 */
public class ReferenceableTest extends JMSTestCase {



   @SuppressWarnings("cast")
   @Test
   public void testSerializable() throws Exception {
      ProxyAssertSupport.assertTrue(cf instanceof Serializable);

      ProxyAssertSupport.assertTrue(queue1 instanceof Serializable);

      ProxyAssertSupport.assertTrue(ActiveMQServerTestCase.topic1 instanceof Serializable);
   }

   @SuppressWarnings("cast")
   @Test
   public void testReferenceable() throws Exception {
      ProxyAssertSupport.assertTrue(cf instanceof Referenceable);

      ProxyAssertSupport.assertTrue(queue1 instanceof Referenceable);

      ProxyAssertSupport.assertTrue(ActiveMQServerTestCase.topic1 instanceof Referenceable);
   }

   @Test
   public void testReferenceCF() throws Exception {
      Reference cfRef = ((Referenceable) cf).getReference();

      String factoryName = cfRef.getFactoryClassName();

      ObjectFactory factory = (ObjectFactory) Class.forName(factoryName).getDeclaredConstructor().newInstance();

      Object instance = factory.getObjectInstance(cfRef, null, null, null);

      ProxyAssertSupport.assertTrue(instance instanceof ActiveMQConnectionFactory);

      ActiveMQJMSConnectionFactory cf2 = (ActiveMQJMSConnectionFactory) instance;

      simpleSendReceive(cf2, queue1);
   }

   @Test
   public void testReferenceQueue() throws Exception {
      Reference queueRef = ((Referenceable) queue1).getReference();

      String factoryName = queueRef.getFactoryClassName();

      ObjectFactory factory = (ObjectFactory) Class.forName(factoryName).getDeclaredConstructor().newInstance();

      Object instance = factory.getObjectInstance(queueRef, null, null, null);

      ProxyAssertSupport.assertTrue(instance instanceof ActiveMQDestination);

      ActiveMQQueue queue2 = (ActiveMQQueue) instance;

      ProxyAssertSupport.assertEquals(queue1.getQueueName(), queue2.getQueueName());

      simpleSendReceive(cf, queue2);
   }

   @Test
   public void testReferenceTopic() throws Exception {
      Reference topicRef = ((Referenceable) ActiveMQServerTestCase.topic1).getReference();

      String factoryName = topicRef.getFactoryClassName();

      ObjectFactory factory = (ObjectFactory) Class.forName(factoryName).getDeclaredConstructor().newInstance();

      Object instance = factory.getObjectInstance(topicRef, null, null, null);

      ProxyAssertSupport.assertTrue(instance instanceof ActiveMQDestination);

      ActiveMQTopic topic2 = (ActiveMQTopic) instance;

      ProxyAssertSupport.assertEquals(ActiveMQServerTestCase.topic1.getTopicName(), topic2.getTopicName());

      simpleSendReceive(cf, topic2);
   }

   protected void simpleSendReceive(final ConnectionFactory cf1, final Destination dest) throws Exception {
      Connection conn = createConnection(cf1);

      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageProducer prod = sess.createProducer(dest);

      MessageConsumer cons = sess.createConsumer(dest);

      conn.start();

      TextMessage tm = sess.createTextMessage("ref test");

      prod.send(tm);

      tm = (TextMessage) cons.receive(1000);

      ProxyAssertSupport.assertNotNull(tm);

      ProxyAssertSupport.assertEquals("ref test", tm.getText());
   }

}

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
package org.apache.activemq.artemis.jms.tests.message;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.activemq.artemis.jms.tests.ActiveMQServerTestCase;
import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public abstract class MessageTestBase extends ActiveMQServerTestCase {


   protected Message message;

   protected Connection conn;

   protected Session session;

   protected MessageProducer queueProd;

   protected MessageConsumer queueCons;


   @Override
   @Before
   public void setUp() throws Exception {
      super.setUp();

      conn = getConnectionFactory().createConnection();
      session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      queueProd = session.createProducer(queue1);
      queueCons = session.createConsumer(queue1);

      conn.start();
   }

   @Override
   @After
   public void tearDown() throws Exception {
      if (conn != null)
         conn.close();
   }

   @Test
   public void testNonPersistentSend() throws Exception {
      prepareMessage(message);

      queueProd.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
      queueProd.send(message);

      log.debug("Message sent");

      Message r = queueCons.receive(2000);
      ProxyAssertSupport.assertNotNull(r);

      log.debug("Message received");

      ProxyAssertSupport.assertEquals(DeliveryMode.NON_PERSISTENT, r.getJMSDeliveryMode());

      assertEquivalent(r, DeliveryMode.NON_PERSISTENT);
   }

   @Test
   public void testPersistentSend() throws Exception {
      prepareMessage(message);

      queueProd.setDeliveryMode(DeliveryMode.PERSISTENT);

      queueProd.send(message);

      Message r = queueCons.receive(1000);
      ProxyAssertSupport.assertNotNull(r);

      ProxyAssertSupport.assertEquals(DeliveryMode.PERSISTENT, r.getJMSDeliveryMode());

      assertEquivalent(r, DeliveryMode.PERSISTENT);
   }

   @Test
   public void testRedelivery() throws Exception {
      prepareMessage(message);

      session.close();
      session = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);

      queueProd = session.createProducer(queue1);
      queueProd.setDeliveryMode(DeliveryMode.PERSISTENT);
      queueCons = session.createConsumer(queue1);

      queueProd.send(message);

      Message r = queueCons.receive(500);

      assertEquivalent(r, DeliveryMode.PERSISTENT);

      queueCons.close();
      session.close();

      session = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
      queueCons = session.createConsumer(queue1);
      r = queueCons.receive(1000);

      assertEquivalent(r, DeliveryMode.PERSISTENT, true);

      r.acknowledge();

      ProxyAssertSupport.assertNull(queueCons.receiveNoWait());

   }

   @Test
   public void testSendMoreThanOnce() throws Exception {
      prepareMessage(message);

      session.close();
      session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      queueProd = session.createProducer(queue1);
      queueProd.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
      queueCons = session.createConsumer(queue1);

      queueProd.send(message);
      queueProd.send(message);
      queueProd.send(message);

      Message r = queueCons.receive(500);

      assertEquivalent(r, DeliveryMode.NON_PERSISTENT);

      r = queueCons.receive(500);

      assertEquivalent(r, DeliveryMode.NON_PERSISTENT);

      r = queueCons.receive(500);

      assertEquivalent(r, DeliveryMode.NON_PERSISTENT);

      queueCons.close();
      session.close();

   }



   protected void prepareMessage(final Message m) throws JMSException {
      m.setBooleanProperty("booleanProperty", true);
      m.setByteProperty("byteProperty", (byte) 3);
      m.setDoubleProperty("doubleProperty", 4.0);
      m.setFloatProperty("floatProperty", 5.0f);
      m.setIntProperty("intProperty", 6);
      m.setLongProperty("longProperty", 7);
      m.setShortProperty("shortProperty", (short) 8);
      m.setStringProperty("stringProperty", "this is a String property");

      m.setJMSCorrelationID("this is the correlation ID");
      m.setJMSReplyTo(ActiveMQServerTestCase.topic1);
      m.setJMSType("someArbitraryType");
   }

   private void assertEquivalent(final Message m, final int mode) throws JMSException {
      assertEquivalent(m, mode, false);
   }

   protected void assertEquivalent(final Message m, final int mode, final boolean redelivered) throws JMSException {
      ProxyAssertSupport.assertNotNull(m);
      ProxyAssertSupport.assertEquals(true, m.getBooleanProperty("booleanProperty"));
      ProxyAssertSupport.assertEquals((byte) 3, m.getByteProperty("byteProperty"));
      ProxyAssertSupport.assertEquals(Double.valueOf(4.0), Double.valueOf(m.getDoubleProperty("doubleProperty")));
      ProxyAssertSupport.assertEquals(Float.valueOf(5.0f), Float.valueOf(m.getFloatProperty("floatProperty")));
      ProxyAssertSupport.assertEquals(6, m.getIntProperty("intProperty"));
      ProxyAssertSupport.assertEquals(7, m.getLongProperty("longProperty"));
      ProxyAssertSupport.assertEquals((short) 8, m.getShortProperty("shortProperty"));
      ProxyAssertSupport.assertEquals("this is a String property", m.getStringProperty("stringProperty"));

      ProxyAssertSupport.assertEquals("this is the correlation ID", m.getJMSCorrelationID());
      ProxyAssertSupport.assertEquals(ActiveMQServerTestCase.topic1, m.getJMSReplyTo());
      ProxyAssertSupport.assertEquals("someArbitraryType", m.getJMSType());
      ProxyAssertSupport.assertEquals(queue1, m.getJMSDestination());
      ProxyAssertSupport.assertEquals("JMS Redelivered property", m.getJMSRedelivered(), redelivered);
      ProxyAssertSupport.assertEquals(mode, m.getJMSDeliveryMode());
   }


}

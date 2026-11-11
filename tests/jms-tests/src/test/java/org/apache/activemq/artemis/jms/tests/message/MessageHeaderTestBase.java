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

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageEOFException;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;
import java.util.Arrays;
import java.util.Enumeration;

import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.jms.client.ActiveMQBytesMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQMapMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQObjectMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQStreamMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQTextMessage;
import org.apache.activemq.artemis.jms.tests.ActiveMQServerTestCase;
import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.BeforeEach;

public abstract class MessageHeaderTestBase extends ActiveMQServerTestCase {

   /**
    * Loads the message header fields with significant values.
    */
   public static void configureMessage(final ActiveMQMessage m) throws JMSException {
      m.setJMSMessageID("ID:messageID777");
      m.setJMSTimestamp(123456789L);
      m.setJMSCorrelationID("correlationID777");
      m.setJMSReplyTo(ActiveMQJMSClient.createQueue("ReplyToQueue"));
      m.setJMSDestination(ActiveMQJMSClient.createQueue("DestinationQueue"));
      m.setJMSDeliveryMode(DeliveryMode.PERSISTENT);
      m.setJMSExpiration(987654321L);
      m.setJMSPriority(9);
      m.setBooleanProperty("booleanProperty", true);
      m.setByteProperty("byteProperty", (byte) 2);
      m.setShortProperty("shortProperty", (short) 3);
      m.setIntProperty("intProperty", 4);
      m.setLongProperty("longProperty", 5L);
      m.setFloatProperty("floatProperty", 6);
      m.setDoubleProperty("doubleProperty", 7);
      m.setStringProperty("stringPoperty", "someString");
      m.setStringProperty("JMSXNaughtyProperty", "aardvark");
   }

   /**
    * Makes sure two physically different message are equivalent: they have identical JMS fields and
    * body.
    */
   public static void ensureEquivalent(final Message m1, final ActiveMQMessage m2) throws JMSException {
      ProxyAssertSupport.assertTrue(m1 != m2);

      // Can't compare message id since not set until send

      ProxyAssertSupport.assertEquals(m1.getJMSTimestamp(), m2.getJMSTimestamp());

      byte[] corrIDBytes = null;
      String corrIDString = null;

      try {
         corrIDBytes = m1.getJMSCorrelationIDAsBytes();
      } catch (JMSException e) {
         // correlation ID specified as String
         corrIDString = m1.getJMSCorrelationID();
      }

      if (corrIDBytes != null) {
         ProxyAssertSupport.assertTrue(Arrays.equals(corrIDBytes, m2.getJMSCorrelationIDAsBytes()));
      } else if (corrIDString != null) {
         ProxyAssertSupport.assertEquals(corrIDString, m2.getJMSCorrelationID());
      } else {
         // no correlation id

         try {
            byte[] corrID2 = m2.getJMSCorrelationIDAsBytes();
            ProxyAssertSupport.assertNull(corrID2);
         } catch (JMSException e) {
            // correlatin ID specified as String
            String corrID2 = m2.getJMSCorrelationID();
            ProxyAssertSupport.assertNull(corrID2);
         }
      }
      ProxyAssertSupport.assertEquals(m1.getJMSReplyTo(), m2.getJMSReplyTo());
      ProxyAssertSupport.assertEquals(m1.getJMSDestination(), m2.getJMSDestination());
      ProxyAssertSupport.assertEquals(m1.getJMSDeliveryMode(), m2.getJMSDeliveryMode());
      // We don't check redelivered since this is always dealt with on the proxy
      ProxyAssertSupport.assertEquals(m1.getJMSType(), m2.getJMSType());
      ProxyAssertSupport.assertEquals(m1.getJMSExpiration(), m2.getJMSExpiration());
      ProxyAssertSupport.assertEquals(m1.getJMSPriority(), m2.getJMSPriority());

      int m1PropertyCount = 0, m2PropertyCount = 0;
      for (Enumeration<String> p = m1.getPropertyNames(); p.hasMoreElements(); ) {
         String name = p.nextElement();

         if (!name.startsWith("JMSX")) {
            m1PropertyCount++;
         }
      }
      for (Enumeration p = m2.getPropertyNames(); p.hasMoreElements(); ) {
         String name = (String) p.nextElement();

         if (!name.startsWith("JMSX")) {
            m2PropertyCount++;
         }
      }

      ProxyAssertSupport.assertEquals(m1PropertyCount, m2PropertyCount);

      for (Enumeration props = m1.getPropertyNames(); props.hasMoreElements(); ) {
         boolean found = false;

         String name = (String) props.nextElement();

         if (name.startsWith("JMSX")) {
            // ignore
            continue;
         }

         boolean booleanProperty = false;
         try {
            booleanProperty = m1.getBooleanProperty(name);
            found = true;
         } catch (JMSException e) {
            // not a boolean
         }

         if (found) {
            ProxyAssertSupport.assertEquals(booleanProperty, m2.getBooleanProperty(name));
            continue;
         }

         byte byteProperty = 0;
         try {
            byteProperty = m1.getByteProperty(name);
            found = true;
         } catch (JMSException e) {
            // not a byte
         }

         if (found) {
            ProxyAssertSupport.assertEquals(byteProperty, m2.getByteProperty(name));
            continue;
         }

         short shortProperty = 0;
         try {
            shortProperty = m1.getShortProperty(name);
            found = true;
         } catch (JMSException e) {
            // not a short
         }

         if (found) {
            ProxyAssertSupport.assertEquals(shortProperty, m2.getShortProperty(name));
            continue;
         }

         int intProperty = 0;
         try {
            intProperty = m1.getIntProperty(name);
            found = true;
         } catch (JMSException e) {
            // not an int
         }

         if (found) {
            ProxyAssertSupport.assertEquals(intProperty, m2.getIntProperty(name));
            continue;
         }

         long longProperty = 0;
         try {
            longProperty = m1.getLongProperty(name);
            found = true;
         } catch (JMSException e) {
            // not a long
         }

         if (found) {
            ProxyAssertSupport.assertEquals(longProperty, m2.getLongProperty(name));
            continue;
         }

         float floatProperty = 0;
         try {
            floatProperty = m1.getFloatProperty(name);
            found = true;
         } catch (JMSException e) {
            // not a float
         }

         if (found) {
            ProxyAssertSupport.assertTrue(floatProperty == m2.getFloatProperty(name));
            continue;
         }

         double doubleProperty = 0;
         try {
            doubleProperty = m1.getDoubleProperty(name);
            found = true;
         } catch (JMSException e) {
            // not a double
         }

         if (found) {
            ProxyAssertSupport.assertTrue(doubleProperty == m2.getDoubleProperty(name));
            continue;
         }

         String stringProperty = null;
         try {
            stringProperty = m1.getStringProperty(name);
            found = true;
         } catch (JMSException e) {
            // not a String
         }

         if (found) {
            ProxyAssertSupport.assertEquals(stringProperty, m2.getStringProperty(name));
            continue;
         }

         ProxyAssertSupport.fail("Cannot identify property " + name);
      }
   }

   public static void ensureEquivalent(final BytesMessage m1, final ActiveMQBytesMessage m2) throws JMSException {
      MessageHeaderTestBase.ensureEquivalent((Message) m1, m2);

      long len = m1.getBodyLength();
      for (int i = 0; i < len; i++) {
         ProxyAssertSupport.assertEquals(m1.readByte(), m2.readByte());
      }

      try {
         m1.readByte();
         ProxyAssertSupport.fail("should throw MessageEOFException");
      } catch (MessageEOFException e) {
         // OK
      }

      try {
         m2.readByte();
         ProxyAssertSupport.fail("should throw MessageEOFException");
      } catch (MessageEOFException e) {
         // OK
      }
   }

   public static void ensureEquivalent(final MapMessage m1, final ActiveMQMapMessage m2) throws JMSException {
      MessageHeaderTestBase.ensureEquivalent((Message) m1, m2);

      for (Enumeration<String> e = m1.getMapNames(); e.hasMoreElements(); ) {
         String name = e.nextElement();
         ProxyAssertSupport.assertEquals(m1.getObject(name), m2.getObject(name));
      }

      for (Enumeration<String> e = m2.getMapNames(); e.hasMoreElements(); ) {
         String name = e.nextElement();
         ProxyAssertSupport.assertEquals(m2.getObject(name), m1.getObject(name));
      }
   }

   public static void ensureEquivalent(final ObjectMessage m1, final ActiveMQObjectMessage m2) throws JMSException {
      MessageHeaderTestBase.ensureEquivalent((Message) m1, m2);
      ProxyAssertSupport.assertEquals(m1.getObject(), m2.getObject());
   }

   public static void ensureEquivalent(final StreamMessage m1, final ActiveMQStreamMessage m2) throws JMSException {
      MessageHeaderTestBase.ensureEquivalent((Message) m1, m2);

      m1.reset();
      m2.reset();
      boolean m1eof = false, m2eof = false;
      while (true) {
         byte b1, b2;
         try {
            b1 = m1.readByte();
         } catch (MessageEOFException e) {
            m1eof = true;
            break;
         }

         try {
            b2 = m2.readByte();
         } catch (MessageEOFException e) {
            m2eof = true;
            break;
         }

         ProxyAssertSupport.assertEquals(b1, b2);
      }

      if (m1eof) {
         try {
            m2.readByte();
            ProxyAssertSupport.fail("should throw MessageEOFException");
         } catch (MessageEOFException e) {
            // OK
         }
      }

      if (m2eof) {
         try {
            m1.readByte();
            ProxyAssertSupport.fail("should throw MessageEOFException");
         } catch (MessageEOFException e) {
            // OK
         }
      }
   }

   public static void ensureEquivalent(final TextMessage m1, final ActiveMQTextMessage m2) throws JMSException {
      MessageHeaderTestBase.ensureEquivalent((Message) m1, m2);
      ProxyAssertSupport.assertEquals(m1.getText(), m2.getText());
   }


   protected Connection producerConnection, consumerConnection;

   protected Session queueProducerSession, queueConsumerSession;

   protected MessageProducer queueProducer;
   protected MessageConsumer queueConsumer;

   protected Session topicProducerSession, topicConsumerSession;

   protected MessageProducer topicProducer;

   protected MessageConsumer topicConsumer;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      producerConnection = addConnection(getConnectionFactory().createConnection());
      consumerConnection = addConnection(getConnectionFactory().createConnection());

      queueProducerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      queueConsumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

      queueProducer = queueProducerSession.createProducer(queue1);
      queueConsumer = queueConsumerSession.createConsumer(queue1);

      topicProducerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      topicConsumerSession = consumerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);

      topicProducer = topicProducerSession.createProducer(ActiveMQServerTestCase.topic1);
      topicConsumer = topicConsumerSession.createConsumer(ActiveMQServerTestCase.topic1);

      consumerConnection.start();
   }
}

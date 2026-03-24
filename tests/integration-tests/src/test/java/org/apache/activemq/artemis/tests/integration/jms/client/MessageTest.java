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
package org.apache.activemq.artemis.tests.integration.jms.client;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageEOFException;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.StreamMessage;

import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.reader.MessageUtil;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.junit.Assert;
import org.junit.Test;

public class MessageTest extends JMSTestBase {


   private static final long TIMEOUT = 1000;

   private static final String propName1 = "myprop1";

   private static final String propName2 = "myprop2";

   private static final String propName3 = "myprop3";



   /**
    * @see https://jira.jboss.org/jira/browse/HORNETQ-242
    */
   @Test
   public void testStreamMessageReadsNull() throws Exception {
      Connection conn = cf.createConnection();
      try {
         Queue queue = createQueue("testQueue");

         Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer prod = sess.createProducer(queue);

         MessageConsumer cons = sess.createConsumer(queue);

         conn.start();

         StreamMessage msg = sess.createStreamMessage();

         msg.writeInt(1);
         msg.writeInt(2);
         msg.writeInt(3);

         StreamMessage received = (StreamMessage) sendAndConsumeMessage(msg, prod, cons);

         Assert.assertNotNull(received);

         assertEquals(1, received.readObject());
         assertEquals(2, received.readObject());
         assertEquals(3, received.readObject());

         try {
            received.readObject();

            fail("Should throw exception");
         } catch (MessageEOFException e) {
            //Ok
         }

         try {
            received.readBoolean();

            fail("Should throw exception");
         } catch (MessageEOFException e) {
            //Ok
         }

         try {
            received.readByte();

            fail("Should throw exception");
         } catch (MessageEOFException e) {
            //Ok
         }

         try {
            received.readChar();

            fail("Should throw exception");
         } catch (MessageEOFException e) {
            //Ok
         }

         try {
            received.readDouble();

            fail("Should throw exception");
         } catch (MessageEOFException e) {
            //Ok
         }

         try {
            received.readFloat();

            fail("Should throw exception");
         } catch (MessageEOFException e) {
            //Ok
         }

         try {
            received.readInt();

            fail("Should throw exception");
         } catch (MessageEOFException e) {
            //Ok
         }

         try {
            received.readLong();

            fail("Should throw exception");
         } catch (MessageEOFException e) {
            //Ok
         }

         try {
            received.readShort();

            fail("Should throw exception");
         } catch (MessageEOFException e) {
            //Ok
         }

         try {
            received.readString();

            fail("Should throw exception");
         } catch (MessageEOFException e) {
            //Ok
         }
      } finally {
         conn.close();
      }
   }

   @Test
   public void testNullProperties() throws Exception {
      conn = cf.createConnection();

      Queue queue = createQueue("testQueue");

      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageProducer prod = sess.createProducer(queue);

      MessageConsumer cons = sess.createConsumer(queue);

      conn.start();

      Message msg = sess.createMessage();

      msg.setStringProperty("Test", "SomeValue");

      assertEquals("SomeValue", msg.getStringProperty("Test"));

      msg.setStringProperty("Test", null);

      assertEquals(null, msg.getStringProperty("Test"));

      msg.setObjectProperty(MessageTest.propName1, null);

      msg.setObjectProperty(MessageUtil.JMSXGROUPID, null);

      msg.setObjectProperty(MessageUtil.JMSXUSERID, null);

      msg.setStringProperty(MessageTest.propName2, null);

      msg.getStringProperty(MessageTest.propName1);

      msg.setStringProperty("Test", null);

      Message received = sendAndConsumeMessage(msg, prod, cons);

      Assert.assertNotNull(received);

      checkProperties(received);
   }




   private void checkProperties(final Message message) throws Exception {
      Assert.assertNull(message.getObjectProperty(MessageTest.propName1));
      Assert.assertNull(message.getStringProperty(MessageTest.propName1));
      Assert.assertNull(message.getStringProperty(MessageTest.propName2));
      Assert.assertNull(message.getObjectProperty(MessageTest.propName2));
      Assert.assertNull(message.getStringProperty(MessageTest.propName3));
      Assert.assertNull(message.getObjectProperty(MessageTest.propName3));

      try {
         instanceLog.debug(message.getIntProperty(MessageTest.propName1));
         Assert.fail("Should throw exception");
      } catch (NumberFormatException e) {
         // Ok
      }

      try {
         instanceLog.debug(message.getShortProperty(MessageTest.propName1));
      } catch (NumberFormatException e) {
         // Ok
      }
      try {
         instanceLog.debug(message.getByteProperty(MessageTest.propName1));
      } catch (NumberFormatException e) {
         // Ok
      }
      Assert.assertEquals(false, message.getBooleanProperty(MessageTest.propName1));
      try {
         instanceLog.debug(message.getLongProperty(MessageTest.propName1));
      } catch (NumberFormatException e) {
         // Ok
      }
      try {
         instanceLog.debug(message.getFloatProperty(MessageTest.propName1));
      } catch (NullPointerException e) {
         // Ok
      }
      try {
         instanceLog.debug(message.getDoubleProperty(MessageTest.propName1));
      } catch (NullPointerException e) {
         // Ok
      }
   }

   // https://issues.jboss.org/browse/HORNETQ-988
   @Test
   public void testShouldNotThrowException() throws Exception {
      Connection conn = null;

      createTopic(true, "Topic1");
      try {
         conn = cf.createConnection();

         conn.start();

         Session session1 = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         session1.createConsumer(ActiveMQJMSClient.createTopic("Topic1"));
         Session session2 = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         session2.createConsumer(ActiveMQJMSClient.createTopic("*"));

         session1.close();
         session2.close();

         Session session3 = conn.createSession(false, Session.CLIENT_ACKNOWLEDGE);
         MessageConsumer cons = session3.createConsumer(ActiveMQJMSClient.createTopic("Topic1"));
         MessageProducer prod = session3.createProducer(ActiveMQJMSClient.createTopic("Topic1"));
         MessageConsumer consGeral = session3.createConsumer(ActiveMQJMSClient.createTopic("*"));
         prod.send(session3.createTextMessage("hello"));
         assertNotNull(cons.receive(5000));
         assertNotNull(consGeral.receive(5000));
         createTopic(true, "Topic2");

         MessageProducer prod2 = session3.createProducer(ActiveMQJMSClient.createTopic("Topic2"));

         prod2.send(session3.createTextMessage("test"));

         assertNull(cons.receiveNoWait());

         assertNotNull(consGeral.receive(5000));

      } finally {
         if (conn != null) {
            conn.close();
         }
      }
   }

   private Message sendAndConsumeMessage(final Message msg,
                                         final MessageProducer prod,
                                         final MessageConsumer cons) throws Exception {
      prod.send(msg);

      Message received = cons.receive(MessageTest.TIMEOUT);

      return received;
   }

}

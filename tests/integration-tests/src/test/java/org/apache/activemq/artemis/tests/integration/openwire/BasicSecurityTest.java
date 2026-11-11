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
package org.apache.activemq.artemis.tests.integration.openwire;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.JMSSecurityException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.ServerSession;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;
import java.io.Serializable;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.command.ActiveMQQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class BasicSecurityTest extends BasicOpenWireTest {

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      this.enableSecurity = true;
      super.setUp();
   }

   @Test
   public void testConnectionWithCredentials() throws Exception {
      Connection newConn = null;

      //correct
      try {
         newConn = factory.createConnection("openwireSender", "SeNdEr");
         newConn.start();
         newConn.close();

         newConn = factory.createConnection("openwireReceiver", "ReCeIvEr");
         newConn.start();
         newConn.close();

         newConn = null;
      } finally {
         if (newConn != null) {
            newConn.close();
         }
      }

      //wrong password
      try {
         newConn = factory.createConnection("openwireSender", "WrongPasswD");
         newConn.start();
      } catch (JMSSecurityException e) {
         //expected
      } finally {
         if (newConn != null) {
            newConn.close();
         }
      }

      //wrong user
      try {
         newConn = factory.createConnection("wronguser", "SeNdEr");
         newConn.start();
      } catch (JMSSecurityException e) {
         //expected
      } finally {
         if (newConn != null) {
            newConn.close();
         }
      }

      //both wrong
      try {
         newConn = factory.createConnection("wronguser", "wrongpass");
         newConn.start();
      } catch (JMSSecurityException e) {
         //expected
      } finally {
         if (newConn != null) {
            newConn.close();
         }
      }

      //default user
      try {
         newConn = factory.createConnection();
         newConn.start();
      } catch (JMSSecurityException e) {
         //expected
      } finally {
         if (newConn != null) {
            newConn.close();
         }
      }
   }

   @Test
   public void testSendnReceiveAuthorization() throws Exception {
      Connection sendingConn = null;
      Connection receivingConn = null;

      //Sender
      try {
         Destination dest = new ActiveMQQueue(queueName);

         receivingConn = factory.createConnection("openwireReceiver", "ReCeIvEr");
         receivingConn.start();

         sendingConn = factory.createConnection("openwireSender", "SeNdEr");
         sendingConn.start();

         Session sendingSession = sendingConn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session receivingSession = receivingConn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         TextMessage message = sendingSession.createTextMessage("Hello World");

         MessageProducer producer = null;

         try {
            receivingSession.createProducer(dest);
            fail("Should not be able to create a producer when not authorized to send");
         } catch (JMSSecurityException ex) {
            // expected
         }

         producer = receivingSession.createProducer(null);

         try {
            producer.send(dest, message);
            fail("exception expected");
         } catch (JMSSecurityException e) {
            //expected
            producer.close();
         }

         producer = sendingSession.createProducer(dest);
         producer.send(message);

         MessageConsumer consumer;
         try {
            consumer = sendingSession.createConsumer(dest);
            fail("exception expected");
         } catch (JMSSecurityException e) {
            e.printStackTrace();
            //expected
         }

         consumer = receivingSession.createConsumer(dest);
         TextMessage received = (TextMessage) consumer.receive(5000);

         assertNotNull(received);
         assertEquals("Hello World", received.getText());
      } finally {
         if (sendingConn != null) {
            sendingConn.close();
         }

         if (receivingConn != null) {
            receivingConn.close();
         }
      }
   }

   @Test
   public void testSendReceiveAuthorizationOnComposite() throws Exception {
      Connection sendingConn = null;
      Connection receivingConn = null;

      final String compositeName = queueName + "," + queueName2;

      //Sender
      try {
         Destination composite = new ActiveMQQueue(compositeName);
         Destination dest1 = new ActiveMQQueue(queueName);
         Destination dest2 = new ActiveMQQueue(queueName2);

         // Server must have this composite version of the Address and Queue for this test to work
         // as the user does not have auto create permissions and it will try to create this.
         server.createQueue(QueueConfiguration.of(compositeName).setAddress(compositeName).setRoutingType(RoutingType.ANYCAST));

         receivingConn = factory.createConnection("openwireReceiver", "ReCeIvEr");
         receivingConn.start();

         sendingConn = factory.createConnection("openwireSender", "SeNdEr");
         sendingConn.start();

         Session sendingSession = sendingConn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session receivingSession = receivingConn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         TextMessage message = sendingSession.createTextMessage("Hello World");

         MessageProducer producer = null;

         try {
            receivingSession.createProducer(composite);
            fail("Should get a security exception on create");
         } catch (JMSSecurityException ex) {
            // expected
         }

         producer = receivingSession.createProducer(null);

         try {
            producer.send(composite, message);
            fail("exception expected");
         } catch (JMSSecurityException e) {
            //expected
            producer.close();
         }

         producer = sendingSession.createProducer(composite);
         producer.send(message);

         try {
            sendingSession.createConsumer(dest1);
            fail("exception expected");
         } catch (JMSSecurityException e) {
            e.printStackTrace();
            //expected
         }

         MessageConsumer consumer1 = receivingSession.createConsumer(dest1);
         TextMessage received1 = (TextMessage) consumer1.receive(5000);
         MessageConsumer consumer2 = receivingSession.createConsumer(dest2);
         TextMessage received2 = (TextMessage) consumer2.receive(5000);

         assertNotNull(received1);
         assertEquals("Hello World", received1.getText());
         assertNotNull(received2);
         assertEquals("Hello World", received2.getText());
      } finally {
         if (sendingConn != null) {
            sendingConn.close();
         }

         if (receivingConn != null) {
            receivingConn.close();
         }
      }
   }

   @Test
   public void testCreateTempDestinationAuthorization() throws Exception {
      Connection conn1 = null;
      Connection conn2 = null;

      try {
         conn1 = factory.createConnection("openwireGuest", "GuEsT");
         conn1.start();

         conn2 = factory.createConnection("openwireDestinationManager", "DeStInAtIoN");
         conn2.start();

         Session session1 = conn1.createSession(false, Session.AUTO_ACKNOWLEDGE);

         try {
            session1.createTemporaryQueue();
            fail("user shouldn't be able to create temp queue");
         } catch (JMSSecurityException e) {
            //expected
         }

         Session session2 = conn2.createSession(false, Session.AUTO_ACKNOWLEDGE);

         TemporaryQueue q = session2.createTemporaryQueue();
         assertNotNull(q);
      } finally {
         if (conn1 != null) {
            conn1.close();
         }

         if (conn2 != null) {
            conn2.close();
         }
      }
   }

   @Test
   public void testConnectionConsumer() throws Exception {
      Connection conn1 = null;

      try {

         conn1 = factory.createConnection("openwireGuest", "GuEsT");
         conn1.start();

         try {
            Destination dest = new ActiveMQQueue(queueName);

            conn1.createConnectionConsumer(dest, null, () -> new ServerSession() {
               @Override
               public Session getSession() throws JMSException {
                  return new Session() {
                     @Override
                     public BytesMessage createBytesMessage() throws JMSException {
                        return null;
                     }

                     @Override
                     public MapMessage createMapMessage() throws JMSException {
                        return null;
                     }

                     @Override
                     public Message createMessage() throws JMSException {
                        return null;
                     }

                     @Override
                     public ObjectMessage createObjectMessage() throws JMSException {
                        return null;
                     }

                     @Override
                     public ObjectMessage createObjectMessage(Serializable object) throws JMSException {
                        return null;
                     }

                     @Override
                     public StreamMessage createStreamMessage() throws JMSException {
                        return null;
                     }

                     @Override
                     public TextMessage createTextMessage() throws JMSException {
                        return null;
                     }

                     @Override
                     public TextMessage createTextMessage(String text) throws JMSException {
                        return null;
                     }

                     @Override
                     public boolean getTransacted() throws JMSException {
                        return false;
                     }

                     @Override
                     public int getAcknowledgeMode() throws JMSException {
                        return 0;
                     }

                     @Override
                     public void commit() throws JMSException {

                     }

                     @Override
                     public void rollback() throws JMSException {

                     }

                     @Override
                     public void close() throws JMSException {

                     }

                     @Override
                     public void recover() throws JMSException {

                     }

                     @Override
                     public MessageListener getMessageListener() throws JMSException {
                        return null;
                     }

                     @Override
                     public void setMessageListener(MessageListener listener) throws JMSException {

                     }

                     @Override
                     public void run() {

                     }

                     @Override
                     public MessageProducer createProducer(Destination destination) throws JMSException {
                        return null;
                     }

                     @Override
                     public MessageConsumer createConsumer(Destination destination) throws JMSException {
                        return null;
                     }

                     @Override
                     public MessageConsumer createConsumer(Destination destination, String messageSelector) throws JMSException {
                        return null;
                     }

                     @Override
                     public MessageConsumer createConsumer(Destination destination,
                                                           String messageSelector,
                                                           boolean NoLocal) throws JMSException {
                        return null;
                     }

                     @Override
                     public Queue createQueue(String queueName) throws JMSException {
                        return null;
                     }

                     @Override
                     public Topic createTopic(String topicName) throws JMSException {
                        return null;
                     }

                     @Override
                     public TopicSubscriber createDurableSubscriber(Topic topic, String name) throws JMSException {
                        return null;
                     }

                     @Override
                     public TopicSubscriber createDurableSubscriber(Topic topic,
                                                                    String name,
                                                                    String messageSelector,
                                                                    boolean noLocal) throws JMSException {
                        return null;
                     }

                     @Override
                     public QueueBrowser createBrowser(Queue queue) throws JMSException {
                        return null;
                     }

                     @Override
                     public QueueBrowser createBrowser(Queue queue, String messageSelector) throws JMSException {
                        return null;
                     }

                     @Override
                     public TemporaryQueue createTemporaryQueue() throws JMSException {
                        return null;
                     }

                     @Override
                     public TemporaryTopic createTemporaryTopic() throws JMSException {
                        return null;
                     }

                     @Override
                     public void unsubscribe(String name) throws JMSException {

                     }

                     @Override
                     public MessageConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName) throws JMSException {
                        return null;
                     }

                     @Override
                     public MessageConsumer createSharedConsumer(Topic topic,
                                                                 String sharedSubscriptionName,
                                                                 String messageSelector) throws JMSException {
                        return null;
                     }

                     @Override
                     public MessageConsumer createDurableConsumer(Topic topic, String name) throws JMSException {
                        return null;
                     }

                     @Override
                     public MessageConsumer createDurableConsumer(Topic topic,
                                                                  String name,
                                                                  String messageSelector,
                                                                  boolean noLocal) throws JMSException {
                        return null;
                     }

                     @Override
                     public MessageConsumer createSharedDurableConsumer(Topic topic, String name) throws JMSException {
                        return null;
                     }

                     @Override
                     public MessageConsumer createSharedDurableConsumer(Topic topic, String name, String messageSelector) throws JMSException {
                        return null;
                     }
                  };
               }

               @Override
               public void start() throws JMSException {

               }
            }, 100);
         } catch (JMSSecurityException e) {
            //expected
         }

      } finally {
         if (conn1 != null) {
            conn1.close();
         }
      }
   }
}

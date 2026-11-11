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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.BytesMessage;
import javax.jms.CompletionListener;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.IllegalStateException;
import javax.jms.JMSContext;
import javax.jms.JMSProducer;
import javax.jms.JMSSecurityException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.DefaultConnectionProperties;
import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test JMS Security.
 * <p>
 * This test must be run with the Test security config. on the server
 */
public class SecurityTest extends JMSTestCase {
   private String originalAmqUser;
   private String originalAmqPassword;
   private String originalBrokerBindUrl;

   @BeforeEach
   public void setupProperty() {
      originalAmqUser = System.getProperty(DefaultConnectionProperties.AMQ_USER);
      originalAmqPassword = System.getProperty(DefaultConnectionProperties.AMQ_PASSWORD);
      originalBrokerBindUrl = System.getProperty(DefaultConnectionProperties.BROKER_BIND_URL);
   }

   @AfterEach
   public void clearProperty() {
      if (originalAmqUser == null) {
         System.clearProperty(DefaultConnectionProperties.AMQ_USER);
      } else {
         System.setProperty(DefaultConnectionProperties.AMQ_USER, originalAmqUser);
      }
      if (originalAmqPassword == null) {
         System.clearProperty(DefaultConnectionProperties.AMQ_PASSWORD);
      } else {
         System.setProperty(DefaultConnectionProperties.AMQ_PASSWORD, originalAmqPassword);
      }
      if (originalBrokerBindUrl == null) {
         System.clearProperty(DefaultConnectionProperties.BROKER_BIND_URL);
      } else {
         System.setProperty(DefaultConnectionProperties.BROKER_BIND_URL, originalBrokerBindUrl);
      }
      DefaultConnectionProperties.initialize();
   }


   /**
    * Login with no user, no password Should allow login (equivalent to guest)
    */
   @Test
   public void testLoginNoUserNoPassword() throws Exception {
      createConnection();
      createConnection(null, null);
   }

   /**
    * Login with no user, no password
    * Should allow login (equivalent to guest)
    */
   @Test
   public void testLoginNoUserNoPasswordWithNoGuest() throws Exception {
      createConnection();
      createConnection(null, null);
   }

   /**
    * Login with valid user and password
    * Should allow
    */
   @Test
   public void testLoginValidUserAndPassword() throws Exception {
      createConnection("guest", "guest");
   }

   /**
    * Login with valid user and password
    * Should allow
    */
   @Test
   public void testLoginValidUserAndPasswordSystemProperty() throws Exception {
      System.setProperty(DefaultConnectionProperties.AMQ_USER, "guest");
      System.setProperty(DefaultConnectionProperties.AMQ_PASSWORD, "guest");
      DefaultConnectionProperties.initialize();
      ConnectionFactory cf = new ActiveMQConnectionFactory();
      Connection conn = addConnection(cf.createConnection());
   }

   /**
    * Login with valid user and password
    * Should allow
    */
   @Test
   public void testLoginValidUserAndPasswordSystemPropertyWithAdditionalProperties() throws Exception {
      System.setProperty(DefaultConnectionProperties.AMQ_USER, "guest");
      System.setProperty(DefaultConnectionProperties.AMQ_PASSWORD, "guest");
      System.setProperty(DefaultConnectionProperties.BROKER_BIND_URL, "tcp://localhost:61616?compressLargeMessage=true");
      DefaultConnectionProperties.initialize();
      ConnectionFactory cf = new ActiveMQConnectionFactory();
      Connection conn = addConnection(cf.createConnection());
      assertTrue(((ActiveMQConnectionFactory) cf).isCompressLargeMessage());
   }

   /**
    * Login with valid user and invalid password
    * Should allow
    */
   @Test
   public void testLoginValidUserInvalidPassword() throws Exception {
      try {
         Connection conn1 = createConnection("guest", "not.the.valid.password");
         ProxyAssertSupport.fail();
      } catch (JMSSecurityException e) {
         // Expected
      }
   }

   /**
    * Login with valid user and invalid password
    * Should allow
    */
   @Test
   public void testLoginValidUserInvalidPasswordSystemProperty() throws Exception {
      System.setProperty(DefaultConnectionProperties.AMQ_USER, "guest");
      System.setProperty(DefaultConnectionProperties.AMQ_PASSWORD, "not.the.valid.password");
      DefaultConnectionProperties.initialize();
      try {
         ConnectionFactory cf = new ActiveMQConnectionFactory();
         Connection conn1 = addConnection(cf.createConnection());
         ProxyAssertSupport.fail();
      } catch (JMSSecurityException e) {
         // Expected
      }
   }

   /**
    * Login with invalid user and invalid password
    * Should allow
    */
   @Test
   public void testLoginInvalidUserInvalidPassword() throws Exception {
      try {
         Connection conn1 = createConnection("not.the.valid.user", "not.the.valid.password");
         ProxyAssertSupport.fail();
      } catch (JMSSecurityException e) {
         // Expected
      }
   }

   /**
    * Login with valid user and password
    * But try send to address not authorised - Persistent
    * Should not allow and should throw exception
    */
   @Test
   public void testLoginValidUserAndPasswordButNotAuthorisedToSend() throws Exception {
      SimpleString queueName = SimpleString.of("guest.cannot.send");
      if (getJmsServer().locateQueue(queueName) == null) {
         getJmsServer().createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));
      }
      ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616");
      Connection connection = connectionFactory.createConnection("guest", "guest");
      Session session = connection.createSession();
      Destination destination = session.createQueue(queueName.toString());
      MessageProducer messageProducer = session.createProducer(destination);
      try {
         messageProducer.send(session.createTextMessage("hello"));
         fail("JMSSecurityException expected as guest is not allowed to send");
      } catch (JMSSecurityException activeMQSecurityException) {
         //pass
      }
      connection.close();
   }

   /**
    * Login with valid user and password
    * But try send to address not authorised - Non Persistent.
    * Should have same behaviour as Persistent with exception on send.
    */
   @Test
   public void testLoginValidUserAndPasswordButNotAuthorisedToSendNonPersistent() throws Exception {
      SimpleString queueName = SimpleString.of("guest.cannot.send");
      if (getJmsServer().locateQueue(queueName) == null) {
         getJmsServer().createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));
      }
      ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616");
      connectionFactory.setConfirmationWindowSize(100);
      connectionFactory.setBlockOnDurableSend(false);
      connectionFactory.setBlockOnNonDurableSend(false);
      Connection connection = connectionFactory.createConnection("guest", "guest");
      Session session = connection.createSession();
      Destination destination = session.createQueue(queueName.toString());
      MessageProducer messageProducer = session.createProducer(destination);
      messageProducer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
      try {
         AtomicReference<Exception> e = new AtomicReference<>();

         CountDownLatch countDownLatch = new CountDownLatch(1);
         messageProducer.send(session.createTextMessage("hello"), new CompletionListener() {
            @Override
            public void onCompletion(Message message) {
               countDownLatch.countDown();
            }

            @Override
            public void onException(Message message, Exception exception) {
               e.set(exception);
               countDownLatch.countDown();
            }
         });
         countDownLatch.await(10, TimeUnit.SECONDS);
         if (e.get() != null) {
            throw e.get();
         }
         fail("JMSSecurityException expected as guest is not allowed to send");
      } catch (JMSSecurityException activeMQSecurityException) {
         activeMQSecurityException.printStackTrace();
      } finally {
         connection.close();
      }
   }

   /**
    * Same as testLoginValidUserAndPasswordButNotAuthorisedToSendNonPersistent, but using JMS 2 API.
    */
   @Test
   public void testLoginValidUserAndPasswordButNotAuthorisedToSendNonPersistentJMS2() throws Exception {
      SimpleString queueName = SimpleString.of("guest.cannot.send");
      if (getJmsServer().locateQueue(queueName) == null) {
         getJmsServer().createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));
      }
      ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616");
      connectionFactory.setConfirmationWindowSize(100);
      connectionFactory.setBlockOnDurableSend(false);
      connectionFactory.setBlockOnNonDurableSend(false);
      JMSContext context = connectionFactory.createContext("guest", "guest");
      Destination destination = context.createQueue(queueName.toString());
      JMSProducer messageProducer = context.createProducer();
      messageProducer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
      try {
         AtomicReference<Exception> e = new AtomicReference<>();

         CountDownLatch countDownLatch = new CountDownLatch(1);
         messageProducer.setAsync(new CompletionListener() {
            @Override
            public void onCompletion(Message message) {
               countDownLatch.countDown();
            }

            @Override
            public void onException(Message message, Exception exception) {
               e.set(exception);
               countDownLatch.countDown();
            }
         });
         messageProducer.send(destination, context.createTextMessage("hello"));
         countDownLatch.await(10, TimeUnit.SECONDS);
         if (e.get() != null) {
            throw e.get();
         }
         fail("JMSSecurityException expected as guest is not allowed to send");
      } catch (JMSSecurityException activeMQSecurityException) {
         activeMQSecurityException.printStackTrace();
      } finally {
         context.close();
      }
   }

   /**
    * Same as testLoginValidUserAndPasswordButNotAuthorisedToSendNonPersistent, but using a large message.
    */
   @Test
   public void testLoginValidUserAndPasswordButNotAuthorisedToSendLargeNonPersistent() throws Exception {
      SimpleString queueName = SimpleString.of("guest.cannot.send");
      if (getJmsServer().locateQueue(queueName) == null) {
         getJmsServer().createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));
      }
      ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616");
      connectionFactory.setConfirmationWindowSize(100);
      connectionFactory.setBlockOnDurableSend(false);
      connectionFactory.setBlockOnNonDurableSend(false);
      connectionFactory.setMinLargeMessageSize(1024);
      Connection connection = connectionFactory.createConnection("guest", "guest");
      Session session = connection.createSession();
      Destination destination = session.createQueue(queueName.toString());
      MessageProducer messageProducer = session.createProducer(destination);
      messageProducer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
      try {
         AtomicReference<Exception> e = new AtomicReference<>();

         CountDownLatch countDownLatch = new CountDownLatch(1);
         BytesMessage message = session.createBytesMessage();
         message.writeBytes(new byte[10 * 1024]);
         messageProducer.send(message, new CompletionListener() {
            @Override
            public void onCompletion(Message message) {
               countDownLatch.countDown();
            }

            @Override
            public void onException(Message message, Exception exception) {
               e.set(exception);
               countDownLatch.countDown();
            }
         });
         countDownLatch.await(10, TimeUnit.SECONDS);
         if (e.get() != null) {
            throw e.get();
         }
         fail("JMSSecurityException expected as guest is not allowed to send");
      } catch (JMSSecurityException activeMQSecurityException) {
         activeMQSecurityException.printStackTrace();
      }
      connection.close();
   }

   /**
    * user/pwd with preconfigured clientID, should return preconf
    */
   @Test
   public void testPreConfClientID() throws Exception {
      Connection conn = null;
      try {
         ActiveMQServerTestCase.deployConnectionFactory("dilbert-id", "preConfcf", "preConfcf");
         ConnectionFactory cf = (ConnectionFactory) getInitialContext().lookup("preConfcf");
         conn = cf.createConnection("guest", "guest");
         String clientID = conn.getClientID();
         ProxyAssertSupport.assertEquals("Invalid ClientID", "dilbert-id", clientID);
      } finally {
         if (conn != null) {
            conn.close();
         }
         ActiveMQServerTestCase.undeployConnectionFactory("preConfcf");
      }
   }

   /**
    * Try setting client ID
    */
   @Test
   public void testSetClientID() throws Exception {
      Connection conn = createConnection();
      conn.setClientID("myID");
      String clientID = conn.getClientID();
      ProxyAssertSupport.assertEquals("Invalid ClientID", "myID", clientID);
   }

   /**
    * Try setting client ID on preconfigured connection - should throw exception
    */
   @Test
   public void testSetClientIDPreConf() throws Exception {
      Connection conn = null;
      try {
         ActiveMQServerTestCase.deployConnectionFactory("dilbert-id", "preConfcf", "preConfcf");
         ConnectionFactory cf = (ConnectionFactory) getInitialContext().lookup("preConfcf");
         conn = cf.createConnection("guest", "guest");
         conn.setClientID("myID");
         ProxyAssertSupport.fail();
      } catch (IllegalStateException e) {
         // Expected
      } finally {
         if (conn != null) {
            conn.close();
         }
         ActiveMQServerTestCase.undeployConnectionFactory("preConfcf");
      }
   }

   /**
    * Try setting client ID after an operation has been performed on the connection
    */
   @Test
   public void testSetClientIDAfterOp() throws Exception {
      Connection conn = createConnection();
      conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      try {
         conn.setClientID("myID");
         ProxyAssertSupport.fail();
      } catch (IllegalStateException e) {
         // Expected
      }
   }
}

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

import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;
import javax.jms.ConnectionMetaData;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.ServerSessionPool;
import javax.jms.Session;
import javax.jms.TopicConnection;

import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * Connection tests. Contains all connection tests, except tests relating to closing a connection,
 * which go to ConnectionClosedTest.
 */
public class ConnectionTest extends JMSTestCase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


   @Test
   public void testManyConnections() throws Exception {
      for (int i = 0; i < 100; i++) {
         Connection conn = createConnection();
         conn.close();
      }
   }

   //
   // Note: All tests related to closing a Connection should go to ConnectionClosedTest
   //

   @Test
   public void testGetClientID() throws Exception {
      Connection connection = createConnection();
      String clientID = connection.getClientID();

      // We don't currently set client ids on the server, so this should be null.
      // In the future we may provide connection factories that set a specific client id
      // so this may change
      ProxyAssertSupport.assertNull(clientID);

      connection.close();
   }

   @Test
   public void testSetClientID() throws Exception {
      Connection connection = createConnection();

      final String clientID = "my-test-client-id";

      connection.setClientID(clientID);

      ProxyAssertSupport.assertEquals(clientID, connection.getClientID());

      Connection connection2 = createConnection();
      try {
         connection2.setClientID(clientID);
         fail("setClientID was expected to throw an exception");
      } catch (JMSException e) {
         // expected
      }

      connection.close();

      connection2.setClientID(clientID);
   }

   @Test
   public void testSetClientIdAfterStop() throws Exception {
      try (Connection connection = createConnection()) {
         connection.stop();
         connection.setClientID("clientId");
      }
   }


   @Test
   public void testSetClientAfterStart() throws Exception {
      Connection connection = null;
      try {
         connection = createConnection();

         // we startthe connection
         connection.start();

         // an attempt to set the client ID now should throw an IllegalStateException
         connection.setClientID("testSetClientID_2");
         ProxyAssertSupport.fail("Should throw a javax.jms.IllegalStateException");
      } catch (javax.jms.IllegalStateException e) {
      } catch (JMSException e) {
         ProxyAssertSupport.fail("Should raise a javax.jms.IllegalStateException, not a " + e);
      } catch (java.lang.IllegalStateException e) {
         ProxyAssertSupport.fail("Should raise a javax.jms.IllegalStateException, not a java.lang.IllegalStateException");
      } finally {
         if (connection != null) {
            connection.close();
         }
      }

   }

   @Test
   public void testSetClientIDFail() throws Exception {
      final String clientID = "my-test-client-id";

      // Setting a client id must be the first thing done to the connection
      // otherwise a javax.jms.IllegalStateException must be thrown

      Connection connection = createConnection();
      connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      try {
         connection.setClientID(clientID);
         ProxyAssertSupport.fail();
      } catch (javax.jms.IllegalStateException e) {
         logger.trace("Caught exception ok");
      }

      connection.close();

      // TODO: This will probably go away, remove it enterily after we
      //       make sure this rule can go away
      //      connection = createConnection();
      //      connection.getClientID();
      //      try
      //      {
      //         connection.setClientID(clientID);
      //         ProxyAssertSupport.fail();
      //      }
      //      catch (javax.jms.IllegalStateException e)
      //      {
      //      }
      //      connection.close();
   }

   @Test
   public void testGetMetadata() throws Exception {
      Connection connection = createConnection();

      ConnectionMetaData metaData = connection.getMetaData();

      // TODO - need to check whether these are same as current version
      metaData.getJMSMajorVersion();
      metaData.getJMSMinorVersion();
      metaData.getJMSProviderName();
      metaData.getJMSVersion();
      metaData.getJMSXPropertyNames();
      metaData.getProviderMajorVersion();
      metaData.getProviderMinorVersion();
      metaData.getProviderVersion();

      connection.close();
   }

   @Test
   public void testSetClientIdAfterGetMetadata() throws Exception {
      try (Connection connection = createConnection()) {
         connection.getMetaData();
         connection.setClientID("clientId");
      }
   }

   /**
    * Test creation of QueueSession
    */
   @Test
   public void testQueueConnection1() throws Exception {
      QueueConnection qc = queueCf.createQueueConnection();

      qc.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);

      qc.close();
   }

   /**
    * Test creation of TopicSession
    */
   @Test
   public void testTopicConnection() throws Exception {
      TopicConnection tc = topicCf.createTopicConnection();

      tc.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);
   }

   /**
    * Test ExceptionListener stuff
    */
   @Test
   public void testExceptionListener() throws Exception {
      Connection conn = createConnection();

      ExceptionListener listener1 = new MyExceptionListener();

      conn.setExceptionListener(listener1);

      ExceptionListener listener2 = conn.getExceptionListener();

      ProxyAssertSupport.assertNotNull(listener2);

      ProxyAssertSupport.assertEquals(listener1, listener2);

      conn.close();

      // Ensure setting / getting exception listener can occur before setting clientid.
      final String clientID = "my-test-client-id";

      Connection connection = createConnection();
      ExceptionListener listener = connection.getExceptionListener();
      try {
         connection.setClientID(clientID);
      } catch (javax.jms.IllegalStateException e) {
         ProxyAssertSupport.fail();
      }
      connection.close();

      connection = createConnection();
      connection.setExceptionListener(listener);
      try {
         connection.setClientID(clientID);
      } catch (javax.jms.IllegalStateException e) {
         ProxyAssertSupport.fail();
      }
      connection.close();

   }

   @Test
   public void testConnectionListenerBug() throws Exception {
      for (int i = 0; i < 1000; i++) {
         Connection conn = createConnection();

         MyExceptionListener listener = new MyExceptionListener();

         conn.setExceptionListener(listener);

         conn.close();
      }
   }

   /**
    * This test is similar to a JORAM Test...
    * (UnifiedTest::testCreateDurableConnectionConsumerOnQueueConnection)
    */
   @Test
   public void testDurableSubscriberOnQueueConnection() throws Exception {
      QueueConnection queueConnection = ((QueueConnectionFactory) queueCf).createQueueConnection();

      try {
         queueConnection.createDurableConnectionConsumer(ActiveMQServerTestCase.topic1, "subscriptionName", "", (ServerSessionPool) null, 1);
         ProxyAssertSupport.fail("Should throw a javax.jms.IllegalStateException");
      } catch (javax.jms.IllegalStateException e) {
      } catch (java.lang.IllegalStateException e) {
         ProxyAssertSupport.fail("Should throw a javax.jms.IllegalStateException");
      } catch (JMSException e) {
         ProxyAssertSupport.fail("Should throw a javax.jms.IllegalStateException, not a " + e);
      } finally {
         queueConnection.close();
      }
   }

   static class MyExceptionListener implements ExceptionListener {

      JMSException exceptionReceived;

      @Override
      public void onException(final JMSException exception) {
         exceptionReceived = exception;
         logger.trace("Received exception");
      }
   }
}

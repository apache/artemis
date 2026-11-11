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
package org.apache.activemq.artemis.tests.integration.amqp;

import javax.jms.Connection;
import javax.jms.JMSException;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.LinkedList;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class JMSClientTestSupport extends AmqpClientTestSupport {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected LinkedList<Connection> jmsConnections = new LinkedList<>();

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();
   }

   @AfterEach
   @Override
   public void tearDown() throws Exception {
      try {
         for (Connection connection : jmsConnections) {
            try {
               connection.close();
            } catch (Throwable ignored) {
               ignored.printStackTrace();
            }
         }
      } catch (Exception e) {
         logger.warn("Exception during tearDown", e);
      }
      jmsConnections.clear();

      super.tearDown();
   }

   protected Connection trackJMSConnection(Connection connection) {
      jmsConnections.add(connection);

      return connection;
   }

   protected String getJmsConnectionURIOptions() {
      return "";
   }

   protected String getBrokerQpidJMSConnectionString() {

      try {
         int port = AMQP_PORT;

         String uri = null;

         if (isUseSSL()) {
            if (isUseWebSockets()) {
               uri = "amqpwss://127.0.0.1:" + port;
            } else {
               uri = "amqps://127.0.0.1:" + port;
            }
         } else {
            if (isUseWebSockets()) {
               uri = "amqpws://127.0.0.1:" + port;
            } else {
               uri = "amqp://127.0.0.1:" + port;
            }
         }

         if (!getJmsConnectionURIOptions().isEmpty()) {
            uri = uri + "?" + getJmsConnectionURIOptions();
         }

         return uri;
      } catch (Exception e) {
         throw new RuntimeException();
      }
   }

   protected URI getBrokerQpidJMSConnectionURI() {
      try {
         return new URI(getBrokerQpidJMSConnectionString());
      } catch (Exception e) {
         throw new RuntimeException();
      }
   }

   protected URI getBrokerQpidJMSFailoverConnectionURI() {
      try {
         return new URI("failover:(" + getBrokerQpidJMSConnectionString() + ")");
      } catch (Exception e) {
         throw new RuntimeException();
      }
   }

   protected Connection createConnection() throws JMSException {
      return createConnection(getBrokerQpidJMSConnectionURI(), null, null, null, true);
   }

   protected Connection createFailoverConnection() throws JMSException {
      return createConnection(getBrokerQpidJMSFailoverConnectionURI(), null, null, null, true);
   }

   protected Connection createConnection(boolean start) throws JMSException {
      return createConnection(getBrokerQpidJMSConnectionURI(), null, null, null, start);
   }

   protected Connection createConnection(String clientId) throws JMSException {
      return createConnection(getBrokerQpidJMSConnectionURI(), null, null, clientId, true);
   }

   protected Connection createConnection(String clientId, boolean start) throws JMSException {
      return createConnection(getBrokerQpidJMSConnectionURI(), null, null, clientId, start);
   }

   protected Connection createConnection(String username, String password) throws JMSException {
      return createConnection(getBrokerQpidJMSConnectionURI(), username, password, null, true);
   }

   protected Connection createConnection(String username, String password, String clientId) throws JMSException {
      return createConnection(getBrokerQpidJMSConnectionURI(), username, password, clientId, true);
   }

   protected Connection createConnection(String username, String password, String clientId, boolean start) throws JMSException {
      return createConnection(getBrokerQpidJMSConnectionURI(), username, password, clientId, start);
   }

   protected Connection createConnection(URI remoteURI, String username, String password, String clientId, boolean start) throws JMSException {
      JmsConnectionFactory factory = new JmsConnectionFactory(remoteURI);

      Connection connection = trackJMSConnection(factory.createConnection(username, password));

      connection.setExceptionListener(exception -> exception.printStackTrace());

      if (clientId != null && !clientId.isEmpty()) {
         connection.setClientID(clientId);
      }

      if (start) {
         connection.start();
      }

      return connection;
   }


   protected String getBrokerCoreJMSConnectionString() {

      try {
         int port = AMQP_PORT;

         String uri = null;

         if (isUseSSL()) {
            throw new UnsupportedOperationException("SSL not supported here");
         } else {
            uri = "tcp://127.0.0.1:" + port;
         }

         if (!getJmsConnectionURIOptions().isEmpty()) {
            uri = uri + "?" + getJmsConnectionURIOptions();
         }

         return uri;
      } catch (Exception e) {
         throw new RuntimeException();
      }
   }

   protected Connection createCoreConnection() throws JMSException {
      return createCoreConnection(getBrokerCoreJMSConnectionString(), null, null, null, true);
   }

   protected Connection createCoreConnection(boolean start) throws JMSException {
      return createCoreConnection(getBrokerCoreJMSConnectionString(), null, null, null, start);
   }

   protected Connection createCoreConnection(String username, String password) throws JMSException {
      return createCoreConnection(getBrokerCoreJMSConnectionString(), username, password, null, true);
   }

   private Connection createCoreConnection(String connectionString, String username, String password, String clientId, boolean start) throws JMSException {
      ActiveMQJMSConnectionFactory factory = new ActiveMQJMSConnectionFactory(connectionString);

      Connection connection = trackJMSConnection(factory.createConnection(username, password));

      connection.setExceptionListener(exception -> exception.printStackTrace());

      if (clientId != null && !clientId.isEmpty()) {
         connection.setClientID(clientId);
      }

      if (start) {
         connection.start();
      }

      return connection;
   }

   protected String getBrokerOpenWireJMSConnectionString() {

      try {
         int port = AMQP_PORT;

         String uri = null;

         if (isUseSSL()) {
            throw new UnsupportedOperationException("SSL not supported here");
         } else {
            uri = "tcp://127.0.0.1:" + port;
         }

         if (!getJmsConnectionURIOptions().isEmpty()) {
            uri = uri + "?" + getJmsConnectionURIOptions();
         } else {
            uri = uri + "?wireFormat.cacheEnabled=true";
         }

         return uri;
      } catch (Exception e) {
         throw new RuntimeException();
      }
   }

   protected Connection createOpenWireConnection() throws JMSException {
      return createOpenWireConnection(getBrokerOpenWireJMSConnectionString(), null, null, null, true);
   }

   protected Connection createOpenWireConnection(boolean start) throws JMSException {
      return createOpenWireConnection(getBrokerOpenWireJMSConnectionString(), null, null, null, start);
   }

   protected Connection createOpenWireConnection(String username, String password) throws JMSException {
      return createOpenWireConnection(getBrokerOpenWireJMSConnectionString(), username, password, null, true);
   }

   protected Connection createOpenWireConnection(String connectionString, String username, String password, String clientId, boolean start) throws JMSException {
      return createOpenWireConnection(connectionString, username, password, clientId, start, true);
   }

   protected Connection createOpenWireConnection(String connectionString, String username, String password, String clientId, boolean start, boolean watchAdvisories) throws JMSException {
      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(connectionString);

      if (!watchAdvisories) {
         factory.setWatchTopicAdvisories(false);
      }

      Connection connection = trackJMSConnection(factory.createConnection(username, password));

      connection.setExceptionListener(exception -> exception.printStackTrace());

      if (clientId != null && !clientId.isEmpty()) {
         connection.setClientID(clientId);
      }

      if (start) {
         connection.start();
      }

      return connection;
   }

   interface ConnectionSupplier {
      Connection createConnection() throws JMSException;
   }

   interface SecureConnectionSupplier {
      Connection createConnection(String username, String Password) throws JMSException;
   }
}

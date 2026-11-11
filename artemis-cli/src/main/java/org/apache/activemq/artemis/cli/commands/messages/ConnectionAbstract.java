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
package org.apache.activemq.artemis.cli.commands.messages;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.JMSSecurityException;

import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.cli.factory.ConnectionFactoryClosable;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.qpid.jms.JmsConnectionFactory;
import picocli.CommandLine.Option;

public class ConnectionAbstract extends ConnectionConfigurationAbtract {
   @Option(names = "--clientID", description = "ClientID set on the connection.")
   protected String clientID;

   @Option(names = "--protocol", description = "Protocol used. Valid values are ${COMPLETION-CANDIDATES}", converter = ConnectionProtocol.ProtocolConverter.class)
   protected ConnectionProtocol protocol = ConnectionProtocol.CORE;

   public String getClientID() {
      return clientID;
   }

   public ConnectionAbstract setClientID(String clientID) {
      this.clientID = clientID;
      return this;
   }

   public ConnectionProtocol getProtocol() {
      return protocol;
   }

   public void setProtocol(ConnectionProtocol protocol) {
      this.protocol = protocol;
   }

   public void setProtocol(String protocol) {
      this.protocol = ConnectionProtocol.fromString(protocol);
   }

   protected ConnectionFactoryClosable createConnectionFactory() throws Exception {
      recoverConnectionInformation();
      return createConnectionFactory(brokerURL, user, password, clientID, protocol);
   }

   protected ConnectionFactoryClosable createConnectionFactory(String brokerURL,
                                                       String user,
                                                       String password,
                                                       String clientID,
                                                       ConnectionProtocol protocol) throws Exception {
      if (protocol == ConnectionProtocol.CORE) {
         return new ConnectionFactoryClosable(createCoreConnectionFactory(brokerURL, user, password, clientID));
      } else if (protocol == ConnectionProtocol.AMQP) {
         return new ConnectionFactoryClosable(createAMQPConnectionFactory(brokerURL, user, password, clientID));
      } else {
         throw new IllegalStateException("protocol " + protocol + " not supported");
      }
   }

   private ConnectionFactory createAMQPConnectionFactory(String brokerURL,
                                                         String user,
                                                         String password,
                                                         String clientID) {
      if (brokerURL.startsWith("tcp://")) {
         // replacing tcp:// by amqp://
         brokerURL = "amqp" + brokerURL.substring(3);
      }
      JmsConnectionFactory cf = new JmsConnectionFactory(user, password, brokerURL);
      if (clientID != null) {
         cf.setClientID(clientID);
      }

      try {
         tryConnect(brokerURL, user, password, cf);
         return cf;
      } catch (JMSSecurityException e) {
         // if a security exception will get the user and password through an input
         getActionContext().err.println("Connection failed::" + e.getMessage());
         user = inputUser(user);
         password = inputPassword(password);
         cf = new JmsConnectionFactory(user, password, brokerURL);
         if (clientID != null) {
            cf.setClientID(clientID);
         }
         try {
            tryConnect(brokerURL, user, password, cf);
         } catch (Exception e2) {
         }
         return cf;
      } catch (JMSException e) {
         // if a connection exception will ask for the URL, user and password
         getActionContext().err.println("Connection failed::" + e.getMessage());
         brokerURL = inputBrokerURL(brokerURL);
         user = inputUser(user);
         password = inputPassword(password);
         cf = new JmsConnectionFactory(user, password, brokerURL);
         if (clientID != null) {
            cf.setClientID(clientID);
         }
         try {
            tryConnect(brokerURL, user, password, cf);
         } catch (Exception e2) {
         }
         return cf;
      }
   }

   protected ActiveMQConnectionFactory createCoreConnectionFactory() {
      recoverConnectionInformation();
      return createCoreConnectionFactory(brokerURL, user, password, clientID);
   }

   protected ActiveMQConnectionFactory createCoreConnectionFactory(String brokerURL,
                                                                   String user,
                                                                   String password,
                                                                   String clientID) {
      if (brokerURL.startsWith("amqp://")) {
         // replacing amqp:// by tcp://
         brokerURL = "tcp" + brokerURL.substring(4);
      }

      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory(brokerURL, user, password);
      if (clientID != null) {
         getActionContext().out.println("Consumer:: clientID = " + clientID);
         cf.setClientID(clientID);
      }
      try {
         tryConnect(brokerURL, user, password, cf);
         return cf;
      } catch (JMSSecurityException e) {
         // if a security exception will get the user and password through an input
         if (getActionContext() != null) {
            getActionContext().err.println("Connection failed::" + e.getMessage());
         }
         user = inputUser(user);
         if (!isSilentInput()) {
            password = inputPassword(password);
         }
         cf = new ActiveMQConnectionFactory(brokerURL, user, password);
         if (clientID != null) {
            cf.setClientID(clientID);
         }
         try {
            tryConnect(brokerURL, user, password, cf);
         } catch (Exception e2) {
         }
         return cf;
      } catch (JMSException e) {
         // if a connection exception will ask for the URL, user and password
         if (getActionContext() != null) {
            getActionContext().err.println("Connection failed::" + e.getMessage());
         }
         brokerURL = inputBrokerURL(brokerURL);
         user = inputUser(user);
         if (!isSilentInput()) {
            password = inputPassword(password);
         }
         cf = new ActiveMQConnectionFactory(brokerURL, user, password);
         if (clientID != null) {
            cf.setClientID(clientID);
         }
         try {
            tryConnect(brokerURL, user, password, cf);
         } catch (Exception e2) {
         }
         return cf;
      }
   }

   protected void tryConnect(String brokerURL,
                             String user,
                             String password,
                             ConnectionFactory cf) throws JMSException {
      Connection connection = cf.createConnection();
      connection.close();
      saveConnectionInfo(brokerURL, user, password);
   }

   protected void performCoreManagement(ManagementHelper.MessageAcceptor setup, ManagementHelper.MessageAcceptor ok, ManagementHelper.MessageAcceptor failed) throws Exception {
      try (ActiveMQConnectionFactory factory = createCoreConnectionFactory()) {
         ManagementHelper.doManagement(factory.getServerLocator(), user, password, setup, ok, failed);
      }
   }

   protected void performCoreManagement(String uri, String user, String password, ManagementHelper.MessageAcceptor setup, ManagementHelper.MessageAcceptor ok, ManagementHelper.MessageAcceptor failed) throws Exception {
      try (ActiveMQConnectionFactory factory = createCoreConnectionFactory(uri, user, password, clientID)) {
         ManagementHelper.doManagement(factory.getServerLocator(), user, password, setup, ok, failed);
      }
   }
}
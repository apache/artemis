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
package org.apache.activemq.artemis.mcp.connection;

import javax.jms.Connection;
import javax.jms.JMSException;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.mcp.config.ArtemisMcpConfig;

public final class ArtemisConnectionManager implements AutoCloseable {

   private final ArtemisMcpConfig config;
   private volatile ActiveMQConnectionFactory factory;

   public ArtemisConnectionManager(ArtemisMcpConfig config) {
      this.config = config;
   }

   private ActiveMQConnectionFactory factory() {
      ActiveMQConnectionFactory current = factory;
      if (current == null) {
         synchronized (this) {
            current = factory;
            if (current == null) {
               current = new ActiveMQConnectionFactory(config.brokerUrl());
               factory = current;
            }
         }
      }
      return current;
   }

   public Connection newStartedConnection() throws JMSException {
      Connection connection;
      if (config.user() != null) {
         connection = factory().createConnection(config.user(), config.password());
      } else {
         connection = factory().createConnection();
      }
      try {
         connection.start();
      } catch (JMSException e) {
         try {
            connection.close();
         } catch (JMSException ignored) {
         }
         throw e;
      }
      return connection;
   }

   @Override
   public synchronized void close() {
      if (factory != null) {
         factory.close();
         factory = null;
      }
   }
}

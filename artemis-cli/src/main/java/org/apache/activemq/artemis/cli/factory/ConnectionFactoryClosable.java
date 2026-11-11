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
package org.apache.activemq.artemis.cli.factory;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSContext;
import javax.jms.JMSException;

public class ConnectionFactoryClosable implements AutoCloseable, ConnectionFactory {

   private ConnectionFactory factory;

   public ConnectionFactoryClosable(ConnectionFactory factory) {
      this.factory = factory;
   }

   @Override
   public void close() throws Exception {
      if (factory instanceof AutoCloseable) {
         ((AutoCloseable)factory).close();
      }
   }

   @Override
   public Connection createConnection() throws JMSException {
      return factory.createConnection();
   }

   @Override
   public Connection createConnection(String userName, String password) throws JMSException {
      return factory.createConnection(userName, password);
   }

   @Override
   public JMSContext createContext() {
      return factory.createContext();
   }

   @Override
   public JMSContext createContext(String userName, String password) {
      return factory.createContext(userName, password);
   }

   @Override
   public JMSContext createContext(String userName, String password, int sessionMode) {
      return factory.createContext(userName, password, sessionMode);
   }

   @Override
   public JMSContext createContext(int sessionMode) {
      return factory.createContext(sessionMode);
   }
}

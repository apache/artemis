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
package org.apache.activemq.artemis.api.core.management;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientRequestor;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;

public class ActiveMQManagementProxy implements AutoCloseable {

   private final ServerLocator serverLocator;

   private final ClientSessionFactory sessionFactory;

   private final ClientSession clientSession;


   public ActiveMQManagementProxy(final ClientSession session) {
      serverLocator = null;
      sessionFactory = null;
      clientSession = session;
   }

   public ActiveMQManagementProxy(final ServerLocator locator, final String username, final String password) throws Exception {
      serverLocator = locator;
      sessionFactory = locator.createSessionFactory(username, password);
      clientSession = sessionFactory.createSession(username, password, false, true, true, false, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE).start();
   }

   public <T> T getAttribute(final String resourceName, final String attributeName, final Class<T> attributeClass, final int timeout) throws Exception {
      try (ClientRequestor requestor = new ClientRequestor(clientSession, ActiveMQDefaultConfiguration.getDefaultManagementAddress())) {
         ClientMessage request = clientSession.createMessage(false);

         ManagementHelper.putAttribute(request, resourceName, attributeName);

         ClientMessage reply = requestor.request(request, timeout);

         if (ManagementHelper.hasOperationSucceeded(reply)) {
            return (T)ManagementHelper.getResult(reply, attributeClass);
         } else {
            throw new Exception("Failed to get " + resourceName + "." + attributeName + ". Reason: " + ManagementHelper.getResult(reply, String.class));
         }
      }
   }

   public <T> T invokeOperation(final String resourceName, final String operationName, final Object[] operationParams, final Class<T> operationClass, final int timeout) throws Exception {
      try (ClientRequestor requestor = new ClientRequestor(clientSession, ActiveMQDefaultConfiguration.getDefaultManagementAddress())) {
         ClientMessage request = clientSession.createMessage(false);

         ManagementHelper.putOperationInvocation(request, resourceName, operationName, operationParams);

         ClientMessage reply = requestor.request(request, timeout);

         if (ManagementHelper.hasOperationSucceeded(reply)) {
            return (T)ManagementHelper.getResult(reply, operationClass);
         } else {
            throw new Exception("Failed to invoke " + resourceName + "." + operationName + ". Reason: " + ManagementHelper.getResult(reply, String.class));
         }
      }
   }

   @Override
   public void close() throws Exception {
      clientSession.close();

      if (sessionFactory != null) {
         sessionFactory.close();
      }

      if (serverLocator != null) {
         serverLocator.close();
      }
   }
}

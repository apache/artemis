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
package org.apache.activemq.artemis.tests.integration.management;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientRequestor;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;

public class CoreMessagingProxy {


   private final String resourceName;

   private final ServerLocator locator;


   public CoreMessagingProxy(final ServerLocator locator, final String resourceName) throws Exception {
      this.locator = locator;

      this.resourceName = resourceName;
   }




   public Object retrieveAttributeValue(final String attributeName) {
      return retrieveAttributeValue(attributeName, null);
   }

   public Object retrieveAttributeValue(final String attributeName, final Class desiredType) {
      try (ClientSessionFactory sessionFactory = locator.createSessionFactory();
           ClientSession session = getSession(sessionFactory);
           ClientRequestor requestor = getClientRequestor(session)) {
         ClientMessage m = session.createMessage(false);
         ManagementHelper.putAttribute(m, resourceName, attributeName);
         ClientMessage reply;
         reply = requestor.request(m);
         Object result = ManagementHelper.getResult(reply, desiredType);

         return result;
      } catch (Exception e) {
         throw new IllegalStateException(e);
      }
   }

   public Object invokeOperation(final String operationName, final Object... args) throws Exception {
      return invokeOperation(null, operationName, args);
   }

   public Object invokeOperation(final Class desiredType,
                                 final String operationName,
                                 final Object... args) throws Exception {
      try (ClientSessionFactory sessionFactory = locator.createSessionFactory();
           ClientSession session = getSession(sessionFactory);
           ClientRequestor requestor = getClientRequestor(session)) {
         ClientMessage m = session.createMessage(false);
         ManagementHelper.putOperationInvocation(m, resourceName, operationName, args);
         ClientMessage reply = requestor.request(m);
         if (reply != null) {
            if (ManagementHelper.hasOperationSucceeded(reply)) {
               return ManagementHelper.getResult(reply, desiredType);
            } else {
               throw new Exception((String) ManagementHelper.getResult(reply));
            }
         } else {
            return null;
         }
      }
   }


   private ClientSession getSession(ClientSessionFactory sessionFactory) throws ActiveMQException {
      ClientSession session = sessionFactory.createSession(false, true, true);
      session.start();
      return session;
   }

   private ClientRequestor getClientRequestor(ClientSession session) throws Exception {
      return new ClientRequestor(session, ActiveMQDefaultConfiguration.getDefaultManagementAddress());
   }


}

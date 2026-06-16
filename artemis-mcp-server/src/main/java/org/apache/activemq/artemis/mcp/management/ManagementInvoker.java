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
package org.apache.activemq.artemis.mcp.management;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueRequestor;
import javax.jms.QueueSession;
import javax.jms.Session;

import org.apache.activemq.artemis.api.jms.management.JMSManagementHelper;
import org.apache.activemq.artemis.mcp.connection.ArtemisConnectionManager;

public final class ManagementInvoker {

   public static final String MANAGEMENT_ADDRESS = "activemq.management";

   private final ArtemisConnectionManager connectionManager;

   public ManagementInvoker(ArtemisConnectionManager connectionManager) {
      this.connectionManager = connectionManager;
   }

   public Object attribute(String resourceName, String attribute) throws Exception {
      return request(message -> JMSManagementHelper.putAttribute(message, resourceName, attribute),
         resourceName + "." + attribute);
   }

   public Object operation(String resourceName, String operationName, Object... parameters) throws Exception {
      return request(message -> JMSManagementHelper.putOperationInvocation(message, resourceName, operationName, parameters),
         resourceName + "#" + operationName);
   }

   @FunctionalInterface
   private interface Preparer {
      void prepare(Message message) throws Exception;
   }

   private Object request(Preparer preparer, String label) throws Exception {
      try (Connection connection = connectionManager.newStartedConnection()) {
         QueueSession session = ((QueueConnection) connection).createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue managementQueue = session.createQueue(MANAGEMENT_ADDRESS);
         QueueRequestor requestor = new QueueRequestor(session, managementQueue);
         try {
            Message request = session.createMessage();
            preparer.prepare(request);
            Message reply = requestor.request(request);
            if (!JMSManagementHelper.hasOperationSucceeded(reply)) {
               throw new JMSException("Management call failed for " + label + ": "
                  + JMSManagementHelper.getResult(reply));
            }
            return JMSManagementHelper.getResult(reply);
         } finally {
            requestor.close();
         }
      }
   }
}

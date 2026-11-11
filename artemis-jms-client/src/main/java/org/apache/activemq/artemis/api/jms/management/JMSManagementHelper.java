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
package org.apache.activemq.artemis.api.jms.management;

import javax.jms.JMSException;
import javax.jms.Message;

import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.jms.client.ActiveMQMessage;

/**
 * Helper class to use JMS messages to manage Apache Artemis server resources.
 */
public class JMSManagementHelper {

   private static ClientMessage getCoreMessage(final Message jmsMessage) {
      if (jmsMessage instanceof ActiveMQMessage == false) {
         throw new IllegalArgumentException("Cannot send a foreign message as a management message " + jmsMessage.getClass().getName());
      }

      return ((ActiveMQMessage) jmsMessage).getCoreMessage();
   }

   /**
    * Stores a resource attribute in a JMS message to retrieve the value from the server resource.
    *
    * @param message      JMS message
    * @param resourceName the name of the resource
    * @param attribute    the name of the attribute
    * @throws JMSException if an exception occurs while putting the information in the message
    * @see org.apache.activemq.artemis.api.core.management.ResourceNames
    */
   public static void putAttribute(final Message message,
                                   final String resourceName,
                                   final String attribute) throws JMSException {
      ManagementHelper.putAttribute(JMSManagementHelper.getCoreMessage(message), resourceName, attribute);
   }

   /**
    * Stores an operation invocation in a JMS message to invoke the corresponding operation the value from the server
    * resource.
    *
    * @param message       JMS message
    * @param resourceName  the name of the resource
    * @param operationName the name of the operation to invoke on the resource
    * @throws JMSException if an exception occurs while putting the information in the message
    * @see org.apache.activemq.artemis.api.core.management.ResourceNames
    */
   public static void putOperationInvocation(final Message message,
                                             final String resourceName,
                                             final String operationName) throws JMSException {
      try {
         ManagementHelper.putOperationInvocation(JMSManagementHelper.getCoreMessage(message), resourceName, operationName);
      } catch (Exception e) {
         throw JMSManagementHelper.convertFromException(e);
      }
   }

   private static JMSException convertFromException(final Exception e) {
      JMSException jmse = new JMSException(e.getMessage());

      jmse.initCause(e);

      return jmse;
   }

   /**
    * Stores an operation invocation in a JMS message to invoke the corresponding operation the value from the server
    * resource.
    *
    * @param message       JMS message
    * @param resourceName  the name of the server resource
    * @param operationName the name of the operation to invoke on the server resource
    * @param parameters    the parameters to use to invoke the server resource
    * @throws JMSException if an exception occurs while putting the information in the message
    * @see org.apache.activemq.artemis.api.core.management.ResourceNames
    */
   public static void putOperationInvocation(final Message message,
                                             final String resourceName,
                                             final String operationName,
                                             final Object... parameters) throws JMSException {
      try {
         ManagementHelper.putOperationInvocation(JMSManagementHelper.getCoreMessage(message), resourceName, operationName, parameters);
      } catch (Exception e) {
         throw JMSManagementHelper.convertFromException(e);
      }
   }

   /**
    * {@return whether the JMS message corresponds to the result of a management operation invocation.}
    */
   public static boolean isOperationResult(final Message message) throws JMSException {
      return ManagementHelper.isOperationResult(JMSManagementHelper.getCoreMessage(message));
   }

   /**
    * {@return whether the JMS message corresponds to the result of a management attribute value.}
    */
   public static boolean isAttributesResult(final Message message) throws JMSException {
      return ManagementHelper.isAttributesResult(JMSManagementHelper.getCoreMessage(message));
   }

   /**
    * {@return whether the invocation of the management operation on the server resource succeeded.}
    */
   public static boolean hasOperationSucceeded(final Message message) throws JMSException {
      return ManagementHelper.hasOperationSucceeded(JMSManagementHelper.getCoreMessage(message));
   }

   /**
    * {@return the result of an operation invocation or an attribute value; if an error occurred on the server {@link
    * #hasOperationSucceeded(Message)} will return {@code false} and the result will be a {@code String} corresponding
    * to the server exception}
    */
   public static Object[] getResults(final Message message) throws Exception {
      return ManagementHelper.getResults(JMSManagementHelper.getCoreMessage(message));
   }

   /**
    * {@return the result of an operation invocation or an attribute value; if an error occurred on the server {@link
    * #hasOperationSucceeded(Message)} will return {@code false} and the result will be a {@code String} corresponding
    * to the server exception}
    */
   public static Object getResult(final Message message) throws Exception {
      return getResult(message, null);
   }

   /**
    * {@return the result of an operation invocation or an attribute value; if an error occurred on the server {@link
    * #hasOperationSucceeded(Message)} will return {@code false} and the result will be a {@code String} corresponding
    * to the server exception}
    */
   public static Object getResult(final Message message, Class desiredType) throws Exception {
      return ManagementHelper.getResult(JMSManagementHelper.getCoreMessage(message), desiredType);
   }

   private JMSManagementHelper() {
      // Utility class
   }
}

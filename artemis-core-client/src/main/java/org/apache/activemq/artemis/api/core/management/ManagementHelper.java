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
package org.apache.activemq.artemis.api.core.management;

import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientRequestor;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorImpl;
import org.apache.activemq.artemis.json.JsonArray;

import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;

/**
 * Helper class to use Apache Artemis Core messages to manage server resources.
 */
public final class ManagementHelper {

   public static final SimpleString HDR_RESOURCE_NAME = SimpleString.of("_AMQ_ResourceName");

   public static final SimpleString HDR_ATTRIBUTE = SimpleString.of("_AMQ_Attribute");

   public static final SimpleString HDR_OPERATION_NAME = SimpleString.of("_AMQ_OperationName");

   public static final SimpleString HDR_OPERATION_SUCCEEDED = SimpleString.of("_AMQ_OperationSucceeded");

   public static final SimpleString HDR_NOTIFICATION_TYPE = SimpleString.of("_AMQ_NotifType");

   public static final SimpleString HDR_NOTIFICATION_TIMESTAMP = SimpleString.of("_AMQ_NotifTimestamp");

   public static final SimpleString HDR_ROUTING_NAME = SimpleString.of("_AMQ_RoutingName");

   public static final SimpleString HDR_CLUSTER_NAME = SimpleString.of("_AMQ_ClusterName");

   public static final SimpleString HDR_ADDRESS = SimpleString.of("_AMQ_Address");

   public static final SimpleString HDR_ROUTING_TYPE = SimpleString.of("_AMQ_Routing_Type");

   public static final SimpleString HDR_BINDING_ID = SimpleString.of("_AMQ_Binding_ID");

   public static final SimpleString HDR_BINDING_TYPE = SimpleString.of("_AMQ_Binding_Type");

   public static final SimpleString HDR_FILTERSTRING = SimpleString.of("_AMQ_FilterString");

   public static final SimpleString HDR_DISTANCE = SimpleString.of("_AMQ_Distance");

   public static final SimpleString HDR_CONSUMER_COUNT = SimpleString.of("_AMQ_ConsumerCount");

   public static final SimpleString HDR_USER = SimpleString.of("_AMQ_User");

   public static final SimpleString HDR_VALIDATED_USER = SimpleString.of("_AMQ_ValidatedUser");

   public static final SimpleString HDR_CERT_SUBJECT_DN = SimpleString.of("_AMQ_CertSubjectDN");

   public static final SimpleString HDR_CERT_UPN = SimpleString.of("_AMQ_CertUPN");

   public static final SimpleString HDR_CHECK_TYPE = SimpleString.of("_AMQ_CheckType");

   public static final SimpleString HDR_SESSION_NAME = SimpleString.of("_AMQ_SessionName");

   public static final SimpleString HDR_REMOTE_ADDRESS = SimpleString.of("_AMQ_RemoteAddress");

   public static final SimpleString HDR_PROPOSAL_GROUP_ID = SimpleString.of("_JBM_ProposalGroupId");

   public static final SimpleString HDR_PROPOSAL_VALUE = SimpleString.of("_JBM_ProposalValue");

   public static final SimpleString HDR_PROPOSAL_ALT_VALUE = SimpleString.of("_JBM_ProposalAltValue");

   public static final SimpleString HDR_CONSUMER_NAME = SimpleString.of("_AMQ_ConsumerName");

   public static final SimpleString HDR_CONNECTION_NAME = SimpleString.of("_AMQ_ConnectionName");

   public static final SimpleString HDR_MESSAGE_ID = SimpleString.of("_AMQ_Message_ID");

   public static final SimpleString HDR_PROTOCOL_NAME = SimpleString.of("_AMQ_Protocol_Name");

   public static final SimpleString HDR_CLIENT_ID = SimpleString.of("_AMQ_Client_ID");

   // Lambda declaration for management function. Pretty much same thing as java.util.function.Consumer but with an exception in the declaration that was needed.
   public interface MessageAcceptor {
      void accept(ClientMessage message) throws Exception;
   }

   public static void doManagement(String uri, String user, String password, MessageAcceptor setup, MessageAcceptor ok, MessageAcceptor failed) throws Exception {
      try (ServerLocator locator = ServerLocatorImpl.newLocator(uri)) {
         doManagement(locator, user, password, setup, ok, failed);
      }
   }

   public static void doManagement(ServerLocator locator, String user, String password, MessageAcceptor setup, MessageAcceptor ok, MessageAcceptor failed) throws Exception {
      try (ClientSessionFactory sessionFactory = locator.createSessionFactory();
           ClientSession session = sessionFactory.createSession(user, password, false, true, true, false, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE)) {
         doManagement(session, setup, ok, failed);
      }
   }

   /**
    * Utility function to reuse a ClientSessionConnection and perform a single management operation via core.
    */
   public static void doManagement(ClientSession session, MessageAcceptor setup, MessageAcceptor ok, MessageAcceptor failed) throws Exception {
      session.start();
      try (ClientRequestor requestor = new ClientRequestor(session, "activemq.management")) {
         ClientMessage message = session.createMessage(false);

         setup.accept(message);

         ClientMessage reply = requestor.request(message);

         if (ManagementHelper.hasOperationSucceeded(reply)) {
            if (ok != null) {
               ok.accept(reply);
            }
         } else {
            if (failed != null) {
               failed.accept(reply);
            }
         }
      }
   }

   /**
    * Stores a resource attribute in a message to retrieve the value from the server resource.
    *
    * @param message      message
    * @param resourceName the name of the resource
    * @param attribute    the name of the attribute
    * @see ResourceNames
    */
   public static void putAttribute(final ICoreMessage message, final String resourceName, final String attribute) {
      message.putStringProperty(ManagementHelper.HDR_RESOURCE_NAME, SimpleString.of(resourceName));
      message.putStringProperty(ManagementHelper.HDR_ATTRIBUTE, SimpleString.of(attribute));
   }

   /**
    * Stores an operation invocation in a message to invoke the corresponding operation the value from the server
    * resource.
    *
    * @param message       message
    * @param resourceName  the name of the resource
    * @param operationName the name of the operation to invoke on the resource
    * @see ResourceNames
    */
   public static void putOperationInvocation(final ICoreMessage message,
                                             final String resourceName,
                                             final String operationName) throws Exception {
      ManagementHelper.putOperationInvocation(message, resourceName, operationName, (Object[]) null);
   }

   /**
    * Stores an operation invocation in a  message to invoke the corresponding operation the value from the server
    * resource.
    *
    * @param message       message
    * @param resourceName  the name of the server resource
    * @param operationName the name of the operation to invoke on the server resource
    * @param parameters    the parameters to use to invoke the server resource
    * @see ResourceNames
    */
   public static void putOperationInvocation(final ICoreMessage message,
                                             final String resourceName,
                                             final String operationName,
                                             final Object... parameters) throws Exception {
      // store the name of the operation in the headers
      message.putStringProperty(ManagementHelper.HDR_RESOURCE_NAME, SimpleString.of(resourceName));
      message.putStringProperty(ManagementHelper.HDR_OPERATION_NAME, SimpleString.of(operationName));

      // and the params go in the body, since might be too large for header

      String paramString;

      if (parameters != null) {
         JsonArray jsonArray = JsonUtil.toJSONArray(parameters);

         paramString = jsonArray.toString();
      } else {
         paramString = null;
      }

      message.getBodyBuffer().writeNullableSimpleString(SimpleString.of(paramString));
   }

   /**
    * Used by Apache Artemis management service.
    */
   public static Object[] retrieveOperationParameters(final Message message) throws Exception {
      SimpleString sstring = message.toCore().getReadOnlyBodyBuffer().readNullableSimpleString();
      String jsonString = (sstring == null) ? null : sstring.toString();

      if (jsonString != null) {
         JsonArray jsonArray = JsonUtil.readJsonArray(jsonString);

         return JsonUtil.fromJsonArray(jsonArray);
      } else {
         return null;
      }
   }

   /**
    * {@return whether the JMS message corresponds to the result of a management operation invocation}
    */
   public static boolean isOperationResult(final Message message) {
      return message.containsProperty(ManagementHelper.HDR_OPERATION_SUCCEEDED);
   }

   /**
    * {@return whether the JMS message corresponds to the result of a management attribute value}
    */
   public static boolean isAttributesResult(final Message message) {
      return !ManagementHelper.isOperationResult(message);
   }

   /**
    * Used by Apache Artemis management service.
    */
   public static void storeResult(final CoreMessage message, final Object result) throws Exception {
      String resultString;

      if (result != null) {
         // Result is stored in body, also encoded as JSON array of length 1

         JsonArray jsonArray = JsonUtil.toJSONArray(new Object[]{result});

         resultString = jsonArray.toString();
      } else {
         resultString = null;
      }

      message.getBodyBuffer().writeNullableSimpleString(SimpleString.of(resultString));
   }

   /**
    * {@return the result of an operation invocation or an attribute value; if an error occurred on the server {@link
    * #hasOperationSucceeded(Message)} will return {@code false} and the result will be a {@code String} corresponding
    * to the server exception}
    */
   public static Object[] getResults(final ICoreMessage message) throws Exception {
      SimpleString sstring = message.getReadOnlyBodyBuffer().readNullableSimpleString();
      String jsonString = (sstring == null) ? null : sstring.toString();

      if (jsonString != null) {
         JsonArray jsonArray = JsonUtil.readJsonArray(jsonString);
         return JsonUtil.fromJsonArray(jsonArray);
      } else {
         return null;
      }
   }

   /**
    * {@return the result of an operation invocation or an attribute value; if an error occurred on the server {@link
    * #hasOperationSucceeded(Message)} will return {@code false} and the result will be a {@code String} corresponding
    * to the server exception}
    */
   public static Object getResult(final ICoreMessage message) throws Exception {
      return getResult(message, null);
   }

   /**
    * {@return the result of an operation invocation or an attribute value; if an error occurred on the server {@link
    * #hasOperationSucceeded(Message)} will return {@code false} and the result will be a {@code String} corresponding
    * to the server exception}
    */
   public static Object getResult(final ICoreMessage message, Class desiredType) throws Exception {
      Object[] res = ManagementHelper.getResults(message);

      if (res != null) {
         return JsonUtil.convertJsonValue(res[0], desiredType);
      } else {
         return null;
      }
   }

   /**
    * {@return whether the invocation of the management operation on the server resource succeeded}
    */
   public static boolean hasOperationSucceeded(final Message message) {
      if (message == null) {
         return false;
      }
      if (!ManagementHelper.isOperationResult(message)) {
         return false;
      }
      if (message.containsProperty(ManagementHelper.HDR_OPERATION_SUCCEEDED)) {
         return message.getBooleanProperty(ManagementHelper.HDR_OPERATION_SUCCEEDED);
      }
      return false;
   }

   private ManagementHelper() {
   }
}

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

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorImpl;
import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class provides a simple proxy for management operations
 */
public class SimpleManagement implements AutoCloseable {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String SIMPLE_OPTIONS  = "{\"field\":\"\",\"value\":\"\",\"operation\":\"\"}";

   String uri, user, password;

   ServerLocator locator;
   ClientSessionFactory sessionFactory;
   ClientSession session;


   public SimpleManagement(String uri, String user, String password) {
      this.uri = uri;
      this.user = user;
      this.password = password;
   }

   public SimpleManagement open() throws Exception {
      if (session == null) {
         locator = ServerLocatorImpl.newLocator(uri);
         sessionFactory = locator.createSessionFactory();
         session = sessionFactory.createSession(user, password, false, true, true, false, ActiveMQClient.DEFAULT_ACK_BATCH_SIZE);
      }
      return this;
   }

   public String getUri() {
      return uri;
   }

   /**
    * it will inject the session used by SimpleManagement.
    * beware that if you call {@link #close()} the session passed here will be closed as well. */
   public void setSession(ClientSession session) {
      this.session = session;
   }

   @Override
   public void close() throws Exception {
      if (session != null) {
         session.close();
         session = null;
      }

      if (sessionFactory != null) {
         sessionFactory.close();
         sessionFactory = null;
      }

      if (locator != null) {
         locator.close();
         locator = null;
      }
   }

   public long getCurrentTimeMillis() throws Exception {
      return simpleManagementLong("broker", "getCurrentTimeMillis");
   }

   public boolean isReplicaSync() throws Exception {
      return simpleManagementBoolean("broker", "isReplicaSync");
   }

   public void rebuildPageCounters() throws Exception {
      simpleManagementVoid("broker", "rebuildPageCounters");
   }

   /**
    * Simple helper for management returning a string.
    */
   public String simpleManagement(String resource, String method, Object... parameters) throws Exception {
      AtomicReference<String> responseString = new AtomicReference<>();
      doManagement((m) -> setupCall(m, resource, method, parameters), m -> setStringResult(m, responseString), SimpleManagement::failed);
      return responseString.get();
   }

   /**
    * Simple helper for management returning a long.
    */
   public long simpleManagementLong(String resource, String method, Object... parameters) throws Exception {
      AtomicLong responseLong = new AtomicLong();
      doManagement((m) -> setupCall(m, resource, method, parameters), m -> setLongResult(m, responseLong), SimpleManagement::failed);
      return responseLong.get();
   }

   /**
    * Simple helper for management returning a long.
    */
   public boolean simpleManagementBoolean(String resource, String method, Object... parameters) throws Exception {
      AtomicBoolean responseBoolean = new AtomicBoolean();
      doManagement((m) -> setupCall(m, resource, method, parameters), m -> setBooleanResult(m, responseBoolean), SimpleManagement::failed);
      return responseBoolean.get();
   }

   /**
    * Simple helper for management void calls.
    */
   public void simpleManagementVoid(String resource, String method, Object... parameters) throws Exception {
      doManagement((m) -> setupCall(m, resource, method, parameters), null, SimpleManagement::failed);
   }

   public int simpleManagementInt(String resource, String method, Object... parameters) throws Exception {
      AtomicInteger responseInt = new AtomicInteger();
      doManagement((m) -> setupCall(m, resource, method, parameters), m -> setIntResult(m, responseInt), SimpleManagement::failed);
      return responseInt.get();
   }

   public long getMessageCountOnQueue(String queueName) throws Exception {
      return simpleManagementLong(ResourceNames.QUEUE + queueName, "getMessageCount");
   }

   public long getMessageAddedOnQueue(String queueName) throws Exception {
      return simpleManagementLong(ResourceNames.QUEUE + queueName, "getMessagesAdded");
   }

   public int getDeliveringCountOnQueue(String queueName) throws Exception {
      return simpleManagementInt(ResourceNames.QUEUE + queueName, "getDeliveringCount");
   }

   public int getNumberOfConsumersOnQueue(String queueName) throws Exception {
      String responseString = simpleManagement(ResourceNames.QUEUE + queueName, "listConsumersAsJSON");

      JsonArray consumersAsJSON = JsonUtil.readJsonArray(responseString);

      return consumersAsJSON.size();
   }

   public JsonArray listLockCoordinators() throws Exception {
      return JsonUtil.readJsonArray(simpleManagement("broker", "listLockCoordinatorsAsJSON"));
   }

   public long getMessagesAddedOnQueue(String queueName) throws Exception {
      return simpleManagementLong(ResourceNames.QUEUE + queueName, "getMessagesAdded");
   }

   public Map<String, Long> getQueueCounts(int maxRows) throws Exception {
      String responseString = simpleManagement("broker", "listQueues", SIMPLE_OPTIONS, 1, maxRows);

      JsonObject queuesAsJsonObject = JsonUtil.readJsonObject(responseString);
      JsonArray array = queuesAsJsonObject.getJsonArray("data");

      Map<String, Long> queues = new HashMap<>();

      for (int i = 0; i < array.size(); i++) {
         JsonObject object = array.getJsonObject(i);
         String name = object.getString("name");
         String messageCount = object.getString("messageCount");
         queues.put(name, Long.parseLong(messageCount));
      }

      return queues;
   }

   public String getNodeID() throws Exception {
      return simpleManagement("broker", "getNodeID");
   }

   public JsonArray listNetworkTopology() throws Exception {
      String result = simpleManagement("broker", "listNetworkTopology");
      return JsonUtil.readJsonArray(result);
   }

   protected static void failed(ClientMessage message) throws Exception {
      final String result = (String) ManagementHelper.getResult(message, String.class);
      logger.warn("simple management operation failed:: {}", result);
      throw new Exception("Failed " + result);
   }

   protected static void setupCall(ClientMessage m, String resource, String methodName, Object... parameters) throws Exception {
      if (logger.isDebugEnabled()) {
         logger.debug("Setting up call {}::{}::{}", resource, methodName, parameters);
      }
      ManagementHelper.putOperationInvocation(m, resource, methodName, parameters);
   }

   protected static void setStringResult(ClientMessage m, AtomicReference<String> result) throws Exception {
      String resultString = (String)ManagementHelper.getResult(m, String.class);
      logger.debug("management result:: {}", resultString);
      result.set(resultString);
   }

   protected static void setLongResult(ClientMessage m, AtomicLong result) throws Exception {
      long resultLong = (long)ManagementHelper.getResult(m, Long.class);
      logger.debug("management result:: {}", resultLong);
      result.set(resultLong);
   }

   protected static void setBooleanResult(ClientMessage m, AtomicBoolean result) throws Exception {
      boolean resultBoolean = (boolean)ManagementHelper.getResult(m, Boolean.class);
      logger.debug("management result:: {}", resultBoolean);
      result.set(resultBoolean);
   }

   protected static void setIntResult(ClientMessage m, AtomicInteger result) throws Exception {
      int resultInt = (int)ManagementHelper.getResult(m, Integer.class);
      logger.debug("management result:: {}", resultInt);
      result.set(resultInt);
   }

   protected void doManagement(ManagementHelper.MessageAcceptor setup, ManagementHelper.MessageAcceptor ok, ManagementHelper.MessageAcceptor failed) throws Exception {
      if (session != null) {
         ManagementHelper.doManagement(session, setup, ok, failed);
      } else {
         ManagementHelper.doManagement(uri, user, password, setup, ok, failed);
      }
   }
}
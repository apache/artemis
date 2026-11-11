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
package org.apache.activemq.artemis.core.server.routing.targets;

import org.apache.activemq.artemis.api.core.TransportConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MockTarget extends AbstractTarget {
   private boolean local = false;

   private boolean connected = false;

   private boolean connectable = false;

   private boolean ready = false;

   private Map<String, Object> attributeValues = new HashMap<>();

   private Map<String, Object> operationReturnValues = new HashMap<>();


   @Override
   public boolean isLocal() {
      return false;
   }

   public MockTarget setLocal(boolean local) {
      this.local = local;
      return this;
   }

   @Override
   public boolean isConnected() {
      return connected;
   }

   public boolean isConnectable() {
      return connectable;
   }

   public MockTarget setConnected(boolean connected) {
      this.connected = connected;
      return this;
   }

   public MockTarget setConnectable(boolean connectable) {
      this.connectable = connectable;
      return this;
   }

   public boolean isReady() {
      return ready;
   }

   public MockTarget setReady(boolean ready) {
      this.ready = ready;
      return this;
   }

   public Map<String, Object> getAttributeValues() {
      return attributeValues;
   }

   public void setAttributeValues(Map<String, Object> attributeValues) {
      this.attributeValues = attributeValues;
   }

   public Map<String, Object> getOperationReturnValues() {
      return operationReturnValues;
   }

   public void setOperationReturnValues(Map<String, Object> operationReturnValues) {
      this.operationReturnValues = operationReturnValues;
   }

   public MockTarget() {
      this(new TransportConfiguration(), UUID.randomUUID().toString());
   }

   public MockTarget(TransportConfiguration connector, String nodeID) {
      super(connector, nodeID);
   }

   @Override
   public void connect() throws Exception {
      if (!connectable) {
         throw new IllegalStateException("Target not connectable");
      }

      if (getNodeID() == null) {
         setNodeID(UUID.randomUUID().toString());
      }

      connected = true;

      fireConnectedEvent();
   }

   @Override
   public void disconnect() throws Exception {
      connected = false;

      fireDisconnectedEvent();
   }

   @Override
   public boolean checkReadiness() {
      return connected && ready;
   }

   @Override
   public <T> T getAttribute(String resourceName, String attributeName, Class<T> attributeClass, int timeout) throws Exception {
      checkConnection();

      return (T)attributeValues.get(resourceName + attributeName);
   }

   @Override
   public <T> T invokeOperation(String resourceName, String operationName, Object[] operationParams, Class<T> operationClass, int timeout) throws Exception {
      checkConnection();

      return (T)operationReturnValues.get(resourceName + operationName);
   }

   public void setAttributeValue(String resourceName, String attributeName, Object value) {
      attributeValues.put(resourceName + attributeName, value);
   }

   public void setOperationReturnValue(String resourceName, String attributeName, Object value) {
      operationReturnValues.put(resourceName + attributeName, value);
   }

   private void checkConnection() {
      if (!connected) {
         throw new IllegalStateException("Target not connected");
      }
   }
}

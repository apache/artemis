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
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.management.ManagementService;

public class LocalTarget extends AbstractTarget {
   private final ActiveMQServer server;
   private final ManagementService managementService;

   public LocalTarget(TransportConfiguration connector, ActiveMQServer server) {
      super(connector, null);

      this.server = server;
      this.managementService = server.getManagementService();
   }

   @Override
   public boolean isLocal() {
      return true;
   }

   @Override
   public boolean isConnected() {
      return true;
   }

   @Override
   public void connect() throws Exception {
      if (getNodeID() == null) {
         setNodeID(server.getNodeID().toString());
      }
   }

   @Override
   public void disconnect() throws Exception {

   }

   @Override
   public boolean checkReadiness() {
      return true;
   }

   @Override
   public <T> T getAttribute(String resourceName, String attributeName, Class<T> attributeClass, int timeout) throws Exception {
      return (T)managementService.getAttribute(resourceName, attributeName, null);
   }

   @Override
   public <T> T invokeOperation(String resourceName, String operationName, Object[] operationParams, Class<T> operationClass, int timeout) throws Exception {
      return (T)managementService.invokeOperation(resourceName, operationName, operationParams, null);
   }
}

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
package org.apache.activemq.artemis.mcp.admin;

import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.mcp.connection.ArtemisConnectionManager;
import org.apache.activemq.artemis.mcp.management.ManagementInvoker;

public final class ArtemisAdminClient {

   private final ManagementInvoker invoker;

   public ArtemisAdminClient(ArtemisConnectionManager connectionManager) {
      this.invoker = new ManagementInvoker(connectionManager);
   }

   public Object createQueue(String name, String address, String routingType, boolean durable) throws Exception {
      String targetAddress = (address == null || address.isBlank()) ? name : address;
      return invoker.operation(ResourceNames.BROKER, "createQueue",
         targetAddress, routingType, name, null, durable, -1, false, true);
   }

   public void deleteQueue(String name) throws Exception {
      invoker.operation(ResourceNames.BROKER, "destroyQueue", name);
   }

   public Object createAddress(String name, String routingType) throws Exception {
      return invoker.operation(ResourceNames.BROKER, "createAddress", name, routingType);
   }

   public void deleteAddress(String name) throws Exception {
      invoker.operation(ResourceNames.BROKER, "deleteAddress", name);
   }

   public int purgeQueue(String queueName) throws Exception {
      return toInt(invoker.operation(ResourceNames.QUEUE + queueName, "removeAllMessages"));
   }

   public int deleteMessages(String queueName, String filter) throws Exception {
      return toInt(invoker.operation(ResourceNames.QUEUE + queueName, "removeMessages", filter));
   }

   public int moveMessages(String queueName, String filter, String targetQueue) throws Exception {
      return toInt(invoker.operation(ResourceNames.QUEUE + queueName, "moveMessages", filter, targetQueue));
   }

   public int retryMessages(String queueName) throws Exception {
      return toInt(invoker.operation(ResourceNames.QUEUE + queueName, "retryMessages"));
   }

   private static int toInt(Object value) {
      return value instanceof Number number ? number.intValue() : 0;
   }
}

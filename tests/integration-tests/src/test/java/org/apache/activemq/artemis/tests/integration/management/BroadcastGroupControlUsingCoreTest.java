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

import org.apache.activemq.artemis.api.core.management.BroadcastGroupControl;
import org.apache.activemq.artemis.api.core.management.ResourceNames;

public class BroadcastGroupControlUsingCoreTest extends BroadcastGroupControlTest {

   @Override
   protected BroadcastGroupControl createManagementControl(final String name) throws Exception {
      return new BroadcastGroupControl() {
         private final CoreMessagingProxy proxy = new CoreMessagingProxy(addServerLocator(createInVMNonHALocator()), ResourceNames.BROADCAST_GROUP + name);

         @Override
         public long getBroadcastPeriod() {
            return (Long) proxy.retrieveAttributeValue("broadcastPeriod", Long.class);
         }

         @Override
         public Object[] getConnectorPairs() {
            return (Object[]) proxy.retrieveAttributeValue("connectorPairs");
         }

         @Override
         public String getConnectorPairsAsJSON() {
            return (String) proxy.retrieveAttributeValue("connectorPairsAsJSON");
         }

         @Override
         public String getGroupAddress() {
            return (String) proxy.retrieveAttributeValue("groupAddress");
         }

         @Override
         public int getGroupPort() {
            return (Integer) proxy.retrieveAttributeValue("groupPort", Integer.class);
         }

         @Override
         public int getLocalBindPort() {
            return (Integer) proxy.retrieveAttributeValue("localBindPort", Integer.class);
         }

         @Override
         public String getName() {
            return (String) proxy.retrieveAttributeValue("name");
         }

         @Override
         public boolean isStarted() {
            return (Boolean) proxy.retrieveAttributeValue("started");
         }

         @Override
         public void start() throws Exception {
            proxy.invokeOperation("start");
         }

         @Override
         public void stop() throws Exception {
            proxy.invokeOperation("stop");
         }
      };
   }
}

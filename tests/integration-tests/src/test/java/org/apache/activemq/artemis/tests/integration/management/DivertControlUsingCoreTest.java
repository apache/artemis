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

import java.util.Map;

import org.apache.activemq.artemis.api.core.management.DivertControl;
import org.apache.activemq.artemis.api.core.management.ResourceNames;

public class DivertControlUsingCoreTest extends DivertControlTest {


   // DivertControlTest overrides --------------------------------

   @Override
   protected DivertControl createDivertManagementControl(final String name, final String address) throws Exception {
      return new DivertControl() {
         private final CoreMessagingProxy proxy = new CoreMessagingProxy(addServerLocator(createInVMNonHALocator()), ResourceNames.DIVERT + name);

         @Override
         public String getAddress() {
            return (String) proxy.retrieveAttributeValue("address");
         }

         @Override
         public String getFilter() {
            return (String) proxy.retrieveAttributeValue("filter");
         }

         @Override
         public String getForwardingAddress() {
            return (String) proxy.retrieveAttributeValue("forwardingAddress");
         }

         @Override
         public String getRoutingName() {
            return (String) proxy.retrieveAttributeValue("routingName");
         }

         @Override
         public String getTransformerClassName() {
            return (String) proxy.retrieveAttributeValue("transformerClassName");
         }

         @Override
         public String getTransformerPropertiesAsJSON() {
            return (String) proxy.retrieveAttributeValue("transformerPropertiesAsJSON");
         }

         @Override
         public Map<String, String> getTransformerProperties() {
            return (Map<String, String>) proxy.retrieveAttributeValue("transformerProperties");
         }

         @Override
         public String getRoutingType() {
            return (String) proxy.retrieveAttributeValue("routingType");
         }

         @Override
         public String getUniqueName() {
            return (String) proxy.retrieveAttributeValue("uniqueName");
         }

         @Override
         public boolean isExclusive() {
            return (Boolean) proxy.retrieveAttributeValue("exclusive");
         }

         @Override
         public boolean isRetroactiveResource() {
            return (Boolean) proxy.retrieveAttributeValue("retroactiveResource");
         }

      };
   }


}

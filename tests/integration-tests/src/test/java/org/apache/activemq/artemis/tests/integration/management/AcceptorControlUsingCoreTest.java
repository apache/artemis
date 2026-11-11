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

import org.apache.activemq.artemis.api.core.management.AcceptorControl;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.junit.jupiter.api.Test;

public class AcceptorControlUsingCoreTest extends AcceptorControlTest {


   // AcceptorControlTest overrides --------------------------------

   @Override
   protected AcceptorControl createManagementControl(final String name) throws Exception {
      return new AcceptorControl() {

         private final CoreMessagingProxy proxy = new CoreMessagingProxy(addServerLocator(createInVMNonHALocator()), ResourceNames.ACCEPTOR + name);

         @Override
         public String getFactoryClassName() {
            return (String) proxy.retrieveAttributeValue("factoryClassName");
         }

         @Override
         public String getName() {
            return (String) proxy.retrieveAttributeValue("name");
         }

         @Override
         @SuppressWarnings("unchecked")
         public Map<String, Object> getParameters() {
            return (Map<String, Object>) proxy.retrieveAttributeValue("parameters");
         }

         @Override
         public void reload() {
            try {
               proxy.invokeOperation("reload");
            } catch (Exception e) {
               e.printStackTrace();
            }
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


   @Override
   @Test
   public void testStartStop() throws Exception {
      // this test does not make sense when using core messages:
      // the acceptor must be started to receive the management messages
   }

}

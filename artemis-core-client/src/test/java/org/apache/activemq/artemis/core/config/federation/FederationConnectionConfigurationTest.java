/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.config.federation;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FederationConnectionConfigurationTest {

   @Test
   public void testEncodeDecode() {
      FederationConnectionConfiguration configuration = new FederationConnectionConfiguration()
         .setUsername("user")
         .setPassword("secret")
         .setShareConnection(true)
         .setPriorityAdjustment(-2)
         .setClientFailureCheckPeriod(1000)
         .setConnectionTTL(2000)
         .setRetryInterval(500)
         .setRetryIntervalMultiplier(1.5)
         .setMaxRetryInterval(30000)
         .setInitialConnectAttempts(3)
         .setReconnectAttempts(4)
         .setCallTimeout(5000)
         .setCallFailoverTimeout(6000);

      ActiveMQBuffer buffer = ActiveMQBuffers.dynamicBuffer(256);
      configuration.encode(buffer);
      FederationConnectionConfiguration decoded = new FederationConnectionConfiguration();
      decoded.decode(buffer);

      assertEquals(30000, decoded.getMaxRetryInterval());
      assertEquals(configuration, decoded);
   }
}

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
package org.apache.activemq.artemis.service.extensions.tests.recovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.service.extensions.xa.recovery.XARecoveryConfig;
import org.junit.jupiter.api.Test;

public class XARecoveryConfigTest {

   @Test
   public void testEquals() throws Exception {
      String factClass = "org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory";

      TransportConfiguration transportConfig = new TransportConfiguration(factClass, null);
      XARecoveryConfig config = new XARecoveryConfig(false, new TransportConfiguration[]{transportConfig}, null, null, null);

      TransportConfiguration transportConfig2 = new TransportConfiguration(factClass, null);
      XARecoveryConfig config2 = new XARecoveryConfig(false, new TransportConfiguration[]{transportConfig2}, null, null, null);

      // They are using Different names
      assertNotEquals(transportConfig, transportConfig2);
      assertEquals(transportConfig.newTransportConfig(""), transportConfig2.newTransportConfig(""));

      // The equals here shouldn't take the name into consideration
      assertEquals(config, config2);
   }

   @Test
   public void testNotEquals() throws Exception {
      String factClass = "org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory";

      TransportConfiguration transportConfig = new TransportConfiguration(factClass, null);
      XARecoveryConfig config = new XARecoveryConfig(false, new TransportConfiguration[]{transportConfig}, null, null, null);

      TransportConfiguration transportConfig2 = new TransportConfiguration(factClass + "2", null);
      XARecoveryConfig config2 = new XARecoveryConfig(false, new TransportConfiguration[]{transportConfig2}, null, null, null);

      // They are using Different names
      assertNotEquals(transportConfig, transportConfig2);
      assertNotEquals(transportConfig.newTransportConfig(""), transportConfig2.newTransportConfig(""));

      // The equals here shouldn't take the name into consideration
      assertNotEquals(config, config2);
   }
}

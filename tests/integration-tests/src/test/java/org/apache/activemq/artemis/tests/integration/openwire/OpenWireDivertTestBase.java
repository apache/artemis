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
package org.apache.activemq.artemis.tests.integration.openwire;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.DivertConfiguration;

import javax.jms.ConnectionFactory;

public abstract class OpenWireDivertTestBase extends OpenWireTestBase {

   protected static final int TIMEOUT = 3000;
   protected ConnectionFactory factory;
   protected final String testAddress = "testAddress";
   protected final String forwardAddress = "forwardAddress";
   protected abstract boolean isExclusive();

   @Override
   protected void extraServerConfig(Configuration serverConfig) {
      DivertConfiguration divertConf = new DivertConfiguration().setName("divert1").setRoutingName("divert1").setAddress(testAddress).setForwardingAddress(forwardAddress).setExclusive(isExclusive());
      serverConfig.addDivertConfiguration(divertConf);
   }
}

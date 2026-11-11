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
package org.apache.activemq.artemis.tests.integration.ra;

import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.jms.server.impl.JMSServerManagerImpl;
import org.junit.jupiter.api.BeforeEach;

public class ActiveMQRAClusteredTestBase extends ActiveMQRATestBase {

   protected ActiveMQServer secondaryServer;
   protected JMSServerManagerImpl secondaryJmsServer;
   protected TransportConfiguration secondaryConnector;
   protected TransportConfiguration primaryConnector;

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();

      primaryConnector = new TransportConfiguration(INVM_CONNECTOR_FACTORY);
      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SERVER_ID_PROP_NAME, "1");
      secondaryConnector = new TransportConfiguration(INVM_CONNECTOR_FACTORY, params);

      secondaryServer = addServer(ActiveMQServers.newActiveMQServer(createSecondaryDefaultConfig(true), mbeanServer, usePersistence()));
      addServer(secondaryServer);
      secondaryJmsServer = new JMSServerManagerImpl(secondaryServer);
      secondaryJmsServer.start();
      waitForTopology(secondaryServer, 2);

   }

   @Override
   protected Configuration createDefaultConfig(boolean netty) throws Exception {
      return createSecondaryDefaultConfig(false);
   }

   protected Configuration createSecondaryDefaultConfig(boolean secondary) throws Exception {
      Map<String, Object> invmMap = new HashMap<>();
      Map<String, Object> nettyMap = new HashMap<>();
      String primaryConnectorName = "invm2";
      String secondaryConnectorName = "invm";
      int index = 0;

      if (secondary) {
         invmMap.put(TransportConstants.SERVER_ID_PROP_NAME, "1");
         nettyMap.put("port", "5545");
         primaryConnectorName = "invm";
         secondaryConnectorName = "invm2";
         index = 1;
      }

      ConfigurationImpl configuration = createBasicConfig(index).setJMXManagementEnabled(false).clearAcceptorConfigurations().addAcceptorConfiguration(new TransportConfiguration(INVM_ACCEPTOR_FACTORY, invmMap)).addAcceptorConfiguration(new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, nettyMap)).addConnectorConfiguration(secondaryConnectorName, secondaryConnector).addConnectorConfiguration(primaryConnectorName, primaryConnector).addClusterConfiguration(basicClusterConnectionConfig(secondaryConnectorName, primaryConnectorName).setReconnectAttempts(0));

      recreateDataDirectories(getTestDir(), index, false);

      return configuration;
   }

   @Override
   protected boolean usePersistence() {
      return true;
   }
}

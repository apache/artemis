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
package org.apache.activemq.artemis.tests.unit.core.remoting;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.unit.core.remoting.server.impl.fake.FakeAcceptorFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public class AcceptorsTest extends ActiveMQTestBase {

   @Test
   public void testMultipleAcceptorsWithSameHostPortDifferentName() throws Exception {
      final String acceptorFactoryClass = FakeAcceptorFactory.class.getName();

      Map<String, Object> params = new HashMap<>();
      params.put("host", "localhost");
      params.put("port", 5445);

      Set<TransportConfiguration> tcs = new HashSet<>();
      tcs.add(new TransportConfiguration(acceptorFactoryClass, params, "Acceptor1"));
      tcs.add(new TransportConfiguration(acceptorFactoryClass, params, "Acceptor2"));

      Configuration config = createBasicConfig();
      config.setAcceptorConfigurations(tcs);

      ActiveMQServer server = createServer(config);

      server.start();
      waitForServerToStart(server);

      assertNotNull(server.getRemotingService().getAcceptor("Acceptor1"));
      assertNotNull(server.getRemotingService().getAcceptor("Acceptor2"));
   }
}

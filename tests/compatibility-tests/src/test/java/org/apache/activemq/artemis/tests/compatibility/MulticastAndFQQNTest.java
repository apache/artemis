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

package org.apache.activemq.artemis.tests.compatibility;

import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.ARTEMIS_2_44_0;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.SNAPSHOT;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.compatibility.base.ClasspathBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MulticastAndFQQNTest extends ClasspathBase {

   private ActiveMQServer server;

   @BeforeEach
   public void setUp() throws Exception {
      ConfigurationImpl configuration = new ConfigurationImpl();
      configuration.setJournalType(JournalType.NIO);
      configuration.addAcceptorConfiguration("artemis", "tcp://0.0.0.0:61616");
      configuration.setSecurityEnabled(false);
      configuration.setPersistenceEnabled(false);

      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setRedistributionDelay(0);
      addressSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE);
      addressSettings.setAutoCreateAddresses(false);
      addressSettings.setAutoDeleteAddresses(false);
      addressSettings.setAutoCreateQueues(false);
      addressSettings.setAutoDeleteQueues(false);

      configuration.addAddressSetting("myAddress", addressSettings);

      QueueConfiguration queueConfig = QueueConfiguration.of("myQueue").setAddress("myAddress").setRoutingType(RoutingType.MULTICAST).setDurable(true);

      List<QueueConfiguration> queueConfigs = new ArrayList<>();
      queueConfigs.add(queueConfig);

      Set<String> routingTypes = new HashSet<>();
      routingTypes.add(RoutingType.MULTICAST.toString());

      CoreAddressConfiguration addressConfig = new CoreAddressConfiguration();
      addressConfig.setName("myAddress");
      addressConfig.setQueueConfigs(queueConfigs);
      addressConfig.setRoutingTypes(routingTypes);

      configuration.addAddressConfiguration(addressConfig);

      server = ActiveMQServers.newActiveMQServer(configuration, false);
      server.start();
   }

   @AfterEach
   public void tearDown() throws Exception {
      if (server != null) {
         server.stop();
      }
   }

   private void testVersion(String version) throws Throwable {
      assertNotNull(server.locateQueue("myQueue"));

      ClassLoader clientClassloader = getClasspath(version, false);
      clearGroovy(clientClassloader);
      evaluate(clientClassloader, "multicastFQQNTest/multicastFQQNConsumer.groovy");

      assertNotNull(server.locateQueue("myQueue"));
   }

   @Test
   public void testMultiCastFQQN_SNAPSHOT() throws Throwable {
      testVersion(SNAPSHOT);
   }

   @Test
   public void testMultiCastFQQN_2_44_0() throws Throwable {
      testVersion(ARTEMIS_2_44_0);
   }
}

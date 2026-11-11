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
package org.apache.activemq.artemis.tests.integration.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.jms.persistence.JMSStorageManager;
import org.apache.activemq.artemis.jms.persistence.config.PersistedBindings;
import org.apache.activemq.artemis.jms.persistence.config.PersistedConnectionFactory;
import org.apache.activemq.artemis.jms.persistence.config.PersistedDestination;
import org.apache.activemq.artemis.jms.persistence.config.PersistedType;
import org.apache.activemq.artemis.jms.persistence.impl.journal.JMSJournalStorageManagerImpl;
import org.apache.activemq.artemis.jms.server.config.ConnectionFactoryConfiguration;
import org.apache.activemq.artemis.jms.server.config.impl.ConnectionFactoryConfigurationImpl;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.TimeAndCounterIDGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JMSStorageManagerTest extends ActiveMQTestBase {

   private Map<String, PersistedConnectionFactory> mapExpectedCFs;

   protected JMSStorageManager jmsJournal;

   public JMSStorageManagerTest() {
      super();
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      mapExpectedCFs = new HashMap<>();
   }

   protected void addSetting(PersistedConnectionFactory setting) throws Exception {
      mapExpectedCFs.put(setting.getName(), setting);
      jmsJournal.storeConnectionFactory(setting);
   }

   @Test
   public void testSettings() throws Exception {

      createJMSStorage();

      List<String> transportConfigs = new ArrayList<>();

      for (int i = 0; i < 5; i++) {
         transportConfigs.add("c1-" + i);
         transportConfigs.add("c2-" + i);
      }

      ConnectionFactoryConfiguration config = new ConnectionFactoryConfigurationImpl().setName("some-name").setConnectorNames(transportConfigs);

      addSetting(new PersistedConnectionFactory(config));

      jmsJournal.stop();

      createJMSStorage();

      List<PersistedConnectionFactory> cfs = jmsJournal.recoverConnectionFactories();

      assertEquals(1, cfs.size());

      assertEquals("some-name", cfs.get(0).getName());

      PersistedConnectionFactory cf1 = cfs.get(0);

      assertEquals(10, cf1.getConfig().getConnectorNames().size());

      List<String> configs = cf1.getConfig().getConnectorNames();
      for (int i = 0, j = 0; i < 10; i += 2, j++) {
         assertEquals(configs.get(i), "c1-" + j);
         assertEquals(configs.get(i + 1), "c2-" + j);
      }
   }

   @Test
   public void testSizeOfCF() throws Exception {

      String[] str = new String[5];
      for (int i = 0; i < 5; i++) {
         str[i] = "str" + i;
      }

      ConnectionFactoryConfiguration config = new ConnectionFactoryConfigurationImpl().setName("some-name").setConnectorNames(new ArrayList<>()).setBindings("");

      int size = config.getEncodeSize();

      ActiveMQBuffer buffer = ActiveMQBuffers.fixedBuffer(size);

      config.encode(buffer);

      assertEquals(size, buffer.writerIndex());

      PersistedConnectionFactory persistedCF = new PersistedConnectionFactory(config);

      size = persistedCF.getEncodeSize();

      buffer = ActiveMQBuffers.fixedBuffer(size);

      persistedCF.encode(buffer);

      assertEquals(size, buffer.writerIndex());

   }

   @Test
   public void testSettingsWithConnectorConfigs() throws Exception {

      createJMSStorage();

      String[] str = new String[5];
      for (int i = 0; i < 5; i++) {
         str[i] = "str" + i;
      }

      List<String> connectorConfigs = new ArrayList<>();
      Map<String, Object> primaryParams = new HashMap<>();
      primaryParams.put(TransportConstants.PORT_PROP_NAME, 5665);
      Map<String, Object> backupParams = new HashMap<>();
      backupParams.put(TransportConstants.PORT_PROP_NAME, 5775);
      Map<String, Object> primaryParams2 = new HashMap<>();
      primaryParams2.put(TransportConstants.PORT_PROP_NAME, 6665);

      ConnectionFactoryConfiguration config = new ConnectionFactoryConfigurationImpl().setName("some-name").setConnectorNames(connectorConfigs).setBindings(str).setCallTimeout(RandomUtil.randomPositiveLong());
      List<Pair<String, String>> connectors = new ArrayList<>();
      connectors.add(new Pair<String, String>(RandomUtil.randomUUIDString(), null));
      //config.setConnectorNames(connectors);

      addSetting(new PersistedConnectionFactory(config));

      jmsJournal.stop();

      createJMSStorage();

      List<PersistedConnectionFactory> cfs = jmsJournal.recoverConnectionFactories();

      assertEquals(1, cfs.size());

      assertEquals("some-name", cfs.get(0).getName());

      assertEquals(config.getCallTimeout(), cfs.get(0).getConfig().getCallTimeout());
   }


   @Test
   public void testJNDIPersistence() throws Exception {
      createJMSStorage();

      jmsJournal.storeDestination(new PersistedDestination(PersistedType.Queue, "jndiPersistQueue", null, true));

      jmsJournal.addBindings(PersistedType.Queue, "jndiPersistQueue", "jndi-1");

      List<PersistedDestination> destinations = jmsJournal.recoverDestinations();

      List<PersistedBindings> jndiList = jmsJournal.recoverPersistedBindings();

      assertEquals(1, destinations.size());

      assertEquals(1, jndiList.size());

      jmsJournal.deleteDestination(PersistedType.Queue, "jndiPersistQueue");

      destinations = jmsJournal.recoverDestinations();

      assertEquals(0, destinations.size());

      jmsJournal.stop();

      createJMSStorage();

      destinations = jmsJournal.recoverDestinations();

      assertEquals(0, destinations.size());

      jndiList = jmsJournal.recoverPersistedBindings();

      assertEquals(1, jndiList.size());

      PersistedBindings jndi = jndiList.get(0);

      List<String> jndis = jndi.getBindings();

      assertEquals(1, jndis.size());

      assertEquals("jndi-1", jndis.get(0));

   }


   protected void createJMSStorage() throws Exception {
      jmsJournal = new JMSJournalStorageManagerImpl(null, new TimeAndCounterIDGenerator(), createDefaultInVMConfig(), null);
      runAfter(jmsJournal::stop);
      jmsJournal.start();
      jmsJournal.load();
   }


}

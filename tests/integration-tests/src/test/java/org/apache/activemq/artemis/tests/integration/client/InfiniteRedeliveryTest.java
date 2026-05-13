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
package org.apache.activemq.artemis.tests.integration.client;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.cli.commands.tools.journal.CompactJournal;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.ha.ReplicaPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStoreBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.InVMNodeManager;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.integration.cluster.util.SameProcessActiveMQServer;
import org.apache.activemq.artemis.tests.integration.cluster.util.TestableServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.ReplicatedBackupUtils;
import org.apache.activemq.artemis.tests.util.TransportConfigurationUtils;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(ParameterizedTestExtension.class)
public class InfiniteRedeliveryTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Parameters(name = "protocol={0}, useCLI={1}")
   public static Collection getParameters() {
      return Arrays.asList(new Object[][]{{"CORE", true}, {"AMQP", false}, {"OPENWIRE", false}});
   }

   public InfiniteRedeliveryTest(String protocol, boolean useCLI) {
      this.protocol = protocol;
      this.useCLI = useCLI;
   }


   String protocol;
   boolean useCLI;

   TestableServer primaryServer;
   TestableServer backupServer;

   Configuration backupConfig;
   Configuration primaryConfig;

   protected TestableServer createTestableServer(Configuration config, NodeManager nodeManager) throws Exception {
      boolean isBackup = config.getHAPolicyConfiguration() instanceof ReplicaPolicyConfiguration || config.getHAPolicyConfiguration() instanceof SharedStoreBackupPolicyConfiguration;
      return new SameProcessActiveMQServer(createInVMFailoverServer(true, config, nodeManager, isBackup ? 2 : 1));
   }

   // I am using a replicated config to make sure the replica will also configured replaceable records
   protected void createReplicatedConfigs() throws Exception {
      final TransportConfiguration primaryConnector = TransportConfigurationUtils.getNettyConnector(true, 0);
      final TransportConfiguration backupConnector = TransportConfigurationUtils.getNettyConnector(false, 0);
      final TransportConfiguration backupAcceptor = TransportConfigurationUtils.getNettyAcceptor(false, 0);

      backupConfig = createDefaultConfig(0, true);
      primaryConfig = createDefaultConfig(0, true);

      configureReplicationPair(backupConnector, backupAcceptor, primaryConnector);

      backupConfig.setBindingsDirectory(getBindingsDir(0, true)).setJournalDirectory(getJournalDir(0, true)).setPagingDirectory(getPageDir(0, true)).setLargeMessagesDirectory(getLargeMessagesDir(0, true)).setSecurityEnabled(false);



      backupServer = createTestableServer(backupConfig, new InVMNodeManager(true, backupConfig.getJournalLocation()));

      primaryConfig.clearAcceptorConfigurations().addAcceptorConfiguration(TransportConfigurationUtils.getNettyAcceptor(true, 0));

      primaryServer = createTestableServer(primaryConfig, new InVMNodeManager(false, primaryConfig.getJournalLocation()));
   }

   protected void configureReplicationPair(TransportConfiguration backupConnector,
                                           TransportConfiguration backupAcceptor,
                                           TransportConfiguration primaryConnector) {
      ReplicatedBackupUtils.configureReplicationPair(backupConfig, backupConnector, backupAcceptor, primaryConfig, primaryConnector, null);
      ((ReplicaPolicyConfiguration) backupConfig.getHAPolicyConfiguration()).setMaxSavedReplicatedJournalsSize(-1).setAllowFailBack(true);
      ((ReplicaPolicyConfiguration) backupConfig.getHAPolicyConfiguration()).setRestartBackup(false);
   }


   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();
   }

   protected void startServer(boolean reschedule) throws Exception {
      createReplicatedConfigs();
      Configuration configuration = primaryServer.getServer().getConfiguration();
      configuration.getAddressSettings().clear();
      if (reschedule) {
         AddressSettings settings = new AddressSettings().setMaxDeliveryAttempts(Integer.MAX_VALUE).setRedeliveryDelay(1);
         configuration.getAddressSettings().put("#", settings);
      } else {
         AddressSettings settings = new AddressSettings().setMaxDeliveryAttempts(Integer.MAX_VALUE).setRedeliveryDelay(0);
         configuration.getAddressSettings().put("#", settings);
      }
      primaryServer.start();
      backupServer.start();
      Wait.waitFor(primaryServer.getServer()::isReplicaSync);
   }

   @TestTemplate
   public void testInifinteRedeliveryWithScheduling() throws Exception {
      testInifinteRedeliveryWithScheduling(true);
   }

   @TestTemplate
   public void testInifinteRedeliveryWithoutScheduling() throws Exception {
      testInifinteRedeliveryWithScheduling(false);
   }

   public void testInifinteRedeliveryWithScheduling(boolean reschedule) throws Exception {
      startServer(reschedule);
      primaryServer.getServer().addAddressInfo(new AddressInfo("test").setAutoCreated(false).addRoutingType(RoutingType.ANYCAST));
      primaryServer.getServer().createQueue(QueueConfiguration.of("test").setRoutingType(RoutingType.ANYCAST).setAddress("test").setDurable(true));

      ConnectionFactory factory;

      if (protocol.toUpperCase().equals("OPENWIRE")) {
         factory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616?jms.redeliveryPolicy.maximumRedeliveries=100&jms.redeliveryPolicy.redeliveryDelay=0");
      } else {
         factory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");
      }

      Connection connection = factory.createConnection();
      Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
      Queue queue = session.createQueue("test");
      assertNotNull(queue);
      MessageProducer producer = session.createProducer(queue);

      producer.send(session.createTextMessage("hello"));
      session.commit();


      MessageConsumer consumer = session.createConsumer(queue);
      connection.start();
      for (int i = 0; i < 100; i++) {
         Message message = consumer.receive(10000);
         assertNotNull(message);
         session.rollback();
      }
      connection.close();

      if (!useCLI) {
         primaryServer.getServer().getStorageManager().getMessageJournal().scheduleCompactAndBlock(5000);
         backupServer.getServer().getStorageManager().getMessageJournal().scheduleCompactAndBlock(5000);
      }

      primaryServer.stop();
      backupServer.stop();

      if (useCLI) {
         CompactJournal.compactJournals(backupServer.getServer().getConfiguration());
         CompactJournal.compactJournals(primaryServer.getServer().getConfiguration());
      }

      Map<Integer, AtomicInteger> counts = countJournal(primaryServer.getServer().getConfiguration());
      counts.forEach((k, v) -> logger.debug("{}={}", k, v));
      counts.forEach((k, v) -> assertTrue(v.intValue() < 20, "Record type " + k + " has a lot of records:" + v));

      Map<Integer, AtomicInteger> backupCounts = countJournal(backupServer.getServer().getConfiguration());
      assertFalse(backupCounts.isEmpty());
      backupCounts.forEach((k, v) -> logger.debug("On Backup:{}={}", k, v));
      backupCounts.forEach((k, v) -> assertTrue(v.intValue() < 10, "Backup Record type " + k + " has a lot of records:" + v));


   }
}

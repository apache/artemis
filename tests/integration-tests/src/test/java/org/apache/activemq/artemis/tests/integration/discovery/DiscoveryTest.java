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
package org.apache.activemq.artemis.tests.integration.discovery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.BroadcastEndpoint;
import org.apache.activemq.artemis.api.core.BroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.JGroupsFileBroadcastEndpointFactory;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.jgroups.JChannelManager;
import org.apache.activemq.artemis.api.core.management.CoreNotificationType;
import org.apache.activemq.artemis.core.cluster.DiscoveryEntry;
import org.apache.activemq.artemis.core.cluster.DiscoveryGroup;
import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.cluster.BroadcastGroup;
import org.apache.activemq.artemis.core.server.cluster.impl.BroadcastGroupImpl;
import org.apache.activemq.artemis.core.server.management.Notification;
import org.apache.activemq.artemis.tests.integration.SimpleNotificationService;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This will test Discovery test on JGroups and UDP.
 * <p>
 * In some configurations IPV6 may be a challenge. To make sure this test works, you may add this property to your JVM
 * settings: {@literal -Djgroups.bind_addr=::1}
 * <p>
 * Or ultimately you may also turn off IPV6: {@literal -Djava.net.preferIPv4Stack=true}
 * <p>
 * Note when you are not sure about your IP settings of your test machine, you should make sure that the
 * jgroups.bind_addr and java.net.preferXXStack by defining them explicitly, for example if you would like to use IPV6,
 * set BOTH properties to your JVM like the following: -Djgroups.bind_addr=::1 -Djava.net.preferIPv6Addresses=true
 * <p>
 * or if you prefer IPV4: -Djgroups.bind_addr=localhost -Djava.net.preferIPv4Stack=true
 * <p>
 * Also: Make sure you add integration-tests/src/tests/resources to your project path on the tests/integration-tests
 */
public class DiscoveryTest extends DiscoveryBaseTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String TEST_JGROUPS_CONF_FILE = "test-jgroups-file_ping.xml";

   BroadcastGroup bg = null, bg1 = null, bg2 = null, bg3 = null;
   DiscoveryGroup dg = null, dg1 = null, dg2 = null, dg3 = null;

   @BeforeEach
   public void prepareLoopback() {
      JChannelManager.getInstance().setLoopbackMessages(true);
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      JChannelManager.getInstance().clear().setLoopbackMessages(false);
      // This file path is defined by TEST_JGROUPS_CONF_FILE
      deleteDirectory(new File("./target/tmp/amqtest.ping.dir"));
      for (ActiveMQComponent component : new ActiveMQComponent[]{bg, bg1, bg2, bg3, dg, dg1, dg2, dg3}) {
         stopComponent(component);
      }
      super.tearDown();
   }

   @Test
   public void testSimpleBroadcast() throws Exception {
      final InetAddress groupAddress = InetAddress.getByName(address1);
      final int groupPort = getUDPDiscoveryPort();
      final int timeout = 500;

      final String nodeID = RandomUtil.randomUUIDString();

      bg = newBroadcast(nodeID, RandomUtil.randomUUIDString(), null, -1, groupAddress, groupPort);

      bg.start();

      TransportConfiguration live1 = generateTC();

      bg.addConnector(live1);

      dg = newDiscoveryGroup(RandomUtil.randomUUIDString(), RandomUtil.randomUUIDString(), null, groupAddress, groupPort, timeout);

      dg.start();

      verifyBroadcast(bg, dg);

      List<DiscoveryEntry> entries = dg.getDiscoveryEntries();
      assertEqualsDiscoveryEntries(Arrays.asList(live1), entries);
   }

   @Test
   public void testSimpleBroadcastJGropus() throws Exception {
      final String nodeID = RandomUtil.randomUUIDString();

      bg = new BroadcastGroupImpl(new FakeNodeManager(nodeID), "broadcast", 100, null, new JGroupsFileBroadcastEndpointFactory().setChannelName("tst").setFile(TEST_JGROUPS_CONF_FILE));

      bg.start();

      TransportConfiguration live1 = generateTC();

      bg.addConnector(live1);

      dg = new DiscoveryGroup(nodeID + "1", "broadcast", 5000L, ActiveMQClient.DEFAULT_DISCOVERY_STOPPING_TIMEOUT, new JGroupsFileBroadcastEndpointFactory().setChannelName("tst").setFile(TEST_JGROUPS_CONF_FILE), null);

      dg.start();

      verifyBroadcast(bg, dg);
      List<DiscoveryEntry> entries = dg.getDiscoveryEntries();
      assertEqualsDiscoveryEntries(Arrays.asList(live1), entries);
   }

   @Test
   public void testJGroupsOpenClientInitializesChannel() throws Exception {
      JGroupsFileBroadcastEndpointFactory factory = new JGroupsFileBroadcastEndpointFactory().setFile(TEST_JGROUPS_CONF_FILE).setChannelName("tst");
      BroadcastEndpoint endpoint = factory.createBroadcastEndpoint();
      endpoint.close(false);
      endpoint.openClient();
      endpoint.close(false);
   }

   /**
    * Create one broadcaster and 100 receivers. Make sure broadcasting works. Then stop 99 of the receivers, the last
    * one could still be working.
    */
   @Test
   public void testJGropusChannelReferenceCounting() throws Exception {
      BroadcastEndpointFactory factory = new JGroupsFileBroadcastEndpointFactory().setChannelName("tst").setFile(TEST_JGROUPS_CONF_FILE);
      BroadcastEndpoint broadcaster = factory.createBroadcastEndpoint();
      broadcaster.openBroadcaster();

      try {

         int num = 100;
         BroadcastEndpoint[] receivers = new BroadcastEndpoint[num];
         for (int i = 0; i < num; i++) {
            receivers[i] = factory.createBroadcastEndpoint();
            receivers[i].openClient();
         }

         final byte[] data = new byte[]{1, 2, 3, 4, 5};
         broadcaster.broadcast(data);

         for (int i = 0; i < num; i++) {
            byte[] received = receivers[i].receiveBroadcast(5000, TimeUnit.MILLISECONDS);
            assertNotNull(received);
            assertEquals(5, received.length);
            assertEquals(1, received[0]);
            assertEquals(2, received[1]);
            assertEquals(3, received[2]);
            assertEquals(4, received[3]);
            assertEquals(5, received[4]);
         }

         for (int i = 0; i < num - 1; i++) {
            receivers[i].close(false);
         }

         byte[] data1 = receivers[num - 1].receiveBroadcast(5, TimeUnit.SECONDS);
         assertNull(data1);

         broadcaster.broadcast(data);
         data1 = receivers[num - 1].receiveBroadcast(5, TimeUnit.SECONDS);

         assertNotNull(data1);
         assertEquals(5, data1.length);
         assertEquals(1, data1[0]);
         assertEquals(2, data1[1]);
         assertEquals(3, data1[2]);
         assertEquals(4, data1[3]);
         assertEquals(5, data1[4]);

         receivers[num - 1].close(false);
      } finally {
         broadcaster.close(true);
      }
   }

   /**
    * Create one broadcaster and 50 receivers. Make sure broadcasting works. Then stop all of the receivers, and create
    * 50 new ones. Make sure the 50 new ones are receiving data from the broadcasting.
    */
   @Test
   public void testJGropusChannelReferenceCounting1() throws Exception {
      BroadcastEndpointFactory factory = new JGroupsFileBroadcastEndpointFactory().setChannelName("tst").setFile(TEST_JGROUPS_CONF_FILE);
      BroadcastEndpoint broadcaster = factory.createBroadcastEndpoint();
      broadcaster.openBroadcaster();
      int num = 50;
      BroadcastEndpoint[] receivers = new BroadcastEndpoint[num];
      for (int i = 0; i < num; i++) {
         receivers[i] = factory.createBroadcastEndpoint();
         receivers[i].openClient();
      }

      try {

         final byte[] data = new byte[]{1, 2, 3, 4, 5};
         broadcaster.broadcast(data);

         for (int i = 0; i < num; i++) {
            byte[] received = receivers[i].receiveBroadcast(5000, TimeUnit.MILLISECONDS);
            assertNotNull(received);
            assertEquals(5, received.length);
            assertEquals(1, received[0]);
            assertEquals(2, received[1]);
            assertEquals(3, received[2]);
            assertEquals(4, received[3]);
            assertEquals(5, received[4]);
         }

         for (int i = 0; i < num; i++) {
            receivers[i].close(false);
         }

         //new ones
         for (int i = 0; i < num; i++) {
            receivers[i] = factory.createBroadcastEndpoint();
            receivers[i].openClient();
         }

         broadcaster.broadcast(data);

         for (int i = 0; i < num; i++) {
            byte[] received = receivers[i].receiveBroadcast(5000, TimeUnit.MILLISECONDS);
            assertNotNull(received);
            assertEquals(5, received.length);
            assertEquals(1, received[0]);
            assertEquals(2, received[1]);
            assertEquals(3, received[2]);
            assertEquals(4, received[3]);
            assertEquals(5, received[4]);
         }

      } finally {
         for (int i = 0; i < num; i++) {
            receivers[i].close(false);
         }
         broadcaster.close(true);
      }
   }

   /**
    * Create one broadcaster and 50 receivers. Then stop half of the receivers. Then add the half back, plus some more.
    * Make sure all receivers receive data.
    */
   @Test
   public void testJGropusChannelReferenceCounting2() throws Exception {
      BroadcastEndpointFactory factory = new JGroupsFileBroadcastEndpointFactory().setChannelName("tst").setFile(TEST_JGROUPS_CONF_FILE);
      BroadcastEndpoint broadcaster = factory.createBroadcastEndpoint();
      broadcaster.openBroadcaster();

      int num = 50;
      BroadcastEndpoint[] receivers = new BroadcastEndpoint[num];
      for (int i = 0; i < num; i++) {
         receivers[i] = factory.createBroadcastEndpoint();
         receivers[i].openClient();
      }

      for (int i = 0; i < num / 2; i++) {
         receivers[i].close(false);
      }

      for (int i = 0; i < num / 2; i++) {
         receivers[i] = factory.createBroadcastEndpoint();
         receivers[i].openClient();
      }

      int num2 = 10;
      BroadcastEndpoint[] moreReceivers = new BroadcastEndpoint[num2];

      for (int i = 0; i < num2; i++) {
         moreReceivers[i] = factory.createBroadcastEndpoint();
         moreReceivers[i].openClient();
      }

      final byte[] data = new byte[]{1, 2, 3, 4, 5};
      broadcaster.broadcast(data);

      for (int i = 0; i < num; i++) {
         byte[] received = receivers[i].receiveBroadcast(5000, TimeUnit.MILLISECONDS);
         assertNotNull(received);
         assertEquals(5, received.length);
         assertEquals(1, received[0]);
         assertEquals(2, received[1]);
         assertEquals(3, received[2]);
         assertEquals(4, received[3]);
         assertEquals(5, received[4]);
      }

      for (int i = 0; i < num2; i++) {
         byte[] received = moreReceivers[i].receiveBroadcast(5000, TimeUnit.MILLISECONDS);
         assertNotNull(received);
         assertEquals(5, received.length);
         assertEquals(1, received[0]);
         assertEquals(2, received[1]);
         assertEquals(3, received[2]);
         assertEquals(4, received[3]);
         assertEquals(5, received[4]);
      }

      for (int i = 0; i < num; i++) {
         receivers[i].close(false);
      }

      for (int i = 0; i < num2; i++) {
         moreReceivers[i].close(false);
      }

      broadcaster.close(true);
   }

   @Test
   public void testStraightSendReceiveJGroups() throws Exception {
      BroadcastEndpoint broadcaster = null;
      BroadcastEndpoint client = null;
      try {
         JGroupsFileBroadcastEndpointFactory endpointFactory = new JGroupsFileBroadcastEndpointFactory().setChannelName("tst").setFile(TEST_JGROUPS_CONF_FILE);
         broadcaster = endpointFactory.createBroadcastEndpoint();

         broadcaster.openBroadcaster();

         client = endpointFactory.createBroadcastEndpoint();

         client.openClient();

         Thread.sleep(1000);

         byte[] randomBytes = "PQP".getBytes();

         broadcaster.broadcast(randomBytes);

         byte[] btreceived = client.receiveBroadcast(5, TimeUnit.SECONDS);

         logger.debug("BTReceived = {}", Arrays.toString(btreceived));

         assertNotNull(btreceived);

         assertEquals(randomBytes.length, btreceived.length);

         for (int i = 0; i < randomBytes.length; i++) {
            assertEquals(randomBytes[i], btreceived[i]);
         }
      } finally {
         try {
            if (broadcaster != null) {
               broadcaster.close(true);
            }

            if (client != null) {
               client.close(false);
            }
         } catch (Exception ignored) {
            ignored.printStackTrace();
         }
      }

   }

   @Test
   public void testSimpleBroadcastSpecificNIC() throws Exception {
      final InetAddress groupAddress = InetAddress.getByName(address1);
      final int groupPort = getUDPDiscoveryPort();
      final int timeout = 500;

      final String nodeID = RandomUtil.randomUUIDString();

      // We need to choose a real NIC on the local machine - note this will silently pass if the machine
      // has no usable NIC!

      Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();

      InetAddress localAddress = InetAddress.getLoopbackAddress();

      logger.debug("Local address is {}", localAddress);

      bg = newBroadcast(nodeID, RandomUtil.randomUUIDString(), localAddress, -1, groupAddress, groupPort);

      bg.start();

      TransportConfiguration live1 = generateTC();

      bg.addConnector(live1);

      dg = newDiscoveryGroup(RandomUtil.randomUUIDString(), RandomUtil.randomUUIDString(), localAddress, groupAddress, groupPort, timeout);

      dg.start();

      verifyBroadcast(bg, dg);

      List<DiscoveryEntry> entries = dg.getDiscoveryEntries();
      assertEqualsDiscoveryEntries(Arrays.asList(live1), entries);

   }

   @Test
   public void testSimpleBroadcastWithStopStartDiscoveryGroup() throws Exception {
      final InetAddress groupAddress = InetAddress.getByName(address1);
      final int groupPort = getUDPDiscoveryPort();
      final int timeout = 500;

      final String nodeID = RandomUtil.randomUUIDString();

      bg = newBroadcast(nodeID, RandomUtil.randomUUIDString(), null, -1, groupAddress, groupPort);

      bg.start();

      TransportConfiguration live1 = generateTC();

      bg.addConnector(live1);

      dg = newDiscoveryGroup(RandomUtil.randomUUIDString(), RandomUtil.randomUUIDString(), null, groupAddress, groupPort, timeout);

      dg.start();

      verifyBroadcast(bg, dg);
      List<DiscoveryEntry> entries = dg.getDiscoveryEntries();
      assertEqualsDiscoveryEntries(Arrays.asList(live1), entries);

      bg.stop();

      dg.stop();

      dg.start();

      bg.start();

      verifyBroadcast(bg, dg);
      entries = dg.getDiscoveryEntries();
      assertEqualsDiscoveryEntries(Arrays.asList(live1), entries);
   }

   @Test
   public void testIgnoreTrafficFromOwnNode() throws Exception {
      final InetAddress groupAddress = InetAddress.getByName(address1);
      final int groupPort = getUDPDiscoveryPort();
      final int timeout = 500;

      String nodeID = RandomUtil.randomUUIDString();

      bg = newBroadcast(nodeID, RandomUtil.randomUUIDString(), null, -1, groupAddress, groupPort);

      bg.start();

      TransportConfiguration live1 = generateTC();

      bg.addConnector(live1);

      dg = newDiscoveryGroup(nodeID, RandomUtil.randomUUIDString(), null, groupAddress, groupPort, timeout);

      dg.start();

      verifyNonBroadcast(bg, dg);
      List<DiscoveryEntry> entries = dg.getDiscoveryEntries();

      assertNotNull(entries);

      assertEquals(0, entries.size());

   }

   @Test
   public void testSimpleBroadcastDifferentPort() throws Exception {
      final InetAddress groupAddress = InetAddress.getByName(getUDPDiscoveryAddress());
      final int groupPort = getUDPDiscoveryPort();
      final int timeout = 500;

      bg = newBroadcast(RandomUtil.randomUUIDString(), RandomUtil.randomUUIDString(), null, -1, groupAddress, groupPort);

      bg.start();

      TransportConfiguration live1 = generateTC();

      bg.addConnector(live1);

      final int port2 = getUDPDiscoveryPort(1);

      dg = newDiscoveryGroup(RandomUtil.randomUUIDString(), RandomUtil.randomUUIDString(), null, groupAddress, port2, timeout);

      dg.start();

      verifyNonBroadcast(bg, dg);
   }

   @Test
   public void testSimpleBroadcastDifferentAddressAndPort() throws Exception {
      final InetAddress groupAddress = InetAddress.getByName(address1);
      final int groupPort = getUDPDiscoveryPort();
      final int timeout = 500;

      bg = newBroadcast(RandomUtil.randomUUIDString(), RandomUtil.randomUUIDString(), null, -1, groupAddress, groupPort);

      bg.start();

      TransportConfiguration live1 = generateTC();

      bg.addConnector(live1);

      final InetAddress groupAddress2 = InetAddress.getByName(address2);
      final int port2 = getUDPDiscoveryPort(1);

      dg = newDiscoveryGroup(RandomUtil.randomUUIDString(), RandomUtil.randomUUIDString(), null, groupAddress2, port2, timeout);

      dg.start();

      verifyNonBroadcast(bg, dg);
   }

   @Test
   public void testMultipleGroups() throws Exception {
      final int groupPort1 = getUDPDiscoveryPort();

      final int groupPort2 = getUDPDiscoveryPort(1);

      final int groupPort3 = getUDPDiscoveryPort(2);

      final InetAddress groupAddress1 = InetAddress.getByName(address1);

      final InetAddress groupAddress2 = InetAddress.getByName(address2);

      final InetAddress groupAddress3 = InetAddress.getByName(address3);

      final int timeout = 5000;

      String node1 = UUIDGenerator.getInstance().generateStringUUID();

      String node2 = UUIDGenerator.getInstance().generateStringUUID();

      String node3 = UUIDGenerator.getInstance().generateStringUUID();

      bg1 = newBroadcast(node1, RandomUtil.randomUUIDString(), null, -1, groupAddress1, groupPort1);

      bg2 = newBroadcast(node2, RandomUtil.randomUUIDString(), null, -1, groupAddress2, groupPort2);

      bg3 = newBroadcast(node3, RandomUtil.randomUUIDString(), null, -1, groupAddress3, groupPort3);

      bg2.start();
      bg1.start();
      bg3.start();

      TransportConfiguration live1 = generateTC("live1");

      TransportConfiguration live2 = generateTC("live2");

      TransportConfiguration live3 = generateTC("live3");

      bg1.addConnector(live1);
      bg2.addConnector(live2);
      bg3.addConnector(live3);

      dg1 = newDiscoveryGroup("group-1::" + RandomUtil.randomUUIDString(), "group-1::" + RandomUtil.randomUUIDString(), null, groupAddress1, groupPort1, timeout);
      dg1.start();

      dg2 = newDiscoveryGroup("group-2::" + RandomUtil.randomUUIDString(), "group-2::" + RandomUtil.randomUUIDString(), null, groupAddress2, groupPort2, timeout);
      dg2.start();

      dg3 = newDiscoveryGroup("group-3::" + RandomUtil.randomUUIDString(), "group-3::" + RandomUtil.randomUUIDString(), null, groupAddress3, groupPort3, timeout);
      dg3.start();

      bg1.broadcastConnectors();

      bg2.broadcastConnectors();

      bg3.broadcastConnectors();

      boolean ok = dg1.waitForBroadcast(timeout);
      assertTrue(ok);
      List<DiscoveryEntry> entries = dg1.getDiscoveryEntries();
      assertEqualsDiscoveryEntries(Arrays.asList(live1), entries);

      ok = dg2.waitForBroadcast(timeout);
      assertTrue(ok);
      entries = dg2.getDiscoveryEntries();
      assertEqualsDiscoveryEntries(Arrays.asList(live2), entries);

      ok = dg3.waitForBroadcast(timeout);
      assertTrue(ok);
      entries = dg3.getDiscoveryEntries();
      assertEqualsDiscoveryEntries(Arrays.asList(live3), entries);
   }

   // -- fix this test
   // public void testBroadcastNullBackup() throws Exception
   // {
   // final InetAddress groupAddress = InetAddress.getByName(address1);
   // final int groupPort = getUDPDiscoveryPort();
   // final int timeout = 500;
   //
   // String nodeID = RandomUtil.randomString();
   //
   // BroadcastGroup bg = new BroadcastGroupImpl(nodeID,
   // RandomUtil.randomString(),
   // null,
   // -1,
   // groupAddress,
   // groupPort,
   // true);
   //
   // bg.start();
   //
   // TransportConfiguration live1 = generateTC();
   //
   // Pair<TransportConfiguration, TransportConfiguration> connectorPair = new Pair<TransportConfiguration,
   // TransportConfiguration>(live1,
   // null);
   //
   // bg.addConnectorPair(connectorPair);
   //
   // DiscoveryGroup dg = new DiscoveryGroup(RandomUtil.randomString(),
   // RandomUtil.randomString(),
   // null,
   // groupAddress,
   // groupPort,
   // timeout,
   // Executors.newFixedThreadPool(1));
   //
   // dg.start();
   //
   // bg.broadcastConnectors();
   //
   // boolean ok = dg.waitForBroadcast(1000);
   //
   // Assertions.assertTrue(ok);
   // }

   @Test
   public void testDiscoveryListenersCalled() throws Exception {
      final InetAddress groupAddress = InetAddress.getByName(address1);
      final int groupPort = getUDPDiscoveryPort();
      final int timeout = 500;

      String nodeID = RandomUtil.randomUUIDString();

      bg = newBroadcast(nodeID, RandomUtil.randomUUIDString(), null, -1, groupAddress, groupPort);

      bg.start();

      TransportConfiguration live1 = generateTC();

      bg.addConnector(live1);

      dg = newDiscoveryGroup(RandomUtil.randomUUIDString(), RandomUtil.randomUUIDString(), null, groupAddress, groupPort, timeout);

      MyListener listener1 = new MyListener();
      MyListener listener2 = new MyListener();
      MyListener listener3 = new MyListener();

      dg.registerListener(listener1);
      dg.registerListener(listener2);
      dg.registerListener(listener3);

      dg.start();

      verifyBroadcast(bg, dg);

      assertTrue(listener1.called);
      assertTrue(listener2.called);
      assertTrue(listener3.called);

      listener1.called = false;
      listener2.called = false;
      listener3.called = false;

      verifyBroadcast(bg, dg);

      // Won't be called since connectors haven't changed
      assertFalse(listener1.called);
      assertFalse(listener2.called);
      assertFalse(listener3.called);
   }

   @Test
   public void testConnectorsUpdatedMultipleBroadcasters() throws Exception {
      final InetAddress groupAddress = InetAddress.getByName(address1);
      final int groupPort = getUDPDiscoveryPort();
      final int timeout = 500;

      String node1 = RandomUtil.randomUUIDString();
      String node2 = RandomUtil.randomUUIDString();
      String node3 = RandomUtil.randomUUIDString();

      bg1 = newBroadcast(node1, RandomUtil.randomUUIDString(), null, -1, groupAddress, groupPort);
      bg1.start();

      bg2 = newBroadcast(node2, RandomUtil.randomUUIDString(), null, -1, groupAddress, groupPort);
      bg2.start();

      bg3 = newBroadcast(node3, RandomUtil.randomUUIDString(), null, -1, groupAddress, groupPort);
      bg3.start();

      TransportConfiguration live1 = generateTC();
      bg1.addConnector(live1);

      TransportConfiguration live2 = generateTC();
      bg2.addConnector(live2);

      TransportConfiguration live3 = generateTC();
      bg3.addConnector(live3);

      dg = newDiscoveryGroup(RandomUtil.randomUUIDString(), RandomUtil.randomUUIDString(), null, groupAddress, groupPort, timeout);

      MyListener listener1 = new MyListener();
      dg.registerListener(listener1);
      MyListener listener2 = new MyListener();
      dg.registerListener(listener2);

      dg.start();

      verifyBroadcast(bg1, dg);
      List<DiscoveryEntry> entries = dg.getDiscoveryEntries();
      assertEqualsDiscoveryEntries(Arrays.asList(live1), entries);
      assertTrue(listener1.called);
      assertTrue(listener2.called);
      listener1.called = false;
      listener2.called = false;

      verifyBroadcast(bg2, dg);
      entries = dg.getDiscoveryEntries();
      assertEqualsDiscoveryEntries(Arrays.asList(live1, live2), entries);
      assertTrue(listener1.called);
      assertTrue(listener2.called);
      listener1.called = false;
      listener2.called = false;

      verifyBroadcast(bg3, dg);
      entries = dg.getDiscoveryEntries();
      assertEqualsDiscoveryEntries(Arrays.asList(live1, live2, live3), entries);
      assertTrue(listener1.called);
      assertTrue(listener2.called);
      listener1.called = false;
      listener2.called = false;

      verifyBroadcast(bg1, dg);
      entries = dg.getDiscoveryEntries();
      assertEqualsDiscoveryEntries(Arrays.asList(live1, live2, live3), entries);
      assertFalse(listener1.called);
      assertFalse(listener2.called);
      listener1.called = false;
      listener2.called = false;

      verifyBroadcast(bg2, dg);
      entries = dg.getDiscoveryEntries();
      assertEqualsDiscoveryEntries(Arrays.asList(live1, live2, live3), entries);
      assertFalse(listener1.called);
      assertFalse(listener2.called);
      listener1.called = false;
      listener2.called = false;

      verifyBroadcast(bg3, dg);
      entries = dg.getDiscoveryEntries();
      assertEqualsDiscoveryEntries(Arrays.asList(live1, live2, live3), entries);
      assertFalse(listener1.called);
      assertFalse(listener2.called);
      listener1.called = false;
      listener2.called = false;

      bg2.removeConnector(live2);
      verifyBroadcast(bg2, dg);

      // Connector2 should still be there since not timed out yet

      entries = dg.getDiscoveryEntries();
      assertEqualsDiscoveryEntries(Arrays.asList(live1, live2, live3), entries);
      assertFalse(listener1.called);
      assertFalse(listener2.called);
      listener1.called = false;
      listener2.called = false;

      Thread.sleep(timeout * 2);

      bg1.broadcastConnectors();
      boolean ok = dg.waitForBroadcast(1000);
      bg2.broadcastConnectors();
      ok = dg.waitForBroadcast(1000);
      bg3.broadcastConnectors();
      ok = dg.waitForBroadcast(1000);

      entries = dg.getDiscoveryEntries();
      assertEqualsDiscoveryEntries(Arrays.asList(live1, live3), entries);
      assertTrue(listener1.called);
      assertTrue(listener2.called);
      listener1.called = false;
      listener2.called = false;

      bg1.removeConnector(live1);
      bg3.removeConnector(live3);

      Thread.sleep(timeout * 2);

      bg1.broadcastConnectors();
      ok = dg.waitForBroadcast(1000);
      bg2.broadcastConnectors();
      ok = dg.waitForBroadcast(1000);
      bg3.broadcastConnectors();
      ok = dg.waitForBroadcast(1000);

      entries = dg.getDiscoveryEntries();
      assertNotNull(entries);
      assertEquals(0, entries.size());
      assertTrue(listener1.called);
      assertTrue(listener2.called);
      listener1.called = false;
      listener2.called = false;

      bg1.broadcastConnectors();
      ok = dg.waitForBroadcast(1000);
      bg2.broadcastConnectors();
      ok = dg.waitForBroadcast(1000);
      bg3.broadcastConnectors();
      ok = dg.waitForBroadcast(1000);

      entries = dg.getDiscoveryEntries();
      assertNotNull(entries);
      assertEquals(0, entries.size());
      assertFalse(listener1.called);
      assertFalse(listener2.called);
   }

   @Test
   public void testMultipleDiscoveryGroups() throws Exception {
      final InetAddress groupAddress = InetAddress.getByName(address1);
      final int groupPort = getUDPDiscoveryPort();
      final int timeout = 500;

      String nodeID = RandomUtil.randomUUIDString();

      bg = newBroadcast(nodeID, RandomUtil.randomUUIDString(), null, -1, groupAddress, groupPort);

      bg.start();

      TransportConfiguration live1 = generateTC();

      bg.addConnector(live1);

      dg1 = newDiscoveryGroup(RandomUtil.randomUUIDString(), RandomUtil.randomUUIDString(), null, groupAddress, groupPort, timeout);

      dg2 = newDiscoveryGroup(RandomUtil.randomUUIDString(), RandomUtil.randomUUIDString(), null, groupAddress, groupPort, timeout);

      dg3 = newDiscoveryGroup(RandomUtil.randomUUIDString(), RandomUtil.randomUUIDString(), null, groupAddress, groupPort, timeout);

      dg1.start();
      dg2.start();
      dg3.start();

      bg.broadcastConnectors();

      boolean ok = dg1.waitForBroadcast(1000);
      assertTrue(ok);
      List<DiscoveryEntry> entries = dg1.getDiscoveryEntries();
      assertEqualsDiscoveryEntries(Arrays.asList(live1), entries);

      ok = dg2.waitForBroadcast(1000);
      assertTrue(ok);
      entries = dg2.getDiscoveryEntries();
      assertEqualsDiscoveryEntries(Arrays.asList(live1), entries);

      ok = dg3.waitForBroadcast(1000);
      assertTrue(ok);
      entries = dg3.getDiscoveryEntries();
      assertEqualsDiscoveryEntries(Arrays.asList(live1), entries);

      bg.stop();

      dg1.stop();
      dg2.stop();
      dg3.stop();
   }

   @Test
   public void testDiscoveryGroupNotifications() throws Exception {
      SimpleNotificationService notifService = new SimpleNotificationService();
      SimpleNotificationService.Listener notifListener = new SimpleNotificationService.Listener();
      notifService.addNotificationListener(notifListener);

      final InetAddress groupAddress = InetAddress.getByName(address1);
      final int groupPort = getUDPDiscoveryPort();
      final int timeout = 500;

      dg = newDiscoveryGroup(RandomUtil.randomUUIDString(), RandomUtil.randomUUIDString(), null, groupAddress, groupPort, timeout, notifService);

      assertEquals(0, notifListener.count(CoreNotificationType.DISCOVERY_GROUP_STARTED));

      dg.start();

      Wait.assertEquals(1, () -> notifListener.count(CoreNotificationType.DISCOVERY_GROUP_STARTED), 5000, 100);
      Notification notif = notifListener.findAny(CoreNotificationType.DISCOVERY_GROUP_STARTED);
      assertEquals(CoreNotificationType.DISCOVERY_GROUP_STARTED, notif.getType());
      assertEquals(dg.getName(), notif.getProperties().getSimpleStringProperty(SimpleString.of("name")).toString());

      dg.stop();

      Wait.assertEquals(2, () -> notifListener.count(CoreNotificationType.DISCOVERY_GROUP_STARTED, CoreNotificationType.DISCOVERY_GROUP_STOPPED), 5000, 100);
      notif = notifListener.findAny(CoreNotificationType.DISCOVERY_GROUP_STOPPED);
      assertEquals(CoreNotificationType.DISCOVERY_GROUP_STOPPED, notif.getType());
      assertEquals(dg.getName(), notif.getProperties().getSimpleStringProperty(SimpleString.of("name")).toString());
   }

   @Test
   public void testBroadcastGroupNotifications() throws Exception {
      SimpleNotificationService notifService = new SimpleNotificationService();
      SimpleNotificationService.Listener notifListener = new SimpleNotificationService.Listener();
      notifService.addNotificationListener(notifListener);

      final InetAddress groupAddress = InetAddress.getByName(address1);
      final int groupPort = getUDPDiscoveryPort();

      bg = newBroadcast(RandomUtil.randomUUIDString(), RandomUtil.randomUUIDString(), null, -1, groupAddress, groupPort);

      bg.setNotificationService(notifService);

      assertEquals(0, notifListener.count(CoreNotificationType.BROADCAST_GROUP_STARTED));

      bg.start();
      Wait.assertEquals(1, () -> notifListener.count(CoreNotificationType.BROADCAST_GROUP_STARTED), 5000, 100);

      Notification notif = notifListener.findAny(CoreNotificationType.BROADCAST_GROUP_STARTED);
      assertEquals(bg.getName(), notif.getProperties().getSimpleStringProperty(SimpleString.of("name")).toString());

      bg.stop();

      Wait.assertEquals(2, () -> notifListener.count(CoreNotificationType.BROADCAST_GROUP_STARTED, CoreNotificationType.BROADCAST_GROUP_STOPPED), 5000, 100);
      notif = notifListener.findAny(CoreNotificationType.BROADCAST_GROUP_STOPPED);
      assertEquals(CoreNotificationType.BROADCAST_GROUP_STOPPED, notif.getType());
      assertEquals(bg.getName(), notif.getProperties().getSimpleStringProperty(SimpleString.of("name")).toString());
   }
}

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

import java.io.File;
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
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * This will test Discovery test on JGroups and UDP.
 * <br>
 * In some configurations IPV6 may be a challenge. To make sure this test works, you may add this
 * property to your JVM settings: {@literal -Djgroups.bind_addr=::1}
 * <br>
 * Or ultimately you may also turn off IPV6: {@literal -Djava.net.preferIPv4Stack=true}
 * <br>
 * Note when you are not sure about your IP settings of your test machine, you should make sure
 * that the jgroups.bind_addr and java.net.preferXXStack by defining them explicitly, for example
 * if you would like to use IPV6, set BOTH properties to your JVM like the following:
 * -Djgroups.bind_addr=::1 -Djava.net.preferIPv6Addresses=true
 * <br>
 * or if you prefer IPV4:
 * -Djgroups.bind_addr=localhost -Djava.net.preferIPv4Stack=true
 * <br>
 * Also: Make sure you add integration-tests/src/tests/resources to your project path on the
 * tests/integration-tests
 */
public class DiscoveryTest extends DiscoveryBaseTest {

   private static final String TEST_JGROUPS_CONF_FILE = "test-jgroups-file_ping.xml";

   BroadcastGroup bg = null, bg1 = null, bg2 = null, bg3 = null;
   DiscoveryGroup dg = null, dg1 = null, dg2 = null, dg3 = null;

   @Before
   public void prepareLoopback() {
      JChannelManager.getInstance().setLoopbackMessages(true);
   }

   @Override
   @After
   public void tearDown() throws Exception {
      JChannelManager.getInstance().clear().setLoopbackMessages(false);
      /** This file path is defined at {@link #TEST_JGROUPS_CONF_FILE} */
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

      final String nodeID = RandomUtil.randomString();

      bg = newBroadcast(nodeID, RandomUtil.randomString(), null, -1, groupAddress, groupPort);

      bg.start();

      TransportConfiguration live1 = generateTC();

      bg.addConnector(live1);

      dg = newDiscoveryGroup(RandomUtil.randomString(), RandomUtil.randomString(), null, groupAddress, groupPort, timeout);

      dg.start();

      verifyBroadcast(bg, dg);

      List<DiscoveryEntry> entries = dg.getDiscoveryEntries();
      assertEqualsDiscoveryEntries(Arrays.asList(live1), entries);
   }

   @Test
   public void testSimpleBroadcastJGropus() throws Exception {
      final String nodeID = RandomUtil.randomString();

      bg = new BroadcastGroupImpl(new FakeNodeManager(nodeID), "broadcast", 100, null, new JGroupsFileBroadcastEndpointFactory().setChannelName("tst").setFile(TEST_JGROUPS_CONF_FILE));

      bg.start();

      TransportConfiguration live1 = generateTC();

      bg.addConnector(live1);

      dg = new DiscoveryGroup(nodeID + "1", "broadcast", 5000L, new JGroupsFileBroadcastEndpointFactory().setChannelName("tst").setFile(TEST_JGROUPS_CONF_FILE), null);

      dg.start();

      verifyBroadcast(bg, dg);
      List<DiscoveryEntry> entries = dg.getDiscoveryEntries();
      assertEqualsDiscoveryEntries(Arrays.asList(live1), entries);
   }

   /**
    * Create one broadcaster and 100 receivers. Make sure broadcasting works.
    * Then stop 99 of the receivers, the last one could still be working.
    *
    * @throws Exception
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
    * Create one broadcaster and 50 receivers. Make sure broadcasting works.
    * Then stop all of the receivers, and create 50 new ones. Make sure the
    * 50 new ones are receiving data from the broadcasting.
    *
    * @throws Exception
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
    * Create one broadcaster and 50 receivers. Then stop half of the receivers.
    * Then add the half back, plus some more. Make sure all receivers receive data.
    *
    * @throws Exception
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

         instanceLog.debug("BTReceived = " + Arrays.toString(btreceived));

         assertNotNull(btreceived);

         assertEquals(randomBytes.length, btreceived.length);

         for (int i = 0; i < randomBytes.length; i++) {
            assertEquals(randomBytes[i], btreceived[i]);
         }
      } finally {
         try {
            if (broadcaster != null)
               broadcaster.close(true);

            if (client != null)
               client.close(false);
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

      final String nodeID = RandomUtil.randomString();

      // We need to choose a real NIC on the local machine - note this will silently pass if the machine
      // has no usable NIC!

      Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();

      InetAddress localAddress = InetAddress.getLoopbackAddress();

      instanceLog.debug("Local address is " + localAddress);

      bg = newBroadcast(nodeID, RandomUtil.randomString(), localAddress, -1, groupAddress, groupPort);

      bg.start();

      TransportConfiguration live1 = generateTC();

      bg.addConnector(live1);

      dg = newDiscoveryGroup(RandomUtil.randomString(), RandomUtil.randomString(), localAddress, groupAddress, groupPort, timeout);

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

      final String nodeID = RandomUtil.randomString();

      bg = newBroadcast(nodeID, RandomUtil.randomString(), null, -1, groupAddress, groupPort);

      bg.start();

      TransportConfiguration live1 = generateTC();

      bg.addConnector(live1);

      dg = newDiscoveryGroup(RandomUtil.randomString(), RandomUtil.randomString(), null, groupAddress, groupPort, timeout);

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

      String nodeID = RandomUtil.randomString();

      bg = newBroadcast(nodeID, RandomUtil.randomString(), null, -1, groupAddress, groupPort);

      bg.start();

      TransportConfiguration live1 = generateTC();

      bg.addConnector(live1);

      dg = newDiscoveryGroup(nodeID, RandomUtil.randomString(), null, groupAddress, groupPort, timeout);

      dg.start();

      verifyNonBroadcast(bg, dg);
      List<DiscoveryEntry> entries = dg.getDiscoveryEntries();

      Assert.assertNotNull(entries);

      Assert.assertEquals(0, entries.size());

   }

   @Test
   public void testSimpleBroadcastDifferentPort() throws Exception {
      final InetAddress groupAddress = InetAddress.getByName(getUDPDiscoveryAddress());
      final int groupPort = getUDPDiscoveryPort();
      final int timeout = 500;

      bg = newBroadcast(RandomUtil.randomString(), RandomUtil.randomString(), null, -1, groupAddress, groupPort);

      bg.start();

      TransportConfiguration live1 = generateTC();

      bg.addConnector(live1);

      final int port2 = getUDPDiscoveryPort(1);

      dg = newDiscoveryGroup(RandomUtil.randomString(), RandomUtil.randomString(), null, groupAddress, port2, timeout);

      dg.start();

      verifyNonBroadcast(bg, dg);
   }

   @Test
   public void testSimpleBroadcastDifferentAddressAndPort() throws Exception {
      final InetAddress groupAddress = InetAddress.getByName(address1);
      final int groupPort = getUDPDiscoveryPort();
      final int timeout = 500;

      bg = newBroadcast(RandomUtil.randomString(), RandomUtil.randomString(), null, -1, groupAddress, groupPort);

      bg.start();

      TransportConfiguration live1 = generateTC();

      bg.addConnector(live1);

      final InetAddress groupAddress2 = InetAddress.getByName(address2);
      final int port2 = getUDPDiscoveryPort(1);

      dg = newDiscoveryGroup(RandomUtil.randomString(), RandomUtil.randomString(), null, groupAddress2, port2, timeout);

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

      bg1 = newBroadcast(node1, RandomUtil.randomString(), null, -1, groupAddress1, groupPort1);

      bg2 = newBroadcast(node2, RandomUtil.randomString(), null, -1, groupAddress2, groupPort2);

      bg3 = newBroadcast(node3, RandomUtil.randomString(), null, -1, groupAddress3, groupPort3);

      bg2.start();
      bg1.start();
      bg3.start();

      TransportConfiguration live1 = generateTC("live1");

      TransportConfiguration live2 = generateTC("live2");

      TransportConfiguration live3 = generateTC("live3");

      bg1.addConnector(live1);
      bg2.addConnector(live2);
      bg3.addConnector(live3);

      dg1 = newDiscoveryGroup("group-1::" + RandomUtil.randomString(), "group-1::" + RandomUtil.randomString(), null, groupAddress1, groupPort1, timeout);
      dg1.start();

      dg2 = newDiscoveryGroup("group-2::" + RandomUtil.randomString(), "group-2::" + RandomUtil.randomString(), null, groupAddress2, groupPort2, timeout);
      dg2.start();

      dg3 = newDiscoveryGroup("group-3::" + RandomUtil.randomString(), "group-3::" + RandomUtil.randomString(), null, groupAddress3, groupPort3, timeout);
      dg3.start();

      bg1.broadcastConnectors();

      bg2.broadcastConnectors();

      bg3.broadcastConnectors();

      boolean ok = dg1.waitForBroadcast(timeout);
      Assert.assertTrue(ok);
      List<DiscoveryEntry> entries = dg1.getDiscoveryEntries();
      assertEqualsDiscoveryEntries(Arrays.asList(live1), entries);

      ok = dg2.waitForBroadcast(timeout);
      Assert.assertTrue(ok);
      entries = dg2.getDiscoveryEntries();
      assertEqualsDiscoveryEntries(Arrays.asList(live2), entries);

      ok = dg3.waitForBroadcast(timeout);
      Assert.assertTrue(ok);
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
   // Assert.assertTrue(ok);
   // }

   @Test
   public void testDiscoveryListenersCalled() throws Exception {
      final InetAddress groupAddress = InetAddress.getByName(address1);
      final int groupPort = getUDPDiscoveryPort();
      final int timeout = 500;

      String nodeID = RandomUtil.randomString();

      bg = newBroadcast(nodeID, RandomUtil.randomString(), null, -1, groupAddress, groupPort);

      bg.start();

      TransportConfiguration live1 = generateTC();

      bg.addConnector(live1);

      dg = newDiscoveryGroup(RandomUtil.randomString(), RandomUtil.randomString(), null, groupAddress, groupPort, timeout);

      MyListener listener1 = new MyListener();
      MyListener listener2 = new MyListener();
      MyListener listener3 = new MyListener();

      dg.registerListener(listener1);
      dg.registerListener(listener2);
      dg.registerListener(listener3);

      dg.start();

      verifyBroadcast(bg, dg);

      Assert.assertTrue(listener1.called);
      Assert.assertTrue(listener2.called);
      Assert.assertTrue(listener3.called);

      listener1.called = false;
      listener2.called = false;
      listener3.called = false;

      verifyBroadcast(bg, dg);

      // Won't be called since connectors haven't changed
      Assert.assertFalse(listener1.called);
      Assert.assertFalse(listener2.called);
      Assert.assertFalse(listener3.called);
   }

   @Test
   public void testConnectorsUpdatedMultipleBroadcasters() throws Exception {
      final InetAddress groupAddress = InetAddress.getByName(address1);
      final int groupPort = getUDPDiscoveryPort();
      final int timeout = 500;

      String node1 = RandomUtil.randomString();
      String node2 = RandomUtil.randomString();
      String node3 = RandomUtil.randomString();

      bg1 = newBroadcast(node1, RandomUtil.randomString(), null, -1, groupAddress, groupPort);
      bg1.start();

      bg2 = newBroadcast(node2, RandomUtil.randomString(), null, -1, groupAddress, groupPort);
      bg2.start();

      bg3 = newBroadcast(node3, RandomUtil.randomString(), null, -1, groupAddress, groupPort);
      bg3.start();

      TransportConfiguration live1 = generateTC();
      bg1.addConnector(live1);

      TransportConfiguration live2 = generateTC();
      bg2.addConnector(live2);

      TransportConfiguration live3 = generateTC();
      bg3.addConnector(live3);

      dg = newDiscoveryGroup(RandomUtil.randomString(), RandomUtil.randomString(), null, groupAddress, groupPort, timeout);

      MyListener listener1 = new MyListener();
      dg.registerListener(listener1);
      MyListener listener2 = new MyListener();
      dg.registerListener(listener2);

      dg.start();

      verifyBroadcast(bg1, dg);
      List<DiscoveryEntry> entries = dg.getDiscoveryEntries();
      assertEqualsDiscoveryEntries(Arrays.asList(live1), entries);
      Assert.assertTrue(listener1.called);
      Assert.assertTrue(listener2.called);
      listener1.called = false;
      listener2.called = false;

      verifyBroadcast(bg2, dg);
      entries = dg.getDiscoveryEntries();
      assertEqualsDiscoveryEntries(Arrays.asList(live1, live2), entries);
      Assert.assertTrue(listener1.called);
      Assert.assertTrue(listener2.called);
      listener1.called = false;
      listener2.called = false;

      verifyBroadcast(bg3, dg);
      entries = dg.getDiscoveryEntries();
      assertEqualsDiscoveryEntries(Arrays.asList(live1, live2, live3), entries);
      Assert.assertTrue(listener1.called);
      Assert.assertTrue(listener2.called);
      listener1.called = false;
      listener2.called = false;

      verifyBroadcast(bg1, dg);
      entries = dg.getDiscoveryEntries();
      assertEqualsDiscoveryEntries(Arrays.asList(live1, live2, live3), entries);
      Assert.assertFalse(listener1.called);
      Assert.assertFalse(listener2.called);
      listener1.called = false;
      listener2.called = false;

      verifyBroadcast(bg2, dg);
      entries = dg.getDiscoveryEntries();
      assertEqualsDiscoveryEntries(Arrays.asList(live1, live2, live3), entries);
      Assert.assertFalse(listener1.called);
      Assert.assertFalse(listener2.called);
      listener1.called = false;
      listener2.called = false;

      verifyBroadcast(bg3, dg);
      entries = dg.getDiscoveryEntries();
      assertEqualsDiscoveryEntries(Arrays.asList(live1, live2, live3), entries);
      Assert.assertFalse(listener1.called);
      Assert.assertFalse(listener2.called);
      listener1.called = false;
      listener2.called = false;

      bg2.removeConnector(live2);
      verifyBroadcast(bg2, dg);

      // Connector2 should still be there since not timed out yet

      entries = dg.getDiscoveryEntries();
      assertEqualsDiscoveryEntries(Arrays.asList(live1, live2, live3), entries);
      Assert.assertFalse(listener1.called);
      Assert.assertFalse(listener2.called);
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
      Assert.assertTrue(listener1.called);
      Assert.assertTrue(listener2.called);
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
      Assert.assertNotNull(entries);
      Assert.assertEquals(0, entries.size());
      Assert.assertTrue(listener1.called);
      Assert.assertTrue(listener2.called);
      listener1.called = false;
      listener2.called = false;

      bg1.broadcastConnectors();
      ok = dg.waitForBroadcast(1000);
      bg2.broadcastConnectors();
      ok = dg.waitForBroadcast(1000);
      bg3.broadcastConnectors();
      ok = dg.waitForBroadcast(1000);

      entries = dg.getDiscoveryEntries();
      Assert.assertNotNull(entries);
      Assert.assertEquals(0, entries.size());
      Assert.assertFalse(listener1.called);
      Assert.assertFalse(listener2.called);
   }

   @Test
   public void testMultipleDiscoveryGroups() throws Exception {
      final InetAddress groupAddress = InetAddress.getByName(address1);
      final int groupPort = getUDPDiscoveryPort();
      final int timeout = 500;

      String nodeID = RandomUtil.randomString();

      bg = newBroadcast(nodeID, RandomUtil.randomString(), null, -1, groupAddress, groupPort);

      bg.start();

      TransportConfiguration live1 = generateTC();

      bg.addConnector(live1);

      dg1 = newDiscoveryGroup(RandomUtil.randomString(), RandomUtil.randomString(), null, groupAddress, groupPort, timeout);

      dg2 = newDiscoveryGroup(RandomUtil.randomString(), RandomUtil.randomString(), null, groupAddress, groupPort, timeout);

      dg3 = newDiscoveryGroup(RandomUtil.randomString(), RandomUtil.randomString(), null, groupAddress, groupPort, timeout);

      dg1.start();
      dg2.start();
      dg3.start();

      bg.broadcastConnectors();

      boolean ok = dg1.waitForBroadcast(1000);
      Assert.assertTrue(ok);
      List<DiscoveryEntry> entries = dg1.getDiscoveryEntries();
      assertEqualsDiscoveryEntries(Arrays.asList(live1), entries);

      ok = dg2.waitForBroadcast(1000);
      Assert.assertTrue(ok);
      entries = dg2.getDiscoveryEntries();
      assertEqualsDiscoveryEntries(Arrays.asList(live1), entries);

      ok = dg3.waitForBroadcast(1000);
      Assert.assertTrue(ok);
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

      dg = newDiscoveryGroup(RandomUtil.randomString(), RandomUtil.randomString(), null, groupAddress, groupPort, timeout, notifService);

      Assert.assertEquals(0, notifListener.getNotifications().size());

      dg.start();

      Assert.assertEquals(1, notifListener.getNotifications().size());
      Notification notif = notifListener.getNotifications().get(0);
      Assert.assertEquals(CoreNotificationType.DISCOVERY_GROUP_STARTED, notif.getType());
      Assert.assertEquals(dg.getName(), notif.getProperties().getSimpleStringProperty(new SimpleString("name")).toString());

      dg.stop();

      Assert.assertEquals(2, notifListener.getNotifications().size());
      notif = notifListener.getNotifications().get(1);
      Assert.assertEquals(CoreNotificationType.DISCOVERY_GROUP_STOPPED, notif.getType());
      Assert.assertEquals(dg.getName(), notif.getProperties().getSimpleStringProperty(new SimpleString("name")).toString());
   }

   @Test
   public void testBroadcastGroupNotifications() throws Exception {
      SimpleNotificationService notifService = new SimpleNotificationService();
      SimpleNotificationService.Listener notifListener = new SimpleNotificationService.Listener();
      notifService.addNotificationListener(notifListener);

      final InetAddress groupAddress = InetAddress.getByName(address1);
      final int groupPort = getUDPDiscoveryPort();

      bg = newBroadcast(RandomUtil.randomString(), RandomUtil.randomString(), null, -1, groupAddress, groupPort);

      bg.setNotificationService(notifService);

      Assert.assertEquals(0, notifListener.getNotifications().size());

      bg.start();

      Assert.assertEquals(1, notifListener.getNotifications().size());
      Notification notif = notifListener.getNotifications().get(0);
      Assert.assertEquals(CoreNotificationType.BROADCAST_GROUP_STARTED, notif.getType());
      Assert.assertEquals(bg.getName(), notif.getProperties().getSimpleStringProperty(new SimpleString("name")).toString());

      bg.stop();

      Assert.assertEquals(2, notifListener.getNotifications().size());
      notif = notifListener.getNotifications().get(1);
      Assert.assertEquals(CoreNotificationType.BROADCAST_GROUP_STOPPED, notif.getType());
      Assert.assertEquals(bg.getName(), notif.getProperties().getSimpleStringProperty(new SimpleString("name")).toString());
   }
}

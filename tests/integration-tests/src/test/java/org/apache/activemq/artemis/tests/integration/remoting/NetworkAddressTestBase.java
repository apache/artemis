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
package org.apache.activemq.artemis.tests.integration.remoting;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public abstract class NetworkAddressTestBase extends ActiveMQTestBase {


   static {
      try {
         Map<NetworkInterface, InetAddress> map = NetworkAddressTestBase.getAddressForEachNetworkInterface();
         StringBuilder s = new StringBuilder("using network settings:\n");
         Set<Entry<NetworkInterface, InetAddress>> set = map.entrySet();
         for (Entry<NetworkInterface, InetAddress> entry : set) {
            s.append(entry.getKey().getDisplayName() + ": " + entry.getValue().getHostAddress() + "\n");
         }
      } catch (Exception e) {
         e.printStackTrace();
      }

   }

   public static Map<NetworkInterface, InetAddress> getAddressForEachNetworkInterface() throws Exception {
      Map<NetworkInterface, InetAddress> map = new HashMap<>();
      Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
      while (ifaces.hasMoreElements()) {
         NetworkInterface iface = ifaces.nextElement();
         Enumeration<InetAddress> enumeration = iface.getInetAddresses();
         while (enumeration.hasMoreElements()) {
            InetAddress inetAddress = enumeration.nextElement();
            if (inetAddress instanceof Inet4Address) {
               map.put(iface, inetAddress);
               break;
            }
         }
      }

      return map;
   }



   @Test
   public void testConnectToServerWithSameHost() throws Exception {
      Map<NetworkInterface, InetAddress> map = NetworkAddressTestBase.getAddressForEachNetworkInterface();
      if (!map.isEmpty()) {
         Set<Entry<NetworkInterface, InetAddress>> set = map.entrySet();
         Iterator<Entry<NetworkInterface, InetAddress>> iterator = set.iterator();
         InetAddress address = iterator.next().getValue();
         String host = address.getHostAddress();
         testConnection(host, host, true, 0);
      }
   }

   @Test
   public void testConnectToServerAcceptingAllHosts() throws Exception {
      Map<NetworkInterface, InetAddress> map = NetworkAddressTestBase.getAddressForEachNetworkInterface();
      Set<Entry<NetworkInterface, InetAddress>> set = map.entrySet();
      for (Entry<NetworkInterface, InetAddress> entry : set) {
         String host = entry.getValue().getHostAddress();
         testConnection("0.0.0.0", host, true, 0);
      }
   }

   @Test
   public void testConnectToServerAcceptingOnlyAnotherHost() throws Exception {
      Map<NetworkInterface, InetAddress> map = NetworkAddressTestBase.getAddressForEachNetworkInterface();
      if (map.size() <= 1) {
         System.err.println("There must be at least 1 network interfaces: test will not be executed");
         return;
      }

      Set<Entry<NetworkInterface, InetAddress>> set = map.entrySet();
      Iterator<Entry<NetworkInterface, InetAddress>> iterator = set.iterator();
      Entry<NetworkInterface, InetAddress> acceptorEntry = iterator.next();
      Entry<NetworkInterface, InetAddress> connectorEntry = iterator.next();

      testConnection(acceptorEntry.getValue().getHostName(), connectorEntry.getValue().getHostAddress(), false, 0);
   }

   @Test
   public void testConnectToServerUsingLocalPort() throws Exception {
      Map<NetworkInterface, InetAddress> map = NetworkAddressTestBase.getAddressForEachNetworkInterface();
      if (map.size() <= 1) {
         System.err.println("There must be at least 1 network interfaces: test will not be executed");
         return;
      }

      Set<Entry<NetworkInterface, InetAddress>> set = map.entrySet();
      Iterator<Entry<NetworkInterface, InetAddress>> iterator = set.iterator();
      Entry<NetworkInterface, InetAddress> entry = iterator.next();
      String host = entry.getValue().getHostAddress();

      testConnection(host, host, true, 7777);
   }

   @Test
   public void testConnectorToServerAcceptingAListOfHosts() throws Exception {
      Map<NetworkInterface, InetAddress> map = NetworkAddressTestBase.getAddressForEachNetworkInterface();
      if (map.size() <= 1) {
         System.err.println("There must be at least 2 network interfaces: test will not be executed");
         return;
      }

      Set<Entry<NetworkInterface, InetAddress>> set = map.entrySet();
      Iterator<Entry<NetworkInterface, InetAddress>> iterator = set.iterator();
      Entry<NetworkInterface, InetAddress> entry1 = iterator.next();
      Entry<NetworkInterface, InetAddress> entry2 = iterator.next();

      String listOfHosts = entry1.getValue().getHostAddress() + ", " + entry2.getValue().getHostAddress();

      testConnection(listOfHosts, entry1.getValue().getHostAddress(), true, 0);
      testConnection(listOfHosts, entry2.getValue().getHostAddress(), true, 0);
   }

   @Test
   public void testConnectorToServerAcceptingAListOfHosts_2() throws Exception {
      Map<NetworkInterface, InetAddress> map = NetworkAddressTestBase.getAddressForEachNetworkInterface();
      if (map.size() <= 2) {
         System.err.println("There must be at least 3 network interfaces: test will not be executed");
         return;
      }

      Set<Entry<NetworkInterface, InetAddress>> set = map.entrySet();
      Iterator<Entry<NetworkInterface, InetAddress>> iterator = set.iterator();
      Entry<NetworkInterface, InetAddress> entry1 = iterator.next();
      Entry<NetworkInterface, InetAddress> entry2 = iterator.next();
      Entry<NetworkInterface, InetAddress> entry3 = iterator.next();

      String listOfHosts = entry1.getValue().getHostAddress() + ", " + entry2.getValue().getHostAddress();

      testConnection(listOfHosts, entry1.getValue().getHostAddress(), true, 0);
      testConnection(listOfHosts, entry2.getValue().getHostAddress(), true, 0);
      testConnection(listOfHosts, entry3.getValue().getHostAddress(), false, 0);
   }

   public void testConnection(final String acceptorHost,
                              final String connectorHost,
                              final boolean mustConnect,
                              final int localPort) throws Exception {
      Map<String, Object> params = new HashMap<>();
      params.put(getHostPropertyKey(), acceptorHost);
      TransportConfiguration acceptorConfig = new TransportConfiguration(getAcceptorFactoryClassName(), params);
      Set<TransportConfiguration> transportConfigs = new HashSet<>();
      transportConfigs.add(acceptorConfig);

      Configuration config = createDefaultNettyConfig();
      config.setAcceptorConfigurations(transportConfigs);
      ActiveMQServer messagingService = createServer(false, config);
      try {
         messagingService.start();

         params = new HashMap<>();
         params.put(getHostPropertyKey(), connectorHost);
         if (localPort != 0) {
            params.put(getLocalPortProperty(), localPort);
         }
         TransportConfiguration connectorConfig = new TransportConfiguration(getConnectorFactoryClassName(), params);
         ServerLocator locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(connectorConfig));

         if (mustConnect) {
            ClientSessionFactory sf = createSessionFactory(locator);
            if (localPort != 0) {
               Iterator<RemotingConnection> iterator = messagingService.getRemotingService().getConnections().iterator();
               assertTrue(iterator.hasNext(), "no connection created");
               String address = iterator.next().getTransportConnection().getRemoteAddress();
               assertTrue(address.endsWith(":" + localPort));
            }
            sf.close();
         } else {
            try {
               locator.createSessionFactory();
               fail("session creation must fail because connector must not be able to connect to the server bound to another network interface");
            } catch (Exception e) {
            }
         }
      } finally {
         messagingService.stop();
      }
   }



   protected abstract String getAcceptorFactoryClassName();

   protected abstract String getConnectorFactoryClassName();

   protected abstract String getHostPropertyKey();

   protected abstract String getLocalPortProperty();



}

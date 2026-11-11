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
package org.apache.activemq.artemis.api.core;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.core.client.ActiveMQClientLogger;
import org.apache.activemq.artemis.utils.RandomUtil;

/**
 * The configuration used to determine how the server will broadcast members.
 * <p>
 * This is analogous to {@link DiscoveryGroupConfiguration}
 */
public final class UDPBroadcastEndpointFactory implements BroadcastEndpointFactory {

   // You can specify a property as a default. This is useful for testsuite running.
   // for that reason we won't document this property as the proper way to do it is through configuration.
   // these property names can change at any time, so don't use this on production systems
   private transient String localBindAddress = getProperty(UDPBroadcastEndpointFactory.class.getName() + ".localBindAddress", null);

   private transient int localBindPort = -1;

   private String groupAddress = null;

   private int groupPort = -1;

   public UDPBroadcastEndpointFactory() {
   }

   @Override
   public BroadcastEndpoint createBroadcastEndpoint() throws Exception {
      return new UDPBroadcastEndpoint().setGroupAddress(groupAddress != null ? InetAddress.getByName(groupAddress) : null).setGroupPort(groupPort).setLocalBindAddress(localBindAddress != null ? InetAddress.getByName(localBindAddress) : null).setLocalBindPort(localBindPort);
   }

   public String getGroupAddress() {
      return groupAddress;
   }

   public UDPBroadcastEndpointFactory setGroupAddress(String groupAddress) {
      this.groupAddress = groupAddress;
      return this;
   }

   public int getGroupPort() {
      return groupPort;
   }

   public UDPBroadcastEndpointFactory setGroupPort(int groupPort) {
      this.groupPort = groupPort;
      return this;
   }

   public int getLocalBindPort() {
      return localBindPort;
   }

   public UDPBroadcastEndpointFactory setLocalBindPort(int localBindPort) {
      this.localBindPort = localBindPort;
      return this;
   }

   public String getLocalBindAddress() {
      return localBindAddress;
   }

   public UDPBroadcastEndpointFactory setLocalBindAddress(String localBindAddress) {
      this.localBindAddress = localBindAddress;
      return this;
   }

   /**
    * This is the member discovery implementation using direct UDP. It was extracted as a refactoring from
    * {@link org.apache.activemq.artemis.core.cluster.DiscoveryGroup}
    */
   private static class UDPBroadcastEndpoint implements BroadcastEndpoint {

      private static final int SOCKET_TIMEOUT = 500;

      private InetAddress localAddress;

      private int localBindPort;

      private InetAddress groupAddress;

      private int groupPort;

      private DatagramSocket broadcastingSocket;

      private MulticastSocket receivingSocket;

      private volatile boolean open;

      private UDPBroadcastEndpoint() {
      }

      public UDPBroadcastEndpoint setGroupAddress(InetAddress groupAddress) {
         this.groupAddress = groupAddress;
         return this;
      }

      public UDPBroadcastEndpoint setGroupPort(int groupPort) {
         this.groupPort = groupPort;
         return this;
      }

      public UDPBroadcastEndpoint setLocalBindAddress(InetAddress localAddress) {
         this.localAddress = localAddress;
         return this;
      }

      public UDPBroadcastEndpoint setLocalBindPort(int localBindPort) {
         this.localBindPort = localBindPort;
         return this;
      }

      @Override
      public void broadcast(byte[] data) throws Exception {
         DatagramPacket packet = new DatagramPacket(data, data.length, groupAddress, groupPort);
         broadcastingSocket.send(packet);
      }

      @Override
      public byte[] receiveBroadcast() throws Exception {
         final byte[] data = new byte[65535];
         final DatagramPacket packet = new DatagramPacket(data, data.length);

         while (open) {
            try {
               receivingSocket.receive(packet);
            } catch (InterruptedIOException e) {
               // TODO: Do we need this?
               continue;
            } catch (IOException e) {
               if (open) {
                  ActiveMQClientLogger.LOGGER.unableToReceiveBroadcast(this.toString(), e);
               }
            }
            break;
         }
         return data;
      }

      @Override
      public byte[] receiveBroadcast(long time, TimeUnit unit) throws Exception {
         // We just use the regular method on UDP, there's no timeout support
         // and this is basically for tests only
         return receiveBroadcast();
      }

      @Override
      public void openBroadcaster() throws Exception {
         if (localBindPort != -1) {
            broadcastingSocket = new DatagramSocket(localBindPort, localAddress);
         } else {
            if (localAddress != null) {
               for (int i = 0; i < 100; i++) {
                  int nextPort = RandomUtil.randomInterval(3000, 4000);
                  try {
                     broadcastingSocket = new DatagramSocket(nextPort, localAddress);
                     ActiveMQClientLogger.LOGGER.broadcastGroupBindError(localAddress.toString() + ":" + nextPort);
                     break;
                  } catch (Exception e) {
                     ActiveMQClientLogger.LOGGER.broadcastGroupBindErrorRetry(localAddress.toString() + ":" + nextPort, e);
                  }
               }
            }
            if (broadcastingSocket == null) {
               broadcastingSocket = new DatagramSocket();
            }
         }

         open = true;
      }

      @Override
      public void openClient() throws Exception {
         if (checkForLinux() || checkForSolaris() || checkForHp()) {
            try {
               receivingSocket = new MulticastSocket(new InetSocketAddress(groupAddress, groupPort));
            } catch (IOException e) {
               ActiveMQClientLogger.LOGGER.ioDiscoveryError(groupAddress.getHostAddress(), groupAddress instanceof Inet4Address ? "IPv4" : "IPv6");

               receivingSocket = new MulticastSocket(groupPort);
            }
         } else {
            receivingSocket = new MulticastSocket(groupPort);
         }

         if (localAddress != null) {
            receivingSocket.setInterface(localAddress);
         }

         receivingSocket.joinGroup(groupAddress);

         receivingSocket.setSoTimeout(SOCKET_TIMEOUT);

         open = true;
      }

      //@Todo: using isBroadcast to share endpoint between broadcast and receiving
      @Override
      public void close(boolean isBroadcast) throws Exception {
         open = false;

         if (broadcastingSocket != null) {
            broadcastingSocket.close();
         }

         if (receivingSocket != null) {
            receivingSocket.close();
         }
      }

      private static boolean checkForLinux() {
         return checkForPresence("os.name", "linux");
      }

      private static boolean checkForHp() {
         return checkForPresence("os.name", "hp");
      }

      private static boolean checkForSolaris() {
         return checkForPresence("os.name", "sun");
      }

      private static boolean checkForPresence(String key, String value) {
         String tmp = getProperty(key, null);
         return tmp != null && tmp.trim().toLowerCase().startsWith(value);
      }

   }

   private static String getProperty(String key, String defaultValue) {
      try {
         String tmp = System.getProperty(key);
         if (tmp == null) {
            tmp = defaultValue;
         }
         return tmp;
      } catch (Throwable t) {
         ActiveMQClientLogger.LOGGER.unableToGetProperty(t);
         return defaultValue;
      }
   }

   private static int getIntProperty(String key, String defaultValue) {
      String value = getProperty(key, defaultValue);

      try {
         return Integer.parseInt(value);
      } catch (Throwable t) {
         ActiveMQClientLogger.LOGGER.unableToParseValue(t);
         return Integer.parseInt(defaultValue);
      }

   }

   @Override
   public int hashCode() {
      return Objects.hash(groupAddress, groupPort);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!(obj instanceof UDPBroadcastEndpointFactory other)) {
         return false;
      }

      return Objects.equals(groupAddress, other.groupAddress) &&
             groupPort == other.groupPort;
   }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.server.management;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;

public class RmiRegistryFactory {

   private int port = Registry.REGISTRY_PORT;
   private Registry registry;
   private String host;
   private HostLimitedServerSocketFactory socketFactory;

   public int getPort() {
      return port;
   }

   public void setPort(int port) {
      this.port = port;
   }

   public String getHost() {
      return host;
   }

   public void setHost(String host) {
      this.host = host;
   }

   ServerSocket createTestSocket() throws IOException {
      return socketFactory.createServerSocket(0);
   }

   public Object getObject() throws Exception {
      return registry;
   }

   class HostLimitedServerSocketFactory implements RMIServerSocketFactory {
      @Override
      public ServerSocket createServerSocket(int port) throws IOException {
         InetAddress hostAddress;
         if (host != null) {
            hostAddress = InetAddress.getByName(host);
         } else {
            hostAddress = null; // accept connections on all local addresses
         }
         return ServerSocketFactory.getDefault().createServerSocket(port, 0, hostAddress);
      }
   }

   private static class PassThroughToDefaultSocketFactory implements RMIClientSocketFactory {
      @Override
      public Socket createSocket(String host, int port) throws IOException {
         return SocketFactory.getDefault().createSocket(host, port);
      }
   }

   public void init() throws RemoteException, UnknownHostException {
      socketFactory = new HostLimitedServerSocketFactory();
      registry = LocateRegistry.createRegistry(port, new PassThroughToDefaultSocketFactory(), socketFactory);
   }

   public void destroy() throws RemoteException {
      if (registry != null) {
         UnicastRemoteObject.unexportObject(registry, true);
      }
   }
}

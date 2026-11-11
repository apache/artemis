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
package org.apache.activemq.artemis.tests.util;

import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants;

public final class TransportConfigurationUtils {

   private TransportConfigurationUtils() {
      // Utility
   }

   public static TransportConfiguration getInVMAcceptor(final boolean live) {
      return transportConfiguration(ActiveMQTestBase.INVM_ACCEPTOR_FACTORY, live);
   }

   public static TransportConfiguration getInVMConnector(final boolean live) {
      return transportConfiguration(ActiveMQTestBase.INVM_CONNECTOR_FACTORY, live);
   }

   public static TransportConfiguration getInVMAcceptor(final boolean live, int server) {
      return transportConfiguration(ActiveMQTestBase.INVM_ACCEPTOR_FACTORY, live, server);
   }

   public static TransportConfiguration getInVMConnector(final boolean live, int server) {
      return transportConfiguration(ActiveMQTestBase.INVM_CONNECTOR_FACTORY, live, server);
   }

   public static TransportConfiguration getNettyAcceptor(final boolean live, int server) {
      return transportConfiguration(ActiveMQTestBase.NETTY_ACCEPTOR_FACTORY, live, server);
   }

   public static TransportConfiguration getNettyConnector(final boolean live, int server) {
      return transportConfiguration(ActiveMQTestBase.NETTY_CONNECTOR_FACTORY, live, server);
   }

   public static TransportConfiguration getInVMAcceptor(final boolean live, int server, String name) {
      return transportConfiguration(ActiveMQTestBase.INVM_ACCEPTOR_FACTORY, live, server, name);
   }

   public static TransportConfiguration getInVMConnector(final boolean live, int server, String name) {
      return transportConfiguration(ActiveMQTestBase.INVM_CONNECTOR_FACTORY, live, server, name);
   }

   public static TransportConfiguration getNettyAcceptor(final boolean live, int server, String name) {
      return transportConfiguration(ActiveMQTestBase.NETTY_ACCEPTOR_FACTORY, live, server, name);
   }

   public static TransportConfiguration getNettyConnector(final boolean live, int server, String name) {
      return transportConfiguration(ActiveMQTestBase.NETTY_CONNECTOR_FACTORY, live, server, name);
   }

   private static TransportConfiguration transportConfiguration(String classname, boolean live) {
      if (live) {
         return new TransportConfiguration(classname);
      }

      Map<String, Object> server1Params = new HashMap<>();
      server1Params.put(TransportConstants.SERVER_ID_PROP_NAME, 1);
      return new TransportConfiguration(classname, server1Params);
   }

   private static TransportConfiguration transportConfiguration(String classname, boolean primary, int server) {
      if (classname.contains("netty")) {
         Map<String, Object> serverParams = new HashMap<>();
         Integer port = primary ? 61616 + server : 5545 + server;
         serverParams.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.PORT_PROP_NAME, port);
         return new TransportConfiguration(classname, serverParams);
      }

      Map<String, Object> serverParams = new HashMap<>();
      serverParams.put(TransportConstants.SERVER_ID_PROP_NAME, primary ? server : server + 100);
      return new TransportConfiguration(classname, serverParams);
   }

   private static TransportConfiguration transportConfiguration(String classname,
                                                                boolean primary,
                                                                int server,
                                                                String name) {
      if (classname.contains("netty")) {
         Map<String, Object> serverParams = new HashMap<>();
         Integer port = primary ? 61616 + server : 5545 + server;
         serverParams.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.PORT_PROP_NAME, port);
         return new TransportConfiguration(classname, serverParams, name);
      }

      Map<String, Object> serverParams = new HashMap<>();
      serverParams.put(TransportConstants.SERVER_ID_PROP_NAME, primary ? server : server + 100);
      return new TransportConfiguration(classname, serverParams, name);
   }
}

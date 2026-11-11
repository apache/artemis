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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.activemq.artemis.core.config.JMXConnectorConfiguration;
import org.apache.activemq.artemis.tests.util.ServerTestBase;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

public class JMXRMIRegistryPortTest extends ServerTestBase {

   @Test
   public void explicitLocalhostRegistry() throws IOException {
      RmiRegistryFactory registryFactory = new RmiRegistryFactory();
      registryFactory.setHost("localhost");
      registryFactory.setPort(0);
      registryFactory.init();
      runAfter(registryFactory::destroy);
      try (ServerSocket testSocket = registryFactory.createTestSocket()) {
         assertEquals(InetAddress.getByName("localhost"), testSocket.getInetAddress());
      }
   }

   @Test
   public void unlimitedHostRegistry() throws IOException {
      RmiRegistryFactory registryFactory = new RmiRegistryFactory();
      registryFactory.setHost(null);
      registryFactory.setPort(0);
      registryFactory.init();
      runAfter(registryFactory::destroy);
      try (ServerSocket testSocket = registryFactory.createTestSocket()) {
         assertEquals(InetAddress.getByAddress(new byte[]{0, 0, 0, 0}), testSocket.getInetAddress());
      }
   }

   @Test
   public void defaultRegistry() throws IOException {
      RmiRegistryFactory registryFactory = new RmiRegistryFactory();
      JMXConnectorConfiguration configuration = new JMXConnectorConfiguration();
      registryFactory.setHost(configuration.getConnectorHost());
      registryFactory.setPort(configuration.getConnectorPort());
      registryFactory.init();
      registryFactory.destroy();
      try (ServerSocket testSocket = registryFactory.createTestSocket()) {
         assertEquals(InetAddress.getByName("localhost"), testSocket.getInetAddress());
      }
   }
}
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
package org.apache.activemq.artemis.tests.integration.cluster;

import java.util.concurrent.ScheduledExecutorService;

import org.apache.activemq.artemis.api.core.ActiveMQClusterSecurityException;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorImpl;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.cluster.ActiveMQServerSideProtocolManagerFactory;
import org.apache.activemq.artemis.core.server.cluster.ClusterControl;
import org.apache.activemq.artemis.core.server.cluster.ClusterController;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration.getDefaultClusterPassword;
import static org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration.getDefaultClusterUser;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

public class ClusterControllerDirectAuthTest extends ActiveMQTestBase {

   protected ActiveMQServer server;

   protected ClientSession session;

   protected ClientSessionFactory sf;

   protected ServerLocator locator;

   public void startServer(String clusterUser, String clusterPassword) throws Exception {
      server = createServer(false, createDefaultInVMConfig()
         .setSecurityEnabled(true)
         .setClusterUser(clusterUser)
         .setClusterPassword(clusterPassword)
         .addConnectorConfiguration("fake-connector", "vm://1")
         .addClusterConfiguration(new ClusterConnectionConfiguration()
                                     .setName("fake-cluster-connection")
                                     .setConnectorName("fake-connector")));
      server.start();
   }

   @Test
   public void testDirectClusterAuthWithDefaultCredentials() throws Exception {
      testDirectClusterAuth(getDefaultClusterUser(), getDefaultClusterPassword(), false);
   }

   @Test
   public void testDirectClusterAuthWithCustomCredentials() throws Exception {
      testDirectClusterAuth(RandomUtil.randomUUIDString(), RandomUtil.randomUUIDString(), true);
   }

   private void testDirectClusterAuth(String clusterUser, String clusterPassword, boolean shouldSucceed) throws Exception {
      startServer(clusterUser, clusterPassword);
      try (ServerLocatorImpl locator = (ServerLocatorImpl) createInVMNonHALocator()) {
         locator.setProtocolManagerFactory(ActiveMQServerSideProtocolManagerFactory.getInstance(locator, server.getStorageManager()));
         ClusterController controller = getClusterController(clusterUser, clusterPassword);
         ClusterControl clusterControl = controller.connectToNodeInCluster((ClientSessionFactoryInternal) locator.createSessionFactory());
         if (shouldSucceed) {
            clusterControl.authorize();
         } else {
            try {
               clusterControl.authorize();
               fail("should throw ActiveMQClusterSecurityException");
            } catch (Exception e) {
               assertInstanceOf(ActiveMQClusterSecurityException.class, e, "should throw ActiveMQClusterSecurityException");
            }
         }
      }
   }

   private ClusterController getClusterController(String clusterUser, String clusterPassword) {
      Configuration mockConfig = Mockito.mock(Configuration.class);
      Mockito.when(mockConfig.getClusterUser()).thenReturn(clusterUser);
      Mockito.when(mockConfig.getClusterPassword()).thenReturn(clusterPassword);

      ActiveMQServer mockServer = Mockito.mock(ActiveMQServer.class);
      Mockito.when(mockServer.getExecutorFactory()).thenReturn(Mockito.mock(ExecutorFactory.class));
      Mockito.when(mockServer.getConfiguration()).thenReturn(mockConfig);

      return new ClusterController(mockServer, Mockito.mock(ScheduledExecutorService.class), false);
   }
}

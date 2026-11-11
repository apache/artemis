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
package org.apache.activemq.artemis.tests.integration.cluster.bridge;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.ha.SharedStorePrimaryPolicyConfiguration;
import org.apache.activemq.artemis.core.config.ha.SharedStoreBackupPolicyConfiguration;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnector;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.InVMNodeManagerServer;
import org.junit.jupiter.api.AfterEach;

public abstract class BridgeTestBase extends ActiveMQTestBase {

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      InVMConnector.failOnCreateConnection = false;

      super.tearDown();
   }

   protected ActiveMQServer createActiveMQServer(final int id,
                                                 final boolean netty,
                                                 final Map<String, Object> params) throws Exception {
      return createActiveMQServer(id, params, netty, null);
   }

   protected ActiveMQServer createActiveMQServer(final int id,
                                                 final Map<String, Object> params,
                                                 final boolean netty,
                                                 final NodeManager nodeManager) throws Exception {
      TransportConfiguration tc = new TransportConfiguration();

      if (netty) {
         params.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.PORT_PROP_NAME, org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.DEFAULT_PORT + id);
         tc = new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, params);

      } else {
         params.put(org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants.SERVER_ID_PROP_NAME, id);
         tc = new TransportConfiguration(INVM_ACCEPTOR_FACTORY, params);
      }
      Configuration serviceConf = createBasicConfig().setJournalType(getDefaultJournalType()).setBindingsDirectory(getBindingsDir(id, false)).setJournalMinFiles(2).setJournalDirectory(getJournalDir(id, false)).setPagingDirectory(getPageDir(id, false)).setLargeMessagesDirectory(getLargeMessagesDir(id, false))
         // these tests don't need any big storage so limiting the size of the journal files to speed up the test
         .setJournalFileSize(100 * 1024).addAcceptorConfiguration(tc).setHAPolicyConfiguration(new SharedStorePrimaryPolicyConfiguration());

      ActiveMQServer server;
      if (nodeManager == null) {
         server = ActiveMQServers.newActiveMQServer(serviceConf, true);
      } else {
         server = new InVMNodeManagerServer(serviceConf, nodeManager);
      }

      return addServer(server);
   }

   protected ActiveMQServer createBackupActiveMQServer(final int id,
                                                       final Map<String, Object> params,
                                                       final boolean netty,
                                                       final int primaryId,
                                                       final NodeManager nodeManager) throws Exception {
      TransportConfiguration tc = new TransportConfiguration();

      if (netty) {
         params.put(org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.PORT_PROP_NAME, org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants.DEFAULT_PORT + id);
         tc = new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, params);

      } else {
         params.put(org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants.SERVER_ID_PROP_NAME, id);
         tc = new TransportConfiguration(INVM_ACCEPTOR_FACTORY, params);
      }

      Configuration serviceConf = createBasicConfig().setJournalType(getDefaultJournalType()).setBindingsDirectory(getBindingsDir(primaryId, false)).setJournalMinFiles(2).setJournalDirectory(getJournalDir(primaryId, false)).setPagingDirectory(getPageDir(primaryId, false)).setLargeMessagesDirectory(getLargeMessagesDir(primaryId, false))
         // these tests don't need any big storage so limiting the size of the journal files to speed up the test
         .setJournalFileSize(100 * 1024).addAcceptorConfiguration(tc).setHAPolicyConfiguration(new SharedStoreBackupPolicyConfiguration());

      ActiveMQServer server;
      if (nodeManager == null) {
         server = ActiveMQServers.newActiveMQServer(serviceConf, true);
      } else {
         server = new InVMNodeManagerServer(serviceConf, nodeManager);
      }
      return addServer(server);
   }

   protected void waitForServerStart(ActiveMQServer server) throws Exception {
      if (!server.waitForActivation(5000L, TimeUnit.MILLISECONDS)) {
         throw new IllegalStateException("Timed out waiting for server starting = " + server);
      }
   }
}

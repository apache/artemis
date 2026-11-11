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
package org.apache.activemq.artemis.tests.integration.cluster.failover;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;
import java.util.Collection;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameter;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.integration.cluster.util.TestableServer;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class ReplicatedMultipleServerFailoverTest extends MultipleServerFailoverTestBase {

   @Parameter(index = 0)
   public HAType haType;

   @Parameters(name = "ha={0}")
   public static Collection<Object[]> getParams() {
      return Arrays.asList(new Object[][]{{HAType.SharedNothingReplication}, {HAType.PluggableQuorumReplication}});
   }

   @TestTemplate
   public void testStartPrimaryFirst() throws Exception {
      for (TestableServer liveServer : primaryServers) {
         liveServer.start();
      }
      for (TestableServer backupServer : backupServers) {
         backupServer.start();
      }
      waitForTopology(primaryServers.get(0).getServer(), primaryServers.size(), backupServers.size());
      sendCrashReceive();
   }

   @TestTemplate
   public void testStartBackupFirst() throws Exception {
      for (TestableServer backupServer : backupServers) {
         backupServer.start();
      }
      for (TestableServer liveServer : primaryServers) {
         liveServer.start();
      }
      waitForTopology(primaryServers.get(0).getServer(), primaryServers.size(), primaryServers.size());
      sendCrashReceive();
   }

   protected void sendCrashReceive() throws Exception {
      ServerLocator[] locators = new ServerLocator[primaryServers.size()];
      try {
         for (int i = 0; i < locators.length; i++) {
            locators[i] = getServerLocator(i);
         }

         ClientSessionFactory[] factories = new ClientSessionFactory[primaryServers.size()];
         for (int i = 0; i < factories.length; i++) {
            factories[i] = createSessionFactory(locators[i]);
         }

         ClientSession[] sessions = new ClientSession[primaryServers.size()];
         for (int i = 0; i < factories.length; i++) {
            sessions[i] = createSession(factories[i], true, true);
            sessions[i].createQueue(QueueConfiguration.of(ADDRESS));
         }

         //make sure bindings are ready before sending messages
         for (int i = 0; i < primaryServers.size(); i++) {
            this.waitForBindings(primaryServers.get(i).getServer(), ADDRESS.toString(), true, 1, 0, 2000);
            this.waitForBindings(primaryServers.get(i).getServer(), ADDRESS.toString(), false, 1, 0, 2000);
         }

         ClientProducer producer = sessions[0].createProducer(ADDRESS);

         for (int i = 0; i < primaryServers.size() * 100; i++) {
            ClientMessage message = sessions[0].createMessage(true);

            setBody(i, message);

            message.putIntProperty("counter", i);

            producer.send(message);
         }

         producer.close();

         for (TestableServer liveServer : primaryServers) {
            waitForDistribution(ADDRESS, liveServer.getServer(), 100);
         }

         for (TestableServer liveServer : primaryServers) {
            liveServer.crash();
         }
         ClientConsumer[] consumers = new ClientConsumer[primaryServers.size()];
         for (int i = 0; i < factories.length; i++) {
            consumers[i] = sessions[i].createConsumer(ADDRESS);
            sessions[i].start();
         }

         for (int i = 0; i < 100; i++) {
            for (ClientConsumer consumer : consumers) {
               ClientMessage message = consumer.receive(1000);
               assertNotNull(message, "expecting durable msg " + i);
               message.acknowledge();
            }

         }
      } finally {
         for (ServerLocator locator : locators) {
            if (locator != null) {
               try {
                  locator.close();
               } catch (Exception e) {
                  //ignore
               }
            }
         }
      }
   }

   @Override
   public int getPrimaryServerCount() {
      return 6;
   }

   @Override
   public int getBackupServerCount() {
      return 6;
   }

   @Override
   public boolean isNetty() {
      return true;
   }

   @Override
   public HAType haType() {
      return haType;
   }

   @Override
   public String getNodeGroupName() {
      return "nodeGroup";
   }
}

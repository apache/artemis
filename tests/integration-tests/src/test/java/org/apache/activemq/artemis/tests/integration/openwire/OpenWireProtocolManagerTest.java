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
package org.apache.activemq.artemis.tests.integration.openwire;

import org.apache.activemq.artemis.core.protocol.openwire.OpenWireProtocolManager;
import org.apache.activemq.artemis.core.protocol.openwire.util.OpenWireUtil;
import org.apache.activemq.artemis.core.server.cluster.ClusterManager;
import org.apache.activemq.artemis.core.server.impl.Activation;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.selector.impl.LRUCache;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.command.ActiveMQDestination;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpenWireProtocolManagerTest extends ActiveMQTestBase {

   OpenWireProtocolManager underTest;
   LRUCache lruCacheRef;

   @Test
   public void testVtAutoConversion() {
      underTest = new OpenWireProtocolManager(null, new DummyServer(), null, null) {
         @Override
         public ActiveMQDestination virtualTopicConsumerToFQQN(ActiveMQDestination destination) {
            if (lruCacheRef == null) {
               lruCacheRef = vtDestMapCache;
            }
            return super.virtualTopicConsumerToFQQN(destination);
         }
      };

      final int maxCacheSize = 10;
      underTest.setVirtualTopicConsumerLruCacheMax(10);
      underTest.setVirtualTopicConsumerWildcards("A.>;1;selectorAware=true,B.*.>;2,C.*.*.*.EE;3;selectorAware=false");

      ActiveMQDestination A = new org.apache.activemq.command.ActiveMQQueue("A.SomeTopic");
      assertEquals(new org.apache.activemq.command.ActiveMQQueue("SomeTopic::A.SomeTopic"), underTest.virtualTopicConsumerToFQQN(A));

      ActiveMQDestination checkOption = underTest.virtualTopicConsumerToFQQN(A);
      assertNotNull(checkOption.getOptions());
      assertTrue(Boolean.parseBoolean(checkOption.getOptions().get(OpenWireUtil.SELECTOR_AWARE_OPTION)));

      ActiveMQDestination B = new org.apache.activemq.command.ActiveMQQueue("B.b.SomeTopic.B");
      assertEquals(new org.apache.activemq.command.ActiveMQQueue("SomeTopic.B::B.b.SomeTopic.B"), underTest.virtualTopicConsumerToFQQN(B));

      ActiveMQDestination C = new org.apache.activemq.command.ActiveMQQueue("C.c.c.SomeTopic.EE");
      assertEquals(new org.apache.activemq.command.ActiveMQQueue("SomeTopic.EE::C.c.c.SomeTopic.EE"), underTest.virtualTopicConsumerToFQQN(C));

      checkOption = underTest.virtualTopicConsumerToFQQN(C);
      assertNull(checkOption.getOptions());

      for (int i = 0; i < maxCacheSize; i++) {
         ActiveMQDestination identity = new org.apache.activemq.command.ActiveMQQueue("Identity" + i);
         assertEquals(identity, underTest.virtualTopicConsumerToFQQN(identity));
      }

      assertFalse(lruCacheRef.containsKey(A));
   }

   static final class DummyServer extends ActiveMQServerImpl {

      @Override
      public ClusterManager getClusterManager() {
         return new ClusterManager(getExecutorFactory(), this, null, null, null, null, null, false);
      }

      @Override
      public ExecutorFactory getExecutorFactory() {
         return () -> null;
      }

      @Override
      public Activation getActivation() {
         return new Activation() {
            @Override
            public void close(boolean permanently, boolean restarting) throws Exception {

            }

            @Override
            public void run() {

            }
         };
      }
   }
}

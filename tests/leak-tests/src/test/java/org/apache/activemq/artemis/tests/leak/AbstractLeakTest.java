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

package org.apache.activemq.artemis.tests.leak;

import io.github.checkleak.core.CheckLeak;
import org.apache.activemq.artemis.core.journal.impl.JournalImpl;
import org.apache.activemq.artemis.core.paging.impl.PagingManagerImpl;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.ServerStatus;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.actors.OrderedExecutor;
import org.junit.jupiter.api.AfterAll;

public abstract class AbstractLeakTest extends ActiveMQTestBase {

   @AfterAll
   public static void clearStatus() throws Exception {
      ServerStatus.clear();

      CheckLeak checkLeak = new CheckLeak();
      MemoryAssertions.assertMemory(checkLeak, 0, JournalImpl.class.getName());
      MemoryAssertions.assertMemory(checkLeak, 0, ActiveMQServerImpl.class.getName());
      MemoryAssertions.assertMemory(checkLeak, 0, PagingManagerImpl.class.getName());
      MemoryAssertions.assertMemory(checkLeak, 0, OrderedExecutor.class.getName());
      MemoryAssertions.assertNoInnerInstances(OrderedExecutor.class, checkLeak);
   }

}

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
package org.apache.activemq.artemis.tests.integration.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public class QueueConfigRestartTest extends ActiveMQTestBase {




   @Test
   public void testQueueConfigPurgeOnNoConsumerAndRestart() throws Exception {
      ActiveMQServer server = createServer(true);

      server.start();

      SimpleString address = SimpleString.of("test.address");
      SimpleString queue = SimpleString.of("test.queue");

      server.createQueue(QueueConfiguration.of(queue).setAddress(address).setMaxConsumers(10).setPurgeOnNoConsumers(true));

      QueueBinding queueBinding1 = (QueueBinding)server.getPostOffice().getBinding(queue);
      assertTrue(queueBinding1.getQueue().isPurgeOnNoConsumers());

      server.stop();

      server.start();

      QueueBinding queueBinding2 = (QueueBinding)server.getPostOffice().getBinding(queue);
      assertTrue(queueBinding2.getQueue().isPurgeOnNoConsumers());
   }

   @Test
   public void testQueueConfigLastValueAndRestart() throws Exception {
      ActiveMQServer server = createServer(true);

      server.start();

      SimpleString address = SimpleString.of("test.address");
      SimpleString queue = SimpleString.of("test.queue");

      server.createQueue(QueueConfiguration.of(queue).setAddress(address).setMaxConsumers(10).setPurgeOnNoConsumers(true).setExclusive(true).setLastValue(true));

      QueueBinding queueBinding1 = (QueueBinding)server.getPostOffice().getBinding(queue);
      assertTrue(queueBinding1.getQueue().isLastValue());

      server.stop();

      server.start();

      QueueBinding queueBinding2 = (QueueBinding)server.getPostOffice().getBinding(queue);
      assertTrue(queueBinding2.getQueue().isLastValue());
   }

   @Test
   public void testQueueConfigExclusiveAndRestart() throws Exception {
      ActiveMQServer server = createServer(true);

      server.start();

      SimpleString address = SimpleString.of("test.address");
      SimpleString queue = SimpleString.of("test.queue");

      server.createQueue(QueueConfiguration.of(queue).setAddress(address).setMaxConsumers(10).setPurgeOnNoConsumers(true).setExclusive(true).setLastValue(true));

      QueueBinding queueBinding1 = (QueueBinding)server.getPostOffice().getBinding(queue);
      assertTrue(queueBinding1.getQueue().isExclusive());

      server.stop();

      server.start();

      QueueBinding queueBinding2 = (QueueBinding)server.getPostOffice().getBinding(queue);
      assertTrue(queueBinding2.getQueue().isExclusive());
   }

   @Test
   public void testQueueConfigConsumersBeforeDispatchAndRestart() throws Exception {
      int consumersBeforeDispatch = 5;
      ActiveMQServer server = createServer(true);

      server.start();

      SimpleString address = SimpleString.of("test.address");
      SimpleString queue = SimpleString.of("test.queue");

      server.createQueue(QueueConfiguration.of(queue).setAddress(address).setMaxConsumers(10).setPurgeOnNoConsumers(true).setExclusive(true).setConsumersBeforeDispatch(consumersBeforeDispatch));

      QueueBinding queueBinding1 = (QueueBinding)server.getPostOffice().getBinding(queue);
      assertEquals(consumersBeforeDispatch, queueBinding1.getQueue().getConsumersBeforeDispatch());

      server.stop();

      server.start();

      QueueBinding queueBinding2 = (QueueBinding)server.getPostOffice().getBinding(queue);
      assertEquals(consumersBeforeDispatch, queueBinding1.getQueue().getConsumersBeforeDispatch());
   }

   @Test
   public void testQueueConfigDelayBeforeDispatchAndRestart() throws Exception {
      long delayBeforeDispatch = 5000L;
      ActiveMQServer server = createServer(true);

      server.start();

      SimpleString address = SimpleString.of("test.address");
      SimpleString queue = SimpleString.of("test.queue");

      server.createQueue(QueueConfiguration.of(queue).setAddress(address).setMaxConsumers(10).setPurgeOnNoConsumers(true).setExclusive(true).setDelayBeforeDispatch(delayBeforeDispatch));

      QueueBinding queueBinding1 = (QueueBinding)server.getPostOffice().getBinding(queue);
      assertEquals(delayBeforeDispatch, queueBinding1.getQueue().getDelayBeforeDispatch());

      server.stop();

      server.start();

      QueueBinding queueBinding2 = (QueueBinding)server.getPostOffice().getBinding(queue);
      assertEquals(delayBeforeDispatch, queueBinding1.getQueue().getDelayBeforeDispatch());
   }


   @Test
   public void testQueueConfigUserAndRestart() throws Exception {
      ActiveMQServer server = createServer(true);

      server.start();

      SimpleString address = SimpleString.of("test.address");
      SimpleString queue = SimpleString.of("test.queue");

      server.createQueue(QueueConfiguration.of(queue).setAddress(address).setUser("bob").setMaxConsumers(10).setPurgeOnNoConsumers(true));

      QueueBinding queueBinding1 = (QueueBinding)server.getPostOffice().getBinding(queue);
      assertEquals(SimpleString.of("bob"), queueBinding1.getQueue().getUser());

      server.stop();

      server.start();

      QueueBinding queueBinding2 = (QueueBinding)server.getPostOffice().getBinding(queue);
      assertTrue(queueBinding2.getQueue().isPurgeOnNoConsumers());
   }

   @Test
   public void testQueueConfigEnabledAndRestart() throws Exception {
      ActiveMQServer server = createServer(true);

      server.start();

      SimpleString address = SimpleString.of("test.address");
      SimpleString queue = SimpleString.of("test.queue");

      server.createQueue(QueueConfiguration.of(queue).setAddress(address).setEnabled(true));

      QueueBinding queueBinding1 = (QueueBinding)server.getPostOffice().getBinding(queue);
      assertTrue(queueBinding1.getQueue().isEnabled());

      server.stop();

      server.start();

      QueueBinding queueBinding2 = (QueueBinding)server.getPostOffice().getBinding(queue);
      assertTrue(queueBinding2.getQueue().isEnabled());
   }

   @Test
   public void testQueueConfigDisabledAndRestart() throws Exception {
      ActiveMQServer server = createServer(true);

      server.start();

      SimpleString address = SimpleString.of("test.address");
      SimpleString queue = SimpleString.of("test.queue");

      server.createQueue(QueueConfiguration.of(queue).setAddress(address).setEnabled(false));

      QueueBinding queueBinding1 = (QueueBinding)server.getPostOffice().getBinding(queue);
      assertFalse(queueBinding1.getQueue().isEnabled());

      server.stop();

      server.start();

      QueueBinding queueBinding2 = (QueueBinding)server.getPostOffice().getBinding(queue);
      assertFalse(queueBinding2.getQueue().isEnabled());
   }
}

/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.activemq.artemis.core.protocol.openwire.amq;

import java.util.concurrent.ScheduledExecutorService;

import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.protocol.openwire.OpenWireConnection;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.SlowConsumerDetectionListener;
import org.apache.activemq.artemis.core.server.impl.ServerConsumerImpl;
import org.apache.activemq.artemis.utils.UUID;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ActiveMQTopic;
import org.apache.activemq.command.ConsumerInfo;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class AMQConsumerTest {

   @Test
   public void testCreditsWithPrefetch() throws Exception {
      AMQConsumer consumer = getConsumer(1);

      testCredits(consumer);
   }

   @Test
   public void testCreditsUpdatingPrefetchSize() throws Exception {
      AMQConsumer consumer = getConsumer(0);

      consumer.setPrefetchSize(1);

      testCredits(consumer);
   }

   private AMQConsumer getConsumer(int prefetchSize) throws Exception {
      UUID nodeId = UUIDGenerator.getInstance().generateUUID();
      ActiveMQServer coreServer = Mockito.mock(ActiveMQServer.class);
      NodeManager nodeManager = Mockito.mock(NodeManager.class);
      Mockito.when(coreServer.getNodeManager()).thenReturn(nodeManager);
      Mockito.when(nodeManager.getUUID()).thenReturn(nodeId);

      ServerSession coreSession = Mockito.mock(ServerSession.class);
      Mockito.when(coreSession.createConsumer(ArgumentMatchers.anyLong(), ArgumentMatchers.nullable(SimpleString.class),
                                              ArgumentMatchers.nullable(SimpleString.class), ArgumentMatchers.anyInt(),
                                              ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyBoolean(),
                                              ArgumentMatchers.nullable(Integer.class))).thenReturn(Mockito.mock(ServerConsumerImpl.class));
      AMQSession session = Mockito.mock(AMQSession.class);
      Mockito.when(session.getConnection()).thenReturn(Mockito.mock(OpenWireConnection.class));
      Mockito.when(session.getCoreServer()).thenReturn(coreServer);
      Mockito.when(session.getCoreSession()).thenReturn(coreSession);
      Mockito.when(session.convertWildcard(ArgumentMatchers.any(ActiveMQDestination.class))).thenReturn("");

      ConsumerInfo info = new ConsumerInfo();
      info.setPrefetchSize(prefetchSize);

      AMQConsumer consumer = new AMQConsumer(session, new ActiveMQTopic("TEST"), info, Mockito.mock(ScheduledExecutorService.class), false);
      consumer.init(Mockito.mock(SlowConsumerDetectionListener.class), 0);

      return consumer;
   }

   private void testCredits(AMQConsumer consumer) throws  Exception {
      ICoreMessage message = new CoreMessage(1, 0);
      MessageReference reference = Mockito.mock(MessageReference.class);

      Assert.assertTrue(consumer.hasCredits());

      consumer.handleDeliver(reference, message);

      Assert.assertFalse(consumer.hasCredits());

      consumer.acquireCredit(1, true);

      Assert.assertTrue(consumer.hasCredits());
   }
}

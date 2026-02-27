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
package org.apache.activemq.artemis.core.protocol.core.impl;

import io.netty.buffer.Unpooled;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.buffers.impl.ChannelBufferWrapper;
import org.apache.activemq.artemis.core.protocol.core.CoreRemotingConnection;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

public class ActiveMQSessionContextTest {

   @Test
   public void testExceptionContainsCause() throws ActiveMQException {
      CoreRemotingConnection coreRC = Mockito.mock(CoreRemotingConnection.class);
      Mockito.when(coreRC.createTransportBuffer(Packet.INITIAL_PACKET_SIZE)).thenReturn(new ChannelBufferWrapper(Unpooled.buffer(Packet.INITIAL_PACKET_SIZE)));
      Mockito.when(coreRC.blockUntilWritable(Mockito.anyLong())).thenReturn(true);
      Mockito.when(coreRC.getTransportConnection()).thenReturn(Mockito.mock(Connection.class));
      ChannelImpl channel = new ChannelImpl(coreRC, 1, 4000, null);

      ActiveMQSessionContext context = new ActiveMQSessionContext("test", Mockito.mock(RemotingConnection.class), channel, 0, 0);

      try {
         context.sendServerLargeMessageChunk(null, 0, true, true, null, 0, null);
         fail("Expected exception to be thrown");
      } catch (ActiveMQException e) {
         assertInstanceOf(NullPointerException.class, ExceptionUtils.getRootCause(e));
      }
   }
}
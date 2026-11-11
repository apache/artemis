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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import io.netty.buffer.Unpooled;
import org.apache.activemq.artemis.core.buffers.impl.ChannelBufferWrapper;
import org.apache.activemq.artemis.core.protocol.core.CommandConfirmationHandler;
import org.apache.activemq.artemis.core.protocol.core.CoreRemotingConnection;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.ResponseHandler;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.PacketsConfirmedMessage;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class ChannelImplTest {

   ChannelImpl channel;

   @BeforeEach
   public void setUp() {
      CoreRemotingConnection coreRC = Mockito.mock(CoreRemotingConnection.class);
      Mockito.when(coreRC.createTransportBuffer(Packet.INITIAL_PACKET_SIZE)).thenReturn(new ChannelBufferWrapper(Unpooled.buffer(Packet.INITIAL_PACKET_SIZE)));
      Connection connection = Mockito.mock(Connection.class);
      Mockito.when(coreRC.getTransportConnection()).thenReturn(connection);
      channel = new ChannelImpl(coreRC, 1, 4000, null);
   }

   @Test
   public void testCorrelation() {

      AtomicInteger handleResponseCount = new AtomicInteger();

      Packet requestPacket = Mockito.mock(Packet.class);
      Mockito.when(requestPacket.isResponseAsync()).thenReturn(true);
      Mockito.when(requestPacket.isRequiresResponse()).thenReturn(true);
      setResponseHandlerAsPerActiveMQSessionContext((packet, response) -> handleResponseCount.incrementAndGet());

      channel.send(requestPacket);

      assertEquals(1, channel.getCache().size());

      Packet responsePacket = Mockito.mock(Packet.class);
      Mockito.when(responsePacket.isResponseAsync()).thenReturn(true);
      Mockito.when(responsePacket.isResponse()).thenReturn(true);

      channel.handlePacket(responsePacket);

      assertEquals(1, handleResponseCount.get());
      assertEquals(0, channel.getCache().size());
   }

   private void setResponseHandlerAsPerActiveMQSessionContext(ResponseHandler responseHandler) {
      channel.setResponseHandler(responseHandler);
      channel.setCommandConfirmationHandler(wrapAsPerActiveMQSessionContext(responseHandler));
   }

   private CommandConfirmationHandler wrapAsPerActiveMQSessionContext(ResponseHandler responseHandler) {
      return packet -> responseHandler.handleResponse(packet, null);
   }

   @Test
   public void testPacketsConfirmedMessage() {

      AtomicInteger handleResponseCount = new AtomicInteger();

      Packet requestPacket = Mockito.mock(Packet.class);
      Mockito.when(requestPacket.isResponseAsync()).thenReturn(true);
      Mockito.when(requestPacket.isRequiresResponse()).thenReturn(true);
      Mockito.when(requestPacket.isRequiresConfirmations()).thenReturn(true);
      setResponseHandlerAsPerActiveMQSessionContext((packet, response) -> handleResponseCount.incrementAndGet());

      channel.send(requestPacket);

      PacketsConfirmedMessage responsePacket = new PacketsConfirmedMessage((byte) 2);

      channel.handlePacket(responsePacket);

      assertEquals(0, channel.getCache().size());
   }
}
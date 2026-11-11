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
package org.apache.activemq.artemis.core.protocol.hornetq;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.core.protocol.core.impl.CoreProtocolManager;
import org.apache.activemq.artemis.core.protocol.core.impl.CoreProtocolManagerFactory;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyServerConnection;
import org.apache.activemq.artemis.core.server.ActiveMQServer;

/**
 * HornetQ Protocol Manager
 */
class HornetQProtocolManager extends CoreProtocolManager {

   HornetQProtocolManager(CoreProtocolManagerFactory factory,
                          ActiveMQServer server,
                          List<Interceptor> incomingInterceptors,
                          List<Interceptor> outgoingInterceptors) {
      super(factory, server, incomingInterceptors, outgoingInterceptors);
   }

   @Override
   public void handshake(NettyServerConnection connection, ActiveMQBuffer buffer) {
      //if we are not an old client then handshake
      if (buffer.getByte(0) == 'H' &&
         buffer.getByte(1) == 'O' &&
         buffer.getByte(2) == 'R' &&
         buffer.getByte(3) == 'N' &&
         buffer.getByte(4) == 'E' &&
         buffer.getByte(5) == 'T' &&
         buffer.getByte(6) == 'Q') {
         //todo add some handshaking
         buffer.skipBytes(7);
      }
   }

   @Override
   public boolean acceptsNoHandshake() {
      return true;
   }

   @Override
   public boolean isProtocol(byte[] array) {
      String frameStart = new String(array, StandardCharsets.US_ASCII);
      return frameStart.startsWith("HORNETQ");
   }

   @Override
   public String toString() {
      return "HornetQProtocolManager(server=" + super.server + ")";
   }
}

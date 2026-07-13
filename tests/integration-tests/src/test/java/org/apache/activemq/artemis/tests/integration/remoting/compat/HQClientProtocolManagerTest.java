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
package org.apache.activemq.artemis.tests.integration.remoting.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ClientSessionInternal;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.protocol.hornetq.client.HornetQClientProtocolManagerFactory;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HQClientProtocolManagerTest extends ActiveMQTestBase {

   @Test
   public void testNoCheckFailoverMessage() throws Exception {
      final int pingPeriod = 1000;

      ActiveMQServer server = createServer(false, true);

      server.start();

      ClientSessionInternal session = null;

      try {
         ServerLocator locator = createFactory(true).setClientFailureCheckPeriod(pingPeriod).setRetryInterval(500).setRetryIntervalMultiplier(1d).setReconnectAttempts(-1).setConfirmationWindowSize(1024 * 1024);
         locator.setProtocolManagerFactory(new HornetQClientProtocolManagerFactory());
         ClientSessionFactory factory = createSessionFactory(locator);

         session = (ClientSessionInternal) factory.createSession();

         server.stop();

         Thread.sleep((pingPeriod * 2));

         List<String> incomings = server.getConfiguration().getIncomingInterceptorClassNames();
         incomings.add(UnsupportedPacketInterceptor.class.getName());

         server.start();

         //issue a query to make sure session is reconnected.
         ClientSession.QueueQuery query = session.queueQuery(SimpleString.of("anyvalue"));
         assertFalse(query.isExists());

         locator.close();

         UnsupportedPacketInterceptor.checkReceivedTypes();
      } finally {
         try {
            session.close();
         } catch (Throwable e) {
         }

         server.stop();
      }
   }


   public static class UnsupportedPacketInterceptor implements Interceptor {

      private static Set<Byte> receivedTypes = new HashSet<>();
      private static Set<Byte> unsupportedTypes = new HashSet<>();
      static {
         unsupportedTypes.add(PacketImpl.CONNECT);
      }

      @Override
      public boolean intercept(Packet packet, RemotingConnection connection) throws ActiveMQException {
         receivedTypes.add(packet.getType());
         return true;
      }

      public static void checkReceivedTypes() throws Exception {
         for (Byte type : receivedTypes) {
            assertFalse(unsupportedTypes.contains(type), "Received unsupported type: " + type);
         }
      }
   }
}

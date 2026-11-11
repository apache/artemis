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
package org.apache.activemq.artemis.protocol.amqp.proton;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.AddressQueryResult;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPSessionCallback;
import org.apache.activemq.artemis.protocol.amqp.broker.ProtonProtocolManager;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPNotFoundException;
import org.apache.activemq.artemis.protocol.amqp.proton.handler.ProtonHandler;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.engine.Connection;
import org.apache.qpid.proton.engine.EndpointState;
import org.apache.qpid.proton.engine.Sender;
import org.junit.jupiter.api.Test;

import java.util.Collections;

public class ProtonServerSenderContextTest {

   @Test
   public void testAcceptsNullSourceAddressWhenInitialising() throws Exception {
      assertThrows(ActiveMQAMQPNotFoundException.class, () -> {
         ProtonProtocolManager mock = mock(ProtonProtocolManager.class);
         when(mock.getServer()).thenReturn(mock(ActiveMQServer.class));
         Sender mockSender = mock(Sender.class);
         AMQPConnectionContext mockConnContext = mock(AMQPConnectionContext.class);

         ProtonHandler handler = mock(ProtonHandler.class);
         Connection connection = mock(Connection.class);
         when(connection.getRemoteState()).thenReturn(EndpointState.ACTIVE);
         when(mockConnContext.getHandler()).thenReturn(handler);
         when(handler.getConnection()).thenReturn(connection);

         when(mockConnContext.getProtocolManager()).thenReturn(mock);

         AMQPSessionCallback mockSessionCallback = mock(AMQPSessionCallback.class);

         AMQPSessionContext mockSessionContext = mock(AMQPSessionContext.class);
         when(mockSessionContext.getSessionSPI()).thenReturn(mockSessionCallback);
         when(mockSessionContext.getAMQPConnectionContext()).thenReturn(mockConnContext);

         AddressQueryResult queryResult = new AddressQueryResult(null, Collections.emptySet(), 0, false, false, false, false, 0);
         when(mockSessionCallback.addressQuery(any(), any(), anyBoolean())).thenReturn(queryResult);
         ProtonServerSenderContext sc = new ProtonServerSenderContext(
            mockConnContext, mockSender, mockSessionContext, mockSessionCallback);

         Source source = new Source();
         source.setAddress(null);
         when(mockSender.getRemoteSource()).thenReturn(source);

         sc.initialize();
      });
   }
}

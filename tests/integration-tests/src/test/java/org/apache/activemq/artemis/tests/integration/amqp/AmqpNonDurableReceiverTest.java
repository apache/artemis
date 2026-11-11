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
package org.apache.activemq.artemis.tests.integration.amqp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.transport.amqp.client.AmqpClient;
import org.apache.activemq.transport.amqp.client.AmqpConnection;
import org.apache.activemq.transport.amqp.client.AmqpReceiver;
import org.apache.activemq.transport.amqp.client.AmqpSession;
import org.apache.activemq.transport.amqp.client.AmqpValidator;
import org.apache.qpid.proton.amqp.messaging.Terminus;
import org.apache.qpid.proton.amqp.messaging.TerminusExpiryPolicy;
import org.apache.qpid.proton.engine.Receiver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class AmqpNonDurableReceiverTest extends AmqpClientTestSupport {

   @Test
   @Timeout(60)
   public void testLinkDetachReleasesResources() throws Exception {

      AmqpClient client = createAmqpClient();
      AmqpConnection connection = addConnection(client.createConnection());
      connection.connect();

      AmqpSession session = connection.createSession();

      SimpleString simpleTopicName = SimpleString.of(getTopicName());
      final int bindingsBefore = server.getPostOffice().getBindingsForAddress(simpleTopicName).getBindings().size();


      AmqpReceiver receiver = session.createReceiver(getTopicName());

      AtomicBoolean remoteLinkClosed = new AtomicBoolean();
      assertEquals(TerminusExpiryPolicy.LINK_DETACH,
                   ((Terminus) receiver.getEndpoint().getSource()).getExpiryPolicy(),
                   "Unexpected source expiry policy");

      receiver.setStateInspector(new AmqpValidator() {
         @Override
         public void inspectDetachedResource(final Receiver receiver) {
            super.inspectDetachedResource(receiver);
            fail("Remote link detached in unexpected manner");
         }

         @Override
         public void inspectClosedResource(final Receiver receiver) {
            super.inspectClosedResource(receiver);
            remoteLinkClosed.set(true);
         }
      });

      assertEquals(bindingsBefore + 1, server.getPostOffice().getBindingsForAddress(simpleTopicName).getBindings().size(), "Unexpected number of bindings before attach");

      receiver.detach();

      assertEquals(bindingsBefore,
                   server.getPostOffice().getBindingsForAddress(simpleTopicName).getBindings().size(),
                   "Unexpected number of bindings after detach");

      assertTrue(remoteLinkClosed.get(), "Remote link was not closed");

      receiver.getStateInspector().assertValid();
   }
}

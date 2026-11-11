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

package org.apache.activemq.artemis.tests.integration.amqp.largemessages;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.NoSuchElementException;

import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPLargeMessage;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;

public class AMQPLargeMessagesTestUtil {


   public static void validateAllTemporaryBuffers(ActiveMQServer server) {
      server.getPostOffice().getAllBindings()
         .filter(QueueBinding.class::isInstance)
         .forEach(binding -> validateTemporaryBuffers(((QueueBinding) binding).getQueue()));
   }

   public static void validateTemporaryBuffers(Queue serverQueue) {
      LinkedListIterator<MessageReference> totalIterator = serverQueue.browserIterator();
      try {
         while (totalIterator.hasNext()) {
            MessageReference ref = totalIterator.next();
            if (ref.getMessage() instanceof AMQPLargeMessage amqpLargeMessage) {
               // Using a Wait.waitFor here as we may have something working with the buffer in parallel
               Wait.waitFor(() -> amqpLargeMessage.inspectTemporaryBuffer() == null, 1000, 10);
               assertNull(amqpLargeMessage.inspectTemporaryBuffer(), "Temporary buffers are being retained");
            }
         }
      } catch (NoSuchElementException e) {
         // that's ok
      }
      totalIterator.close();
   }

}

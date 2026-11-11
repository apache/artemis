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
package org.apache.activemq.artemis.tests.integration.stomp;

import java.net.URI;
import java.util.UUID;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.protocol.stomp.Stomp;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnection;
import org.apache.activemq.artemis.tests.integration.stomp.util.StompClientConnectionFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class StompTestMultiThreaded extends StompTestBase {

   private static final SimpleString QUEUE = SimpleString.of("x");

   public StompTestMultiThreaded() {
      super("tcp+v10.stomp");
   }

   class SomeConsumer extends Thread {

      private final StompClientConnection conn;

      boolean failed = false;

      SomeConsumer() throws Exception {
         URI uri = createStompClientUri(scheme, "localhost", 61614);
         this.conn = StompClientConnectionFactory.createClientConnection(uri);
      }

      @Override
      public void run() {
         try {
            conn.connect(defUser, defPass);
            if (!subscribe(conn, UUID.randomUUID().toString(), Stomp.Headers.Subscribe.AckModeValues.AUTO, null, null, "/queue/" + QUEUE, true).getCommand().equals(Stomp.Responses.RECEIPT)) {
               failed = true;
            }
         } catch (Throwable e) {
            failed = true;
         } finally {
            try {
               conn.disconnect();
            } catch (Exception e) {
            }
         }
      }
   }

   @Test
   public void testTwoConcurrentSubscribers() throws Exception {
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setAutoDeleteAddresses(false).setAutoDeleteQueues(false));
      server.getRemotingService().createAcceptor("test", "tcp://localhost:61614?protocols=STOMP&anycastPrefix=/queue/").start();

      int nThreads = 2;

      SomeConsumer[] consumers = new SomeConsumer[nThreads];
      for (int j = 0; j < 1000; j++) {

         for (int i = 0; i < nThreads; i++) {
            consumers[i] = new SomeConsumer();
         }

         for (int i = 0; i < nThreads; i++) {
            consumers[i].start();
         }

         for (SomeConsumer consumer : consumers) {
            consumer.join();
            assertFalse(consumer.failed);
         }

         // delete queue here so it can be auto-created again during the next loop iteration
         server.locateQueue(QUEUE).deleteQueue();
      }
   }
}

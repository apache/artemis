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
package org.apache.activemq.artemis.cli.commands.messages.perf;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.cli.commands.messages.perf.AsyncJms2ProducerFacade.SendAttemptResult;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class AsyncJms2ProducerFacadeTest {

   @Test
   public void testObserverRegisteringAgainWhileRunning() throws Exception {
      testObserverRegisteringAgainWhileRunning(false);
   }

   @Test
   public void testObserverRegisteringAgainWhileRunningTransacted() throws Exception {
      testObserverRegisteringAgainWhileRunning(true);
   }

   /**
    * As the perf load generators do, an observer that finds the producer not available registers itself again: with
    * one pending send allowed, the second run of an observer registered twice must wait for the next completion.
    */
   private void testObserverRegisteringAgainWhileRunning(final boolean transacted) throws Exception {
      final Session session = Mockito.mock(Session.class);
      Mockito.when(session.getTransacted()).thenReturn(transacted);
      final MessageProducer producer = Mockito.mock(MessageProducer.class);
      final Message message = Mockito.mock(Message.class);
      final AsyncJms2ProducerFacade facade = new AsyncJms2ProducerFacade(0, session, producer, null, 1, transacted ? 1 : 0);

      final List<SendAttemptResult> results = new ArrayList<>();
      final Runnable observer = new Runnable() {
         @Override
         public void run() {
            try {
               results.add(facade.trySend(message, null, this));
            } catch (JMSException e) {
               throw new RuntimeException(e);
            }
         }
      };

      assertEquals(SendAttemptResult.Success, facade.trySend(message, null, observer));
      assertEquals(SendAttemptResult.NotAvailable, facade.trySend(message, null, observer));
      assertEquals(SendAttemptResult.NotAvailable, facade.trySend(message, null, observer));

      assertNull(facade.onSendCompleted());
      assertEquals(List.of(SendAttemptResult.Success, SendAttemptResult.NotAvailable), results);

      // the observer that registered again runs on the next completion
      assertNull(facade.onSendCompleted());
      assertEquals(List.of(SendAttemptResult.Success, SendAttemptResult.NotAvailable, SendAttemptResult.Success), results);
      assertEquals(3, facade.getMessageSent());
      Mockito.verify(producer, Mockito.times(3)).send(message, null);
   }
}

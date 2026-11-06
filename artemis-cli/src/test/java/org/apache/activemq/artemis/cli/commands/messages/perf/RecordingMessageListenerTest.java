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
package org.apache.activemq.artemis.cli.commands.messages.perf;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RecordingMessageListenerTest {

   private final AtomicBoolean fatalException = new AtomicBoolean();

   private RecordingMessageListener listener(final long commitInterval, final Session session) {
      final RecordingMessageListener listener = new RecordingMessageListener(1, null, commitInterval, new AtomicLong(), null, null, null, fatalException);
      listener.setTransactedSession(session);
      return listener;
   }

   private static void receive(final RecordingMessageListener listener, final int messages) {
      for (int i = 0; i < messages; i++) {
         listener.onMessage(Mockito.mock(Message.class));
      }
   }

   @Test
   public void testCommitsEveryCommitInterval() throws Exception {
      final Session session = Mockito.mock(Session.class);
      final RecordingMessageListener listener = listener(3, session);

      receive(listener, 2);
      Mockito.verify(session, Mockito.never()).commit();
      receive(listener, 5);

      Mockito.verify(session, Mockito.times(2)).commit();
      assertEquals(7, listener.getReceivedMessages());
   }

   @Test
   public void testCommitsEveryMessageWithoutCommitInterval() throws Exception {
      final Session session = Mockito.mock(Session.class);

      receive(listener(0, session), 3);

      Mockito.verify(session, Mockito.times(3)).commit();
   }

   @Test
   public void testCommitReceivedCommitsTheRest() throws Exception {
      final Session session = Mockito.mock(Session.class);
      final RecordingMessageListener listener = listener(3, session);
      receive(listener, 4);
      Mockito.verify(session).commit();

      listener.commitReceived();
      Mockito.verify(session, Mockito.times(2)).commit();

      // nothing is left
      listener.commitReceived();
      Mockito.verify(session, Mockito.times(2)).commit();
   }

   @Test
   public void testNotTransacted() {
      final RecordingMessageListener listener = listener(3, null);

      receive(listener, 4);
      listener.commitReceived();

      assertEquals(4, listener.getReceivedMessages());
      assertFalse(listener.anyFatalException());
   }

   @Test
   public void testFailedCommitIsFatal() throws Exception {
      final Session session = Mockito.mock(Session.class);
      Mockito.doThrow(new JMSException("commit failed")).when(session).commit();
      final RecordingMessageListener listener = listener(1, session);

      receive(listener, 1);

      assertTrue(listener.anyFatalException());
   }
}

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
package org.apache.activemq.artemis.core.replication;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.core.journal.Journal;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReplicatedJournalTest {

   private static final int NO_INVOCATION = 0;
   private static final int JOURNAL_INVOCATION = 1;
   private static final int REPLICA_INVOCATION = 2;

   @Test
   public void testAppendInvocationOrder() throws Exception {
      AtomicInteger firstInvocation = new AtomicInteger(NO_INVOCATION);

      Journal mockJournal = Mockito.mock(Journal.class, invocationOnMock -> {
         if (invocationOnMock.getMethod().getName().startsWith("append") ||
             invocationOnMock.getMethod().getName().startsWith("tryAppend")) {
            firstInvocation.compareAndSet(NO_INVOCATION, JOURNAL_INVOCATION);
         }
         return null;
      });

      ReplicationManager replicationManager = Mockito.mock(ReplicationManager.class, invocationOnMock -> {
         if (invocationOnMock.getMethod().getName().startsWith("append") ||
             invocationOnMock.getMethod().getName().startsWith("tryAppend")) {
            firstInvocation.compareAndSet(NO_INVOCATION, REPLICA_INVOCATION);
         }
         return null;
      });

      ReplicatedJournal replicatedJournal = new ReplicatedJournal((byte)0, mockJournal, replicationManager);

      for (Method method : ReplicatedJournal.class.getDeclaredMethods()) {
         if (method.getName().startsWith("append") ||
             method.getName().startsWith("tryAppend")) {
            List<Object> args = new ArrayList<>();
            for (Class parameterType : method.getParameterTypes()) {
               if (boolean.class.equals(parameterType)) {
                  args.add(false);
               } else if (byte.class.equals(parameterType)) {
                  args.add((byte)0);
               } else if (long.class.equals(parameterType)) {
                  args.add((long)0);
               } else {
                  args.add(null);
               }
            }

            method.invoke(replicatedJournal, args.toArray());

            assertEquals(JOURNAL_INVOCATION, firstInvocation.get(), method.toString());

            firstInvocation.set(NO_INVOCATION);
         }
      }
   }
}

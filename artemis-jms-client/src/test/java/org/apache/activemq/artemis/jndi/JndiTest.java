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

package org.apache.activemq.artemis.jndi;

import javax.naming.Context;
import javax.naming.InitialContext;
import java.util.Hashtable;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory.DYNAMIC_QUEUE_CONTEXT;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class JndiTest {

   @Test
   public void testMultiThreadedDynamicQueueLookup() throws Exception {
      final int nThreads = 10;

      LookerUpper[] lookerUppers = new LookerUpper[nThreads];
      for (int j = 0; j < 100; j++) {
         Hashtable<String, String> props = new Hashtable<>();
         props.put(Context.INITIAL_CONTEXT_FACTORY, "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
         InitialContext context = new InitialContext(props);
         String lookup = DYNAMIC_QUEUE_CONTEXT + "/" + UUID.randomUUID().toString();

         for (int i = 0; i < nThreads; i++) {
            lookerUppers[i] = new LookerUpper(context, lookup);
         }

         for (int i = 0; i < nThreads; i++) {
            lookerUppers[i].start();
         }

         for (LookerUpper lookerUpper : lookerUppers) {
            lookerUpper.join();
            assertFalse(lookerUpper.failed);
         }
         context.close();
      }
   }

   class LookerUpper extends Thread {
      InitialContext context;
      String lookup;
      boolean failed = false;

      LookerUpper(InitialContext context, String lookup) throws Exception {
         this.context = context;
         this.lookup = lookup;
      }

      @Override
      public void run() {
         try {
            context.lookup(lookup);
         } catch (Throwable e) {
            failed = true;
         }
      }
   }
}

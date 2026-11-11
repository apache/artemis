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
package org.apache.activemq.artemis.tests.integration.openwire.amq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.TransactionRolledBackException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.TransactionContext;
import org.apache.activemq.artemis.tests.integration.openwire.BasicOpenWireTest;
import org.apache.activemq.transaction.Synchronization;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * adapted from: org.apache.activemq.TransactionContextTest
 */
public class TransactionContextTest extends BasicOpenWireTest {

   TransactionContext underTest;

   @BeforeEach
   public void setup() throws Exception {
      connection.start();
      underTest = new TransactionContext(connection);
   }

   @Test
   public void testSyncBeforeEndCalledOnceOnRollback() throws Exception {
      final AtomicInteger beforeEndCountA = new AtomicInteger(0);
      final AtomicInteger beforeEndCountB = new AtomicInteger(0);
      final AtomicInteger rollbackCountA = new AtomicInteger(0);
      final AtomicInteger rollbackCountB = new AtomicInteger(0);
      underTest.addSynchronization(new Synchronization() {
         @Override
         public void beforeEnd() throws Exception {
            if (beforeEndCountA.getAndIncrement() == 0) {
               throw new TransactionRolledBackException("force rollback");
            }
         }

         @Override
         public void afterCommit() throws Exception {
            fail("expected rollback exception");
         }

         @Override
         public void afterRollback() throws Exception {
            rollbackCountA.incrementAndGet();
         }

      });

      underTest.addSynchronization(new Synchronization() {
         @Override
         public void beforeEnd() throws Exception {
            beforeEndCountB.getAndIncrement();
         }

         @Override
         public void afterCommit() throws Exception {
            fail("expected rollback exception");
         }

         @Override
         public void afterRollback() throws Exception {
            rollbackCountB.incrementAndGet();
         }

      });

      try {
         underTest.commit();
         fail("expected rollback exception");
      } catch (TransactionRolledBackException expected) {
      }

      assertEquals(1, beforeEndCountA.get(), "beforeEnd A called once");
      assertEquals(1, beforeEndCountA.get(), "beforeEnd B called once");
      assertEquals(1, rollbackCountB.get(), "rollbackCount B 0");
      assertEquals(rollbackCountA.get(), rollbackCountB.get(), "rollbackCount A B");
   }

   @Test
   public void testSyncIndexCleared() throws Exception {
      final AtomicInteger beforeEndCountA = new AtomicInteger(0);
      final AtomicInteger rollbackCountA = new AtomicInteger(0);
      Synchronization sync = new Synchronization() {
         @Override
         public void beforeEnd() throws Exception {
            beforeEndCountA.getAndIncrement();
         }

         @Override
         public void afterCommit() throws Exception {
            fail("expected rollback exception");
         }

         @Override
         public void afterRollback() throws Exception {
            rollbackCountA.incrementAndGet();
         }
      };

      underTest.begin();
      underTest.addSynchronization(sync);
      underTest.rollback();

      assertEquals(1, beforeEndCountA.get(), "beforeEnd");
      assertEquals(1, rollbackCountA.get(), "rollback");

      // do it again
      underTest.begin();
      underTest.addSynchronization(sync);
      underTest.rollback();

      assertEquals(2, beforeEndCountA.get(), "beforeEnd");
      assertEquals(2, rollbackCountA.get(), "rollback");
   }

}

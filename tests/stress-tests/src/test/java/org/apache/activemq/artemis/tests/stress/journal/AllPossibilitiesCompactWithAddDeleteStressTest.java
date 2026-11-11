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
package org.apache.activemq.artemis.tests.stress.journal;

public class AllPossibilitiesCompactWithAddDeleteStressTest extends MixupCompactorTestBase {

   @Override
   public void internalTest() throws Exception {
      createJournal();

      startJournal();

      loadAndCheck();

      long tx0 = idGen.generateID();

      long tx1 = idGen.generateID();

      long add1 = idGen.generateID();

      long add2 = idGen.generateID();

      addTx(tx0, add1);

      rollback(tx0);

      addTx(tx1, add1, add2);

      commit(tx1);

      long tx2 = idGen.generateID();

      updateTx(tx2, add1, add2);

      commit(tx2);

      delete(add1);

      delete(add2);

      checkJournalOperation();

      stopJournal();

      createJournal();

      startJournal();

      loadAndCheck();

      stopJournal();

   }
}

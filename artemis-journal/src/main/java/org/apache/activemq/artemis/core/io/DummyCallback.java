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
package org.apache.activemq.artemis.core.io;

import org.apache.activemq.artemis.core.journal.impl.SyncIOCompletion;
import org.apache.activemq.artemis.journal.ActiveMQJournalLogger;

public class DummyCallback extends SyncIOCompletion {

   private static final DummyCallback instance = new DummyCallback();

   public static DummyCallback getInstance() {
      return DummyCallback.instance;
   }

   @Override
   public void done() {
   }

   @Override
   public void onError(final int errorCode, final String errorMessage) {
      ActiveMQJournalLogger.LOGGER.errorWritingData(errorMessage, errorCode, new Exception(errorMessage));
   }

   @Override
   public void waitCompletion() throws Exception {
   }

   @Override
   public void storeLineUp() {
   }
}

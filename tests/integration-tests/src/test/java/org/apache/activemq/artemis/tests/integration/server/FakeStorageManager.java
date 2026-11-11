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
package org.apache.activemq.artemis.tests.integration.server;

import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager;

public class FakeStorageManager extends NullStorageManager {

   List<Long> messageIds = new ArrayList<>();

   List<Long> ackIds = new ArrayList<>();

   @Override
   public void storeMessage(final Message message) throws Exception {
      messageIds.add(message.getMessageID());
   }

   @Override
   public void storeMessageTransactional(final long txID, final Message message) throws Exception {
      messageIds.add(message.getMessageID());
   }

   @Override
   public void deleteMessage(final long messageID) throws Exception {
      messageIds.remove(messageID);
   }

   @Override
   public void storeAcknowledge(final long queueID, final long messageID) throws Exception {
      ackIds.add(messageID);
   }

   @Override
   public void storeAcknowledgeTransactional(final long txID,
                                             final long queueID,
                                             final long messageiD) throws Exception {
      ackIds.add(messageiD);
   }
}

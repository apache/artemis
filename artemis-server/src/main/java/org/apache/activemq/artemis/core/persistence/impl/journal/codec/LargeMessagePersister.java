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
package org.apache.activemq.artemis.core.persistence.impl.journal.codec;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.persistence.CoreMessageObjectPools;
import org.apache.activemq.artemis.core.persistence.Persister;
import org.apache.activemq.artemis.core.server.LargeServerMessage;

import static org.apache.activemq.artemis.core.persistence.PersisterIDs.CoreLargeMessagePersister_ID;

public class LargeMessagePersister implements Persister<LargeServerMessage> {

   public static final byte ID = CoreLargeMessagePersister_ID;

   private static final LargeMessagePersister theInstance = new LargeMessagePersister();

   @Override
   public byte getID() {
      return ID;
   }

   public static LargeMessagePersister getInstance() {
      return theInstance;
   }

   protected LargeMessagePersister() {
   }

   @Override
   public LargeServerMessage decode(final ActiveMQBuffer buffer, LargeServerMessage message, CoreMessageObjectPools objectPools) {
      ((CoreMessage)message).decodeHeadersAndProperties(buffer.byteBuf());
      return message;
   }

   @Override
   public void encode(final ActiveMQBuffer buffer, LargeServerMessage message) {
      ((CoreMessage)message).encodeHeadersAndProperties(buffer.byteBuf());
   }

   @Override
   public int getEncodeSize(LargeServerMessage message) {
      return message.toMessage().getEncodeSize();
   }
}

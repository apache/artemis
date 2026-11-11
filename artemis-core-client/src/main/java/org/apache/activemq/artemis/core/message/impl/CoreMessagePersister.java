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
package org.apache.activemq.artemis.core.message.impl;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.persistence.CoreMessageObjectPools;
import org.apache.activemq.artemis.core.persistence.Persister;
import org.apache.activemq.artemis.utils.DataConstants;

import static org.apache.activemq.artemis.core.persistence.PersisterIDs.CoreMessagePersister_ID;
public class CoreMessagePersister implements Persister<Message> {
   public static final byte ID = CoreMessagePersister_ID;

   private static CoreMessagePersister theInstance;

   /**
    * This is a hook for testing
    */
   public static void registerPersister(CoreMessagePersister newPersister) {
      theInstance = newPersister;
   }

   public static void resetPersister() {
      theInstance = null;
   }

   public static CoreMessagePersister getInstance() {
      if (theInstance == null) {
         theInstance = new CoreMessagePersister();
      }
      return theInstance;
   }

   protected CoreMessagePersister() {
   }

   @Override
   public byte getID() {
      return ID;
   }

   @Override
   public int getEncodeSize(Message record) {
      return DataConstants.SIZE_BYTE + record.getPersistSize() +
         SimpleString.sizeofNullableString(record.getAddressSimpleString()) + DataConstants.SIZE_LONG;
   }


   /**
    * Sub classes must add the first short as the protocol-id
    */
   @Override
   public void encode(ActiveMQBuffer buffer, Message record) {
      buffer.writeByte((byte)1);
      buffer.writeLong(record.getMessageID());
      buffer.writeNullableSimpleString(record.getAddressSimpleString());
      record.persist(buffer);
   }


   @Override
   public Message decode(ActiveMQBuffer buffer, Message record, CoreMessageObjectPools pool) {
      // the caller must consume the first byte already, as that will be used to decide what persister (protocol) to use
      long id = buffer.readLong();
      final SimpleString address;
      if (pool == null) {
         address = buffer.readNullableSimpleString();
      } else {
         address = SimpleString.readNullableSimpleString(buffer.byteBuf(), pool.getAddressDecoderPool());
      }
      record = new CoreMessage();
      record.reloadPersistence(buffer, pool);
      record.setMessageID(id);
      record.setAddress(address);
      return record;
   }
}

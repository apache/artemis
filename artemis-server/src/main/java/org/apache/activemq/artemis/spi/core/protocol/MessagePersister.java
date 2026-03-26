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
package org.apache.activemq.artemis.spi.core.protocol;

import java.util.Objects;
import java.util.ServiceLoader;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.persistence.CoreMessageObjectPools;
import org.apache.activemq.artemis.core.message.impl.CoreMessagePersister;
import org.apache.activemq.artemis.core.persistence.Persister;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.server.LargeServerMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

import static org.apache.activemq.artemis.core.persistence.PersisterIDs.MAX_PERSISTERS;

public class MessagePersister implements Persister<Message> {

   @Override
   public byte getID() {
      return 0;
   }

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final MessagePersister theInstance = new MessagePersister();

   /**
    * This will be used for reading messages
    */
   private static final Persister<Message>[] persisters = new Persister[MAX_PERSISTERS];

   static {
      CoreMessagePersister persister = CoreMessagePersister.getInstance();
      MessagePersister.registerPersister(persister);

      Iterable<ProtocolManagerFactory> protocols  = ServiceLoader.load(ProtocolManagerFactory.class, MessagePersister.class.getClassLoader());
      for (ProtocolManagerFactory next : protocols) {
         registerProtocol(next);
      }
   }

   public static void registerProtocol(ProtocolManagerFactory manager) {
      Persister<Message>[] messagePersisters = manager.getPersister();
      if (messagePersisters == null || messagePersisters.length == 0) {
         logger.debug("Cannot find persister for {}", manager);
      } else {
         for (Persister p : messagePersisters) {
            registerPersister(p);
         }
      }
   }

   public static void clearPersisters() {
      for (int i = 0; i < persisters.length; i++) {
         persisters[i] = null;
      }
   }

   public static Persister getPersister(byte id) {
      if (id == 0 || id > MAX_PERSISTERS) {
         return null;
      }
      return persisters[id - 1];
   }

   public static void registerPersister(Persister<Message> persister) {
      if (persister != null) {
         assert persister.getID() <= MAX_PERSISTERS : "You must update MessagePersister::MAX_PERSISTERS to a higher number";
         persisters[persister.getID() - 1] = persister;
      }
   }

   public static MessagePersister getInstance() {
      return theInstance;
   }


   protected MessagePersister() {
   }

   @Override
   public int getEncodeSize(Message record) {
      return 0;
   }


   /**
    * Sub classes must add the first short as the protocol-id
    */
   @Override
   public void encode(ActiveMQBuffer buffer, Message record) {
      writePersisterID(buffer);
   }

   protected void writePersisterID(ActiveMQBuffer buffer) {
      buffer.writeByte(getID());
   }

   @Override
   public Message decode(ActiveMQBuffer buffer, Message record, CoreMessageObjectPools pools) {
      return decode(buffer, record, pools, null);
   }


   public Message decode(ActiveMQBuffer buffer, Message record, CoreMessageObjectPools pools, StorageManager storageManager) {
      byte protocol = buffer.readByte();
      Persister<Message> persister = getPersister(protocol);
      Objects.requireNonNull(persister, "couldn't find factory for type=" + protocol);
      Message message = persister.decode(buffer, record, pools);
      if (message instanceof LargeServerMessage largeServerMessage) {
         largeServerMessage.setStorageManager(storageManager);
      }
      return message;
   }
}

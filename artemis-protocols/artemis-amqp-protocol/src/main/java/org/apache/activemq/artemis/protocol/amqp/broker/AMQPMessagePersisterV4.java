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
package org.apache.activemq.artemis.protocol.amqp.broker;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.persistence.CoreMessageObjectPools;
import org.apache.activemq.artemis.utils.DataConstants;

import static org.apache.activemq.artemis.core.persistence.PersisterIDs.AMQPMessagePersisterV4_ID;

/**
 * V4 adds a size field to determine persister boundaries, enabling forward-compatible
 * extensions without additional versioning.
 **/
public class AMQPMessagePersisterV4 extends AMQPMessagePersisterV3 {

   public static final byte ID = AMQPMessagePersisterV4_ID;

   public static AMQPMessagePersisterV4 theInstance;

   public static AMQPMessagePersisterV4 getInstance() {
      if (theInstance == null) {
         theInstance = new AMQPMessagePersisterV4();
      }
      return theInstance;
   }

   public AMQPMessagePersisterV4() {
      super();
   }

   @Override
   public byte getID() {
      return ID;
   }

   @Override
   public int getEncodeSize(Message record) {
      return DataConstants.SIZE_BYTE + record.getPersistSize() + getMetadataSize(record);
   }

   /** Write persister data using Map like boundaries from MapPersister */
   protected void writeMessageMetadata(ActiveMQBuffer buffer, Message record) {
      AMQPMessageMetadataPersister.getInstance().encode(buffer, record);
   }

   protected int getMetadataSize(Message record) {
      return AMQPMessageMetadataPersister.getInstance().getEncodeSize(record);
   }

   @Override
   public void encode(ActiveMQBuffer buffer, Message record) {
      writePersisterID(buffer);

      writeMessageMetadata(buffer, record);

      record.persist(buffer);
   }

   @Override
   public Message decode(ActiveMQBuffer buffer, Message record, CoreMessageObjectPools pool) {
      AMQPMessageMetadataPersister metadataPersister = AMQPMessageMetadataPersister.getInstance();
      AMQPMetadataDecodingState decodingMetaData = new AMQPMetadataDecodingState();

      metadataPersister.decode(buffer, decodingMetaData);

      assert decodingMetaData.memoryEstimate > 0;

      AMQPStandardMessage standardMessage = new AMQPStandardMessage(decodingMetaData.messageFormat);
      standardMessage.reloadPersistence(buffer, pool);
      if (decodingMetaData.extraProperties != null) {
         standardMessage.setExtraProperties(decodingMetaData.extraProperties);
      }
      standardMessage.setMessageID(decodingMetaData.messageID);
      standardMessage.setAddress(decodingMetaData.address);
      standardMessage.setMemoryEstimate(decodingMetaData.memoryEstimate);
      standardMessage.reloadPriority(decodingMetaData.priority);
      standardMessage.reloadSetDurable(decodingMetaData.isDurable);
      standardMessage.reloadExpiration(decodingMetaData.messageExpiration);
      return standardMessage;
   }
}

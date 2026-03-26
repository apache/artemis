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

import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.persistence.CoreMessageObjectPools;
import org.apache.activemq.artemis.utils.DataConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.core.persistence.PersisterIDs.AMQPMessagePersisterV4_ID;

/**
 * V4 adds a size field to determine persister boundaries, enabling forward-compatible
 * extensions without additional versioning.
 **/
public class AMQPMessagePersisterV4 extends AMQPMessagePersisterV3 {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final byte ID = AMQPMessagePersisterV4_ID;


   protected static final int PERSISTER_SIZE = DataConstants.SIZE_INT + // memory estimate
      DataConstants.SIZE_BYTE +
      DataConstants.SIZE_BOOLEAN; // message priority

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
      return DataConstants.SIZE_BYTE + record.getPersistSize() + getMapCodecSize(record);
   }

   @Override
   public void encode(ActiveMQBuffer buffer, Message record) {
      writePersisterID(buffer);

      writeMapCodecData(buffer, record);

      record.persist(buffer);
   }

   /** Write persister data using Map like boundaries from MapPersister */
   protected void writeMapCodecData(ActiveMQBuffer buffer, Message record) {
      AMQPMessageMapCodec.getInstance().encode(buffer, record);
   }

   protected int getMapCodecSize(Message record) {
      return AMQPMessageMapCodec.getInstance().getEncodeSize(record);
   }


   @Override
   public Message decode(ActiveMQBuffer buffer, Message record, CoreMessageObjectPools pool) {
      AMQPMessageMapCodec mapCodec = AMQPMessageMapCodec.getInstance().reset();

      mapCodec.decode(buffer);

      assert mapCodec.memoryEstimate != 0;

      AMQPStandardMessage standardMessage = new AMQPStandardMessage(mapCodec.messageFormat);
      standardMessage.reloadPersistence(buffer, pool);
      if (mapCodec.extraProperties != null) {
         standardMessage.setExtraProperties(mapCodec.extraProperties);
      }
      standardMessage.setMessageID(mapCodec.messageID);
      standardMessage.setAddress(mapCodec.address);
      standardMessage.setMemoryEstimate(mapCodec.memoryEstimate);
      standardMessage.reloadPriority(mapCodec.priority);
      standardMessage.reloadSetDurable(mapCodec.isDurable);
      return standardMessage;
   }
}

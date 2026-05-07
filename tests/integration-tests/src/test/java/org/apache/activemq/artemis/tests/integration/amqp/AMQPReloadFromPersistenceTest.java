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
package org.apache.activemq.artemis.tests.integration.amqp;

import java.util.HashMap;
import java.util.Map;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.io.SequentialFile;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager;
import org.apache.activemq.artemis.logs.AssertionLoggerHandler;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPLargeMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPLargeMessagePersister;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPLargeMessagePersisterV2;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessageMapCodec;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessagePersister;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessagePersisterV2;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessagePersisterV3;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessagePersisterV4;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPStandardMessage;
import org.apache.activemq.artemis.protocol.amqp.util.NettyWritable;
import org.apache.activemq.artemis.tests.unit.core.journal.impl.fakes.FakeSequentialFileFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.transport.amqp.client.AmqpMessage;
import org.apache.qpid.proton.message.impl.MessageImpl;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AMQPReloadFromPersistenceTest extends ActiveMQTestBase {

   SimpleString stringAddedOnFake = RandomUtil.randomUUIDSimpleString();

   @Test
   public void testPersistCheck() throws Exception {
      internalPersistCheck(AMQPMessage.MessageDataScanningStatus.NOT_SCANNED, AMQPMessagePersisterV4.getInstance(), AMQPMessagePersisterV4.getInstance());
      try (AssertionLoggerHandler handler = new AssertionLoggerHandler()) {
         AssertionLoggerHandler.LogLevel originalLevel = AssertionLoggerHandler.setLevel(AMQPMessageMapCodec.class.getName(), AssertionLoggerHandler.LogLevel.TRACE);
         try {
            internalPersistCheck(AMQPMessage.MessageDataScanningStatus.NOT_SCANNED, new FakePersisterFromTheFuture(), AMQPMessagePersisterV4.getInstance());
            internalPersistCheck(AMQPMessage.MessageDataScanningStatus.NOT_SCANNED, AMQPMessagePersisterV4.getInstance(), new FakePersisterFromTheFuture());
            assertTrue(handler.findText(stringAddedOnFake.toString()));
         } finally {
            AssertionLoggerHandler.setLevel(AMQPMessageMapCodec.class.getName(), originalLevel);
         }
      }
   }

   @Test
   public void testPersistCheckOldVersion() throws Exception {
      internalPersistCheck(AMQPMessage.MessageDataScanningStatus.SCANNED, AMQPMessagePersisterV2.getInstance(), AMQPMessagePersisterV2.getInstance());
      internalPersistCheck(AMQPMessage.MessageDataScanningStatus.SCANNED, AMQPMessagePersister.getInstance(), AMQPMessagePersister.getInstance());
      internalPersistCheck(AMQPMessage.MessageDataScanningStatus.SCANNED, AMQPMessagePersisterV3.getInstance(), AMQPMessagePersisterV3.getInstance());
   }

   private void internalPersistCheck(AMQPMessage.MessageDataScanningStatus expectedStatusAfterReload, AMQPMessagePersister persisterOnWrite, AMQPMessagePersister persisterOnRead) throws Exception {

      Map map = new HashMap();
      for (int i = 0; i < 77; i++) {
         map.put("stuff" + i, "value" + i); // just filling stuff
      }
      boolean originalDurable = true;
      AMQPStandardMessage originalMessage = AMQPStandardMessage.createMessage(1, 0, SimpleString.of("duh"), null, null, null, null, map, null, null);
      byte originalPriority = 1;
      originalMessage.setPriority(originalPriority);
      originalMessage.setDurable(originalDurable);
      originalMessage.reencode();
      int originalMemoryEstimate = originalMessage.getMemoryEstimate();

      byte[] originalRandomBytes = RandomUtil.randomBytes(10);

      ActiveMQBuffer buffer = ActiveMQBuffers.dynamicBuffer(1024);
      persisterOnWrite.encode(buffer, originalMessage);
      buffer.writeBytes(originalRandomBytes);

      {
         buffer.readerIndex(1); // first byte is the persister version
         AMQPStandardMessage amqpStandardMessage = (AMQPStandardMessage) persisterOnRead.decode(buffer, null, null);
         assertEquals(expectedStatusAfterReload, amqpStandardMessage.getDataScanningStatus());
         assertEquals(originalPriority, amqpStandardMessage.getPriority());
         assertEquals(expectedStatusAfterReload, amqpStandardMessage.getDataScanningStatus());

         validateExtraBytes(originalRandomBytes, buffer);
      }

      {
         buffer.readerIndex(1); // first byte is the persister version
         AMQPStandardMessage amqpStandardMessage = (AMQPStandardMessage) persisterOnRead.decode(buffer, null, null);
         assertEquals(expectedStatusAfterReload, amqpStandardMessage.getDataScanningStatus());
         assertEquals(originalMemoryEstimate, amqpStandardMessage.getMemoryEstimate());
         assertEquals(expectedStatusAfterReload, amqpStandardMessage.getDataScanningStatus());

         validateExtraBytes(originalRandomBytes, buffer);
      }

      {
         buffer.readerIndex(1); // first byte is the persister version
         AMQPStandardMessage amqpStandardMessage = (AMQPStandardMessage) persisterOnRead.decode(buffer, null, null);
         assertEquals(expectedStatusAfterReload, amqpStandardMessage.getDataScanningStatus());
         assertEquals(originalDurable, amqpStandardMessage.isDurable());
         assertEquals(expectedStatusAfterReload, amqpStandardMessage.getDataScanningStatus());

         validateExtraBytes(originalRandomBytes, buffer);
      }

      // none of these should make it scan anything
      {
         buffer.readerIndex(1); // first byte is the persister version
         AMQPStandardMessage amqpStandardMessage = (AMQPStandardMessage) persisterOnRead.decode(buffer, null, null);
         assertEquals(expectedStatusAfterReload, amqpStandardMessage.getDataScanningStatus());
         assertEquals(77, amqpStandardMessage.getApplicationPropertiesCount());
         assertEquals(77, amqpStandardMessage.getApplicationProperties().getValue().size());
         // this check here should always be scanned, no matter the version you use
         assertEquals(AMQPMessage.MessageDataScanningStatus.SCANNED, amqpStandardMessage.getDataScanningStatus());

         validateExtraBytes(originalRandomBytes, buffer);
      }
   }

   private static void validateExtraBytes(byte[] originalRandomBytes, ActiveMQBuffer buffer) {
      byte[] randomBytes = new byte[originalRandomBytes.length];
      buffer.readBytes(randomBytes);
      assertArrayEquals(originalRandomBytes, randomBytes);
   }

   @Test
   public void testNoApplicationProperties() throws Exception {
      AMQPStandardMessage originalMessage = AMQPStandardMessage.createMessage(1, 0, SimpleString.of("duh"), null, null, null, null, null, null, null);

      AMQPMessagePersisterV4 persister = AMQPMessagePersisterV4.getInstance();

      ActiveMQBuffer buffer = ActiveMQBuffers.dynamicBuffer(1024);
      persister.encode(buffer, originalMessage);

      buffer.readerIndex(1); // skip version byte from the persister
      AMQPStandardMessage amqpStandardMessage = (AMQPStandardMessage) persister.decode(buffer, null, null);

      assertEquals(AMQPMessage.MessageDataScanningStatus.NOT_SCANNED, amqpStandardMessage.getDataScanningStatus());
      assertEquals(0, amqpStandardMessage.getApplicationPropertiesCount());
      assertEquals(AMQPMessage.MessageDataScanningStatus.SCANNED, amqpStandardMessage.getDataScanningStatus());
      assertNull(amqpStandardMessage.getApplicationProperties());
   }

   /**
    * Pretending to be a persister from the future with extra stuff, the current version should handle it okay by skipping the extra unkown data.
    * */
   @Test
   public void testPersistCheckLargeMessage() throws Exception {
      internalPersistCheckLargeMessage(AMQPLargeMessagePersister.getInstance(), AMQPLargeMessagePersister.getInstance(), false);
      internalPersistCheckLargeMessage(AMQPLargeMessagePersisterV2.getInstance(), AMQPLargeMessagePersisterV2.getInstance(), true);
      internalPersistCheckLargeMessage(AMQPLargeMessagePersisterV2.getInstance(), new FakeLargePersisterFromTheFuture(), true);

      try (AssertionLoggerHandler handler = new AssertionLoggerHandler()) {
         AssertionLoggerHandler.LogLevel originalLevel = AssertionLoggerHandler.setLevel(AMQPMessageMapCodec.class.getName(), AssertionLoggerHandler.LogLevel.TRACE);
         try {
            internalPersistCheckLargeMessage(new FakeLargePersisterFromTheFuture(), AMQPLargeMessagePersisterV2.getInstance(), true);
            assertTrue(handler.findText(stringAddedOnFake.toString()));
         } finally {
            AssertionLoggerHandler.setLevel(AMQPMessageMapCodec.class.getName(), originalLevel);
         }
      }
   }

   private void internalPersistCheckLargeMessage(AMQPLargeMessagePersister persisterOnWrite, AMQPLargeMessagePersister persisterOnRead, boolean checkMemoryEstimate) throws Exception {
      FakeSequentialFileFactory fakeSequentialFileFactory = new FakeSequentialFileFactory();
      final StorageManager storageManager = new NullStorageManager() {
         @Override
         public SequentialFile createFileForLargeMessage(long messageID, boolean durable) {
            return fakeSequentialFileFactory.createSequentialFile("message" + messageID + ".large");
         }
      };

      storageManager.start();

      AMQPLargeMessage originalMessage = createLargeMessage(storageManager, "test", 100, (byte) 3);
      ActiveMQBuffer buffer = ActiveMQBuffers.dynamicBuffer(1024);
      persisterOnWrite.encode(buffer, originalMessage);

      byte[] originalRandomBytes = RandomUtil.randomBytes(10);
      buffer.writeBytes(originalRandomBytes);


      {
         buffer.readerIndex(1); // first byte is the persister version
         AMQPLargeMessage largeMessageRead = (AMQPLargeMessage) persisterOnRead.decode(buffer, null, null);
         assertEquals((byte) 3, largeMessageRead.getPriority());
         assertTrue(largeMessageRead.isDurable());
         if (checkMemoryEstimate) {
            assertEquals(originalMessage.getMemoryEstimate(), largeMessageRead.getMemoryEstimate());
         }
         validateExtraBytes(originalRandomBytes, buffer);
      }
   }

   private static MessageImpl createProtonMessage(String address, byte priority) {
      AmqpMessage message = new AmqpMessage();
      message.setBytes(RandomUtil.randomBytes(10));
      message.setAddress(address);
      message.setDurable(true);
      message.setPriority(priority);
      MessageImpl protonMessage = (MessageImpl) message.getWrappedMessage();
      return protonMessage;
   }

   private static AMQPLargeMessage createLargeMessage(StorageManager storageManager,
                                                      String address,
                                                      long msgId,
                                                      byte priority) throws Exception {
      MessageImpl protonMessage = createProtonMessage(address, priority);
      byte[] messageContent = encodeProtonMessage(protonMessage, 1024);
      final AMQPLargeMessage amqpMessage = new AMQPLargeMessage(msgId, 0, null, null, storageManager);
      amqpMessage.setAddress(address);
      amqpMessage.setFileDurable(true);
      amqpMessage.addBytes(messageContent);
      amqpMessage.reloadExpiration(0);
      return amqpMessage;
   }

   private static byte @NonNull [] encodeProtonMessage(MessageImpl message, int expectedSize) {
      ByteBuf nettyBuffer = Unpooled.buffer(expectedSize);
      message.encode(new NettyWritable(nettyBuffer));
      byte[] bytes = new byte[nettyBuffer.writerIndex()];
      nettyBuffer.readBytes(bytes);
      return bytes;
   }



   class FakeLargePersisterFromTheFuture extends AMQPLargeMessagePersisterV2 {

      FakeLargePersisterFromTheFuture() {
         super();
      }

      @Override
      protected void writeMapCodecData(ActiveMQBuffer buffer, Message record) {
         new FutureMap().encode(buffer, record);
      }

      @Override
      protected int getMapCodecSize(Message record) {
         return new FutureMap().getEncodeSize(record);
      }

   }

   class FakePersisterFromTheFuture extends AMQPMessagePersisterV4 {

      FakePersisterFromTheFuture() {
         super();
      }

      @Override
      protected void writeMapCodecData(ActiveMQBuffer buffer, Message record) {
         new FutureMap().encode(buffer, record);
      }

      @Override
      protected int getMapCodecSize(Message record) {
         return new FutureMap().getEncodeSize(record);
      }

   }

   class FutureMap extends AMQPMessageMapCodec {

      private final short ID_FAKE = (short) 101;

      @Override
      protected int getPayloadSize(Message record) {
         return super.getPayloadSize(record) + payloadSizeSimpleString(stringAddedOnFake);
      }

      @Override
      public int getNumberOfElements(Message record) {
         return super.getNumberOfElements(record) + 1;
      }

      @Override
      protected void encodeElements(ActiveMQBuffer buffer, AMQPMessage msgEncode) {
         super.encodeElements(buffer, msgEncode);
         writeSimpleString(buffer, ID_FAKE, stringAddedOnFake);
      }
   }




}

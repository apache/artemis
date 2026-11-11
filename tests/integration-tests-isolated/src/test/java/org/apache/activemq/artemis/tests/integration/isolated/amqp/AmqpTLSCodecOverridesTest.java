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

package org.apache.activemq.artemis.tests.integration.isolated.amqp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.invoke.MethodHandles;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport;
import org.apache.activemq.artemis.protocol.amqp.util.TLSEncode;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.codec.EncoderImpl;
import org.apache.qpid.proton.codec.EncodingCodes;
import org.apache.qpid.protonj2.test.driver.ProtonTestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for configuration of the max transfers per delivery option
 */
class AmqpTLSCodecOverridesTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final int maxDecodeDepth = AmqpSupport.DEFAULT_MAX_DECODE_DEPTH + 1;
   private final int zeroWidthArrayElementLimit = AmqpSupport.DEFAULT_ZERO_WIDTH_ARRAY_ELEMENT_LIMIT + 1;

   @Test
   @Timeout(20)
   public void testTLSEncodeAppliesSystemPropertiesForProtonDecodeControls() throws Exception {
      ActiveMQServer server = createServer(true, createDefaultConfig(true));
      server.start();
      server.createQueue(QueueConfiguration.of(getTestMethodName())
                                           .setRoutingType(RoutingType.ANYCAST)
                                           .setAddress(getTestMethodName())
                                           .setAutoCreated(false));

      System.setProperty(TLSEncode.MAX_DECODE_DEPTH_PROPERTY, Integer.toString(maxDecodeDepth));
      System.setProperty(TLSEncode.ZERO_WIDTH_ARRAY_ELEMENT_LIMIT_PROPERTY, Integer.toString(zeroWidthArrayElementLimit));

      runAfter(() -> {
         System.clearProperty(TLSEncode.MAX_DECODE_DEPTH_PROPERTY);
         System.clearProperty(TLSEncode.ZERO_WIDTH_ARRAY_ELEMENT_LIMIT_PROPERTY);
      });

      assertEquals(maxDecodeDepth, TLSEncode.getCodec().getMaxDecodeDepth());
      assertEquals(zeroWidthArrayElementLimit, TLSEncode.getCodec().getZeroWidthArrayElementLimit());

      try (ProtonTestClient peer = new ProtonTestClient()) {
         peer.queueClientSaslAnonymousConnect();
         peer.connect("localhost", 61616);
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         logger.info("Test started, client connected on: {}", 61616);

         peer.expectOpen();
         peer.expectBegin();
         peer.expectAttach().ofReceiver();
         peer.expectFlow();
         peer.remoteOpen().withContainerId("test-sender").now();
         peer.remoteBegin().now();
         peer.remoteAttach().ofSender()
                            .withInitialDeliveryCount(0)
                            .withName("sending-peer")
                            .withTarget().withAddress(getTestMethodName())
                                         .withCapabilities("queue").also()
                            .withSource().also()
                            .now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final ByteBuffer zeroWidthPayload = createEncodedMessageWithZeroWidthArray(zeroWidthArrayElementLimit); // Should work

         peer.expectDisposition().withState().accepted();
         peer.remoteTransfer().withHandle(0)
                              .withDeliveryId(0)
                              .withDeliveryTag(new byte[] {1})
                              .withMore(false)
                              .withMessageFormat(0)
                              .withPayload(zeroWidthPayload).now();

         final ByteBuffer nestedObjectPayload = createNestedEncodedMessage(maxDecodeDepth); // Should work

         peer.expectDisposition().withState().accepted();
         peer.remoteTransfer().withHandle(0)
                              .withDeliveryId(1)
                              .withDeliveryTag(new byte[] {2})
                              .withMore(false)
                              .withMessageFormat(0)
                              .withPayload(nestedObjectPayload).now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.remoteClose().now();
         peer.close();
      }

      try (ProtonTestClient peer = new ProtonTestClient()) {
         peer.queueClientSaslAnonymousConnect();
         peer.connect("localhost", 61616);
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         logger.info("Test started, client connected on: {}", 61616);

         peer.expectOpen();
         peer.expectBegin();
         peer.expectAttach().ofReceiver();
         peer.expectFlow();
         peer.remoteOpen().withContainerId("test-sender").now();
         peer.remoteBegin().now();
         peer.remoteAttach().ofSender()
                            .withInitialDeliveryCount(0)
                            .withName("sending-peer")
                            .withTarget().withAddress(getTestMethodName())
                                         .withCapabilities("queue").also()
                            .withSource().also()
                            .now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final ByteBuffer zeroWidthPayload = createEncodedMessageWithZeroWidthArray(zeroWidthArrayElementLimit + 1); // Shouldn't work

         peer.expectClose().withError(AmqpError.INTERNAL_ERROR.toString());
         peer.remoteTransfer().withHandle(0)
                              .withDeliveryId(0)
                              .withDeliveryTag(new byte[] {1})
                              .withMore(false)
                              .withMessageFormat(0)
                              .withPayload(zeroWidthPayload).now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.remoteClose().now();
         peer.close();
      }

      try (ProtonTestClient peer = new ProtonTestClient()) {
         peer.queueClientSaslAnonymousConnect();
         peer.connect("localhost", 61616);
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         logger.info("Test started, client connected on: {}", 61616);

         peer.expectOpen();
         peer.expectBegin();
         peer.expectAttach().ofReceiver();
         peer.expectFlow();
         peer.remoteOpen().withContainerId("test-sender").now();
         peer.remoteBegin().now();
         peer.remoteAttach().ofSender()
                            .withInitialDeliveryCount(0)
                            .withName("sending-peer")
                            .withTarget().withAddress(getTestMethodName())
                                         .withCapabilities("queue").also()
                            .withSource().also()
                            .now();
         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);

         final ByteBuffer nestedObjectPayload = createNestedEncodedMessage(maxDecodeDepth + 1); // Shouldn't work

         peer.expectClose().withError(AmqpError.INTERNAL_ERROR.toString());
         peer.remoteTransfer().withHandle(0)
                              .withDeliveryId(0)
                              .withDeliveryTag(new byte[] {1})
                              .withMore(false)
                              .withMessageFormat(0)
                              .withPayload(nestedObjectPayload).now();

         peer.waitForScriptToComplete(5, TimeUnit.SECONDS);
         peer.remoteClose().now();
         peer.close();
      }
   }

   // Must use a message annotations section since the broker won't normally decode
   // the body section unless doing a conversion later.
   private ByteBuffer createNestedEncodedMessage(final int depth) {
      if (depth < 2) {
         throw new IllegalArgumentException("depth needs to be greater than two to account for Section layers");
      }

      final EncoderImpl encoder = TLSEncode.getEncoder();
      final ByteBuffer buffer = ByteBuffer.allocate(128);
      final List<Object> entry = new ArrayList<>();
      final Map<Symbol, Object> map = new HashMap<>();
      final MessageAnnotations value = new MessageAnnotations(map);

      List<Object> current = entry;
      for (int i = 2; i < depth; ++i) {
         final List<Object> next = new ArrayList<>();

         current.add(next);
         current = next;
      }

      map.put(Symbol.valueOf("a"), entry);

      try {
         encoder.setByteBuffer(buffer);
         encoder.writeObject(value);
      } finally {
         encoder.setByteBuffer((ByteBuffer) null);
      }

      return buffer.flip();
   }

   // Must use a message annotations section since the broker won't normally decode
   // the body section unless doing a conversion later.
   protected ByteBuffer createEncodedMessageWithZeroWidthArray(int length) {
      if (length < 0 || length > 255) {
         throw new IllegalArgumentException("Length must be within the range of an unsigned byte");
      }

      final ByteBuffer buffer = ByteBuffer.allocate(16);

      buffer.put((byte) 0); // Described Type Indicator
      buffer.put(EncodingCodes.SMALLULONG);
      buffer.put((byte) 0x72); // message annotations descriptor
      buffer.put(EncodingCodes.MAP8);
      buffer.put((byte) 8);  // Size
      buffer.put((byte) 2);  // Count
      buffer.put(EncodingCodes.SYM8);
      buffer.put((byte) 1);
      buffer.put((byte) 65);
      buffer.put(EncodingCodes.ARRAY8);
      buffer.put((byte) 2);
      buffer.put((byte) length);
      buffer.put(EncodingCodes.BOOLEAN_TRUE);

      return buffer.flip();
   }
}
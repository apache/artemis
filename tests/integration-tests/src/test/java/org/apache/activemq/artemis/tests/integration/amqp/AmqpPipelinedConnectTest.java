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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.activemq.artemis.utils.Wait;
import org.apache.qpid.proton.amqp.security.SaslCode;
import org.apache.qpid.proton.amqp.security.SaslFrameBody;
import org.apache.qpid.proton.amqp.security.SaslOutcome;
import org.apache.qpid.proton.amqp.transport.AmqpError;
import org.apache.qpid.proton.amqp.transport.Close;
import org.apache.qpid.proton.amqp.transport.FrameBody;
import org.apache.qpid.proton.codec.AMQPDefinedTypes;
import org.apache.qpid.proton.codec.DecoderImpl;
import org.apache.qpid.proton.codec.EncoderImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AmqpPipelinedConnectTest extends AmqpClientTestSupport {

   protected static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Override
   protected boolean isSecurityEnabled() {
      return true;
   }

   @Test
   @Timeout(30)
   public void testPipelinedOpenWhenAnonymousWillFail() throws Exception {

      // Frame data for: SaslInit
      //   SaslInit{mechanism=ANONYMOUS, initialResponse=null, hostname='localhost'}
      // Frame data for: Open
      //   Open{ containerId='204c1d45-9c47-402d-809f-7d17a4d97d6e', hostname='localhost', maxFrameSize=131072, channelMax=32767,
      //         idleTimeOut=null, outgoingLocales=null, incomingLocales=null, offeredCapabilities=null, desiredCapabilities=null,
      //         properties=null}
      // Frame data for: Begin
      //   Begin{remoteChannel=null, nextOutgoingId=0, incomingWindow=2147483647, outgoingWindow=2147483647, handleMax=4294967295,
      //         offeredCapabilities=null, desiredCapabilities=null, properties=null}
      // Frame data for: Attach
      //   Attach{name='2b46ad5b-834b-454e-a2f7-2e5e0e324e21', handle=0, role=SENDER, sndSettleMode=MIXED, rcvSettleMode=FIRST,
      //          source=Source{address='null', durable=NONE, expiryPolicy=SESSION_END, timeout=0, dynamic=false,
      //          dynamicNodeProperties=null, distributionMode=null, filter=null, defaultOutcome=null, outcomes=null,
      //          capabilities=null}, target=Target{address='examples', durable=NONE, expiryPolicy=SESSION_END, timeout=0,
      //          dynamic=false, dynamicNodeProperties=null, capabilities=null}, unsettled=null, incompleteUnsettled=false,
      //          initialDeliveryCount=0, maxMessageSize=0, offeredCapabilities=null, desiredCapabilities=null, properties=null}
      final byte[] pipelined = new byte[] {
         // SASL Header
         'A', 'M', 'Q', 'P', 3, 1, 0, 0,
         // SASL Init
         0, 0, 0, 37, 2, 1, 0, 0, 0, 83, 65, -64, 24, 3, -93, 9, 65, 78, 79, 78, 89, 77, 79,
         85, 83, 64, -95, 9, 108, 111, 99, 97, 108, 104, 111, 115, 116,
         // AMQP Header
         'A', 'M', 'Q', 'P', 0, 1, 0, 0,
         // Open
         0, 0, 0, 71, 2, 0, 0, 0, 0, 83, 16, -64, 58, 4, -95, 36, 50, 48, 52, 99, 49, 100, 52,
         53, 45, 57, 99, 52, 55, 45, 52, 48, 50, 100, 45, 56, 48, 57, 102, 45, 55, 100, 49, 55,
         97, 52, 100, 57, 55, 100, 54, 101, -95, 9, 108, 111, 99, 97, 108, 104, 111, 115, 116,
         112, 0, 2, 0, 0, 96, 127, -1,
         // Begin
         0, 0, 0, 26, 2, 0, 0, 0, 0, 83, 17, -64, 13, 4, 64, 67, 112, 127, -1, -1, -1, 112,
         127, -1, -1, -1,
         // Attach
         0, 0, 0, 82, 2, 0, 0, 0, 0, 83, 18, -64, 69, 11, -95, 36, 50, 98, 52, 54, 97, 100, 53,
         98, 45, 56, 51, 52, 98, 45, 52, 53, 52, 101, 45, 97, 50, 102, 55, 45, 50, 101, 53, 101,
         48, 101, 51, 50, 52, 101, 50, 49, 67, 66, 80, 2, 80, 0, 0, 83, 40, 69, 0, 83, 41, -64,
         11, 1, -95, 8, 101, 120, 97, 109, 112, 108, 101, 115, 64, 66, 67, 68};

      final AtomicBoolean closedReceived = new AtomicBoolean();
      final AtomicReference<Throwable> failure = new AtomicReference<>();
      final AtomicInteger saslPerformatives = new AtomicInteger();
      final AtomicInteger performatives = new AtomicInteger();

      try (ClientConnection connection = new ClientConnection()) {
         connection.open("localhost", AMQP_PORT);
         connection.send(pipelined);
         connection.readFromRemote(new FrameDecoder(new FrameBodyHandler() {

            @Override
            public void onSaslFrame(SaslFrameBody saslType) {
               saslPerformatives.incrementAndGet();

               if (saslType instanceof SaslOutcome outcome) {
                  if (outcome.getCode() != SaslCode.OK) {
                     // We are expecting SASL outcome to be OK which it isn't then the
                     // broker behavior has changed and this should fail the test.
                     failure.compareAndSet(null, new AssertionError("SASL outcome expected to be OK but wasn't"));
                  }
               }
            }

            @Override
            public void onError(AssertionError error) {
               failure.compareAndSet(null, error);
            }

            @Override
            public void onAMQPFrame(FrameBody amqpType) {
               performatives.incrementAndGet();
               if (amqpType instanceof Close close) {
                  closedReceived.set(true);

                  if (close.getError() == null || !AmqpError.UNAUTHORIZED_ACCESS.equals(close.getError().getCondition())) {
                     failure.compareAndSet(null, new AssertionError("Connection should indicate access was unauthorized"));
                  }

                  connection.close();
               }
            }
         }));

         Wait.waitFor(() -> closedReceived.get());
      }

      assertEquals(2, saslPerformatives.get()); // Mechanisms and Outcome
      assertEquals(2, performatives.get()); // Open and Close

      assertNull(failure.get());
   }

   private class ClientConnection implements AutoCloseable {

      protected static final long RECEIVE_TIMEOUT = 10000;
      protected Socket clientSocket;

      public void open(String host, int port) throws IOException {
         clientSocket = new Socket(host, port);
         clientSocket.setTcpNoDelay(true);
      }

      public void send(byte[] data) throws Exception {
         final OutputStream outputStream = clientSocket.getOutputStream();
         outputStream.write(data);
         outputStream.flush();
      }

      @Override
      public void close() {
         try {
            clientSocket.close();
         } catch (IOException e) {
         }
      }

      public void readFromRemote(FrameDecoder decoder) throws Exception {
         clientSocket.setSoTimeout((int) RECEIVE_TIMEOUT);
         InputStream is = clientSocket.getInputStream();

         while (true) {
            byte[] incoming = new byte[1024];
            try {
               int read = is.read(incoming);

               if (read == -1) {
                  is.close();
                  return;
               }

               final ByteBuffer packet = ByteBuffer.wrap(incoming, 0, read);

               while (packet.hasRemaining()) {
                  decoder.ingest(packet);
               }
            } catch (Exception ex) {
               break;
            }
         }
      }
   }

   private interface FrameBodyHandler {

      void onSaslFrame(SaslFrameBody saslType);

      void onAMQPFrame(FrameBody amqpType);

      void onError(AssertionError error);

   }

   private static class FrameDecoder {

      public static final int FRAME_SIZE_BYTES = 4;
      public static final byte AMQP_FRAME_TYPE = (byte) 0;
      public static final byte SASL_FRAME_TYPE = (byte) 1;

      private final FrameBodyHandler performativeHandler;

      private final DecoderImpl decoder = new DecoderImpl();
      private final EncoderImpl encoder = new EncoderImpl(decoder);

      private FrameParserStage stage = new HeaderParsingStage();

      private final FrameSizeParsingStage frameSizeParser = new FrameSizeParsingStage();
      private final FrameBufferingStage frameBufferingStage = new FrameBufferingStage();
      private final FrameParserStage frameBodyParsingStage = new FrameBodyParsingStage();

      FrameDecoder(FrameBodyHandler performativeHandler) {
         this.performativeHandler = performativeHandler;

         AMQPDefinedTypes.registerAllTypes(decoder, encoder);
      }

      public void ingest(ByteBuffer buffer) throws AssertionError {
         try {
            stage.parse(buffer);
         } catch (AssertionError ex) {
            transitionToErrorStage(ex);
            performativeHandler.onError(ex);
            throw ex;
         } catch (Throwable throwable) {
            AssertionError error = new AssertionError("Frame decode failed.", throwable);
            transitionToErrorStage(error);
            performativeHandler.onError(error);
            throw error;
         }
      }

      // ---- Methods to transition between stages

      private FrameParserStage transitionToHeaderParsingStage() {
         return stage = new HeaderParsingStage();
      }

      private FrameParserStage transitionToFrameSizeParsingStage() {
         return stage = frameSizeParser.reset(0);
      }

      private FrameParserStage transitionToFrameBufferingStage(int frameSize) {
         return stage = frameBufferingStage.reset(frameSize);
      }

      private FrameParserStage initializeFrameBodyParsingStage(int frameSize) {
         return stage = frameBodyParsingStage.reset(frameSize);
      }

      private ParsingErrorStage transitionToErrorStage(AssertionError error) {
         if (!(stage instanceof ParsingErrorStage)) {
            stage = new ParsingErrorStage(error);
         }

         return (ParsingErrorStage) stage;
      }

      // ----- Frame Parsing Stage definition

      private interface FrameParserStage {

         void parse(ByteBuffer input) throws AssertionError;

         FrameParserStage reset(int frameSize);

      }

      // ---- Built in FrameParserStages

      private class HeaderParsingStage implements FrameParserStage {

         private static final int HEADER_SIZE_BYTES = 8;

         private final byte[] headerBytes = new byte[HEADER_SIZE_BYTES];

         private int headerByte;

         @Override
         public void parse(ByteBuffer incoming) throws AssertionError {
            while (incoming.hasRemaining() && headerByte < HEADER_SIZE_BYTES) {
               headerBytes[headerByte++] = incoming.get();
            }

            if (headerByte == HEADER_SIZE_BYTES) {
               // Transition to parsing the frames if any pipelined into this buffer.
               transitionToFrameSizeParsingStage();
            }
         }

         @Override
         public HeaderParsingStage reset(int frameSize) {
            headerByte = 0;
            return this;
         }
      }

      private class FrameSizeParsingStage implements FrameParserStage {

         private int frameSize;
         private int multiplier = FRAME_SIZE_BYTES;

         @Override
         public void parse(ByteBuffer input) throws AssertionError {
            while (input.hasRemaining()) {
               frameSize |= (input.get() & 0xFF) << (--multiplier * Byte.SIZE);
               if (multiplier == 0) {
                  break;
               }
            }

            if (multiplier == 0) {
               int length = frameSize - FRAME_SIZE_BYTES;

               if (input.remaining() < length) {
                  transitionToFrameBufferingStage(length);
               } else {
                  initializeFrameBodyParsingStage(length);
               }

               stage.parse(input);
            }
         }

         @Override
         public FrameSizeParsingStage reset(int frameSize) {
            this.multiplier = FRAME_SIZE_BYTES;
            this.frameSize = frameSize;
            return this;
         }
      }

      private class FrameBufferingStage implements FrameParserStage {

         private ByteBuffer buffer;

         @Override
         public void parse(ByteBuffer input) throws AssertionError {
            if (input.remaining() < buffer.limit()) {
               buffer.put(input);
            } else {
               final int remaining = buffer.remaining();

               buffer.put(input.slice().limit(input.position() + remaining));
               input.position(input.position() + remaining);

               initializeFrameBodyParsingStage(buffer.flip().remaining());
               stage.parse(buffer);
            }
         }

         @Override
         public FrameBufferingStage reset(int frameSize) {
            buffer = ByteBuffer.allocate(frameSize);
            return this;
         }
      }

      private class FrameBodyParsingStage implements FrameParserStage {

         private int frameSize;

         @Override
         public void parse(ByteBuffer input) throws AssertionError {
            final int dataOffset = (input.get() << 2) & 0x3FF;
            final int frameSize = this.frameSize + FRAME_SIZE_BYTES;
            final int frameType = input.get() & 0xFF;

            input.getShort();  // Read but ignore channel in this handler

            // note that this skips over the extended header if it's present
            if (dataOffset != 8) {
               input.position(input.position() + dataOffset - 8);
            }

            final int frameBodySize = frameSize - dataOffset;

            if (frameBodySize > 0) {
               decoder.setByteBuffer(input);
               final Object body = decoder.readObject();
               decoder.setByteBuffer(null);

               logger.trace("Read Frame body: {}", body);

               if (frameType == AMQP_FRAME_TYPE) {
                  FrameBody performative = (FrameBody) body;
                  transitionToFrameSizeParsingStage();
                  performativeHandler.onAMQPFrame(performative);
               } else if (frameType == SASL_FRAME_TYPE) {
                  SaslFrameBody performative = (SaslFrameBody) body;
                  if (performative instanceof SaslOutcome) {
                     transitionToHeaderParsingStage();
                  } else {
                     transitionToFrameSizeParsingStage();
                  }
                  performativeHandler.onSaslFrame(performative);
               } else {
                  throw new AssertionError(String.format("unknown frame type: %d", frameType));
               }
            }
         }

         @Override
         public FrameBodyParsingStage reset(int frameSize) {
            this.frameSize = frameSize;
            return this;
         }
      }

      private static class ParsingErrorStage implements FrameParserStage {

         private final AssertionError parsingError;

         ParsingErrorStage(AssertionError parsingError) {
            this.parsingError = parsingError;
         }

         @Override
         public void parse(ByteBuffer input) throws AssertionError {
            throw parsingError;
         }

         @Override
         public ParsingErrorStage reset(int frameSize) {
            return this;
         }
      }
   }
}

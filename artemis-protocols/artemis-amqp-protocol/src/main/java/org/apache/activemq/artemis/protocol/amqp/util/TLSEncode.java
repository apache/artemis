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
package org.apache.activemq.artemis.protocol.amqp.util;

import org.apache.activemq.artemis.protocol.amqp.proton.AmqpSupport;
import org.apache.qpid.proton.codec.AMQPDefinedTypes;
import org.apache.qpid.proton.codec.DecoderImpl;
import org.apache.qpid.proton.codec.EncoderImpl;

/**
 * Thread local codec support class that manages the Qpid proton Encoder and Decoder types.
 */
public class TLSEncode {

   public static final String MAX_DECODE_DEPTH_PROPERTY = "artemis.amqp.maxDecodeDepth";
   public static final String ZERO_WIDTH_ARRAY_ELEMENT_LIMIT_PROPERTY = "artemis.amqp.zeroWidthArrayElementLimit";

   public static class EncoderDecoderPair {

      // Static configuration options created once for the broker instance to ensure
      // all decoders are using the same values at all times during a given run so that
      // a mix of values cannot exist for incoming messages or for a journal reader etc.

      private static final int MAX_DECODE_DEPTH =
         Integer.getInteger(MAX_DECODE_DEPTH_PROPERTY, AmqpSupport.DEFAULT_MAX_DECODE_DEPTH);
      private static final int ZERO_WIDTH_ARRAY_ELEMENT_LIMIT =
         Integer.getInteger(ZERO_WIDTH_ARRAY_ELEMENT_LIMIT_PROPERTY, AmqpSupport.DEFAULT_ZERO_WIDTH_ARRAY_ELEMENT_LIMIT);

      private final DecoderImpl decoder = new DecoderImpl();
      private final EncoderImpl encoder = new EncoderImpl(decoder);

      EncoderDecoderPair() {
         AMQPDefinedTypes.registerAllTypes(decoder, encoder);
      }

      public EncoderImpl getEncoder() {
         return encoder;
      }

      public int getMaxDecodeDepth() {
         return MAX_DECODE_DEPTH;
      }

      public int getZeroWidthArrayElementLimit() {
         return ZERO_WIDTH_ARRAY_ELEMENT_LIMIT;
      }

      public DecoderImpl getDecoder() {
         final DecoderImpl decoder = this.decoder;

         // Each returned decoder is reset to configured or assigned defaults to ensure a
         // consistent starting point, the caller can assign new values which we don't want
         // to be sticky between calls.
         decoder.setMaxDecodeDepth(MAX_DECODE_DEPTH);
         decoder.setZeroWidthArrayElementLimit(ZERO_WIDTH_ARRAY_ELEMENT_LIMIT);

         return decoder;
      }
   }

   private static final ThreadLocal<EncoderDecoderPair> tlsCodec = ThreadLocal.withInitial(() -> new EncoderDecoderPair());

   public static EncoderDecoderPair getCodec() {
      return tlsCodec.get();
   }

   public static EncoderImpl getEncoder() {
      return tlsCodec.get().getEncoder();
   }

   public static DecoderImpl getDecoder() {
      return tlsCodec.get().getDecoder();
   }
}

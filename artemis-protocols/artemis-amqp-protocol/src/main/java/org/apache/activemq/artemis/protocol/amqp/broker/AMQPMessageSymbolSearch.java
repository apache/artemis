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
package org.apache.activemq.artemis.protocol.amqp.broker;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.protocol.amqp.util.TLSEncode;
import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.amqp.messaging.AmqpSequence;
import org.apache.qpid.proton.amqp.messaging.AmqpValue;
import org.apache.qpid.proton.amqp.messaging.ApplicationProperties;
import org.apache.qpid.proton.amqp.messaging.Data;
import org.apache.qpid.proton.amqp.messaging.Footer;
import org.apache.qpid.proton.amqp.messaging.MessageAnnotations;
import org.apache.qpid.proton.amqp.messaging.Properties;
import org.apache.qpid.proton.codec.DecoderImpl;
import org.apache.qpid.proton.codec.ReadableBuffer;
import org.apache.qpid.proton.codec.TypeConstructor;

final class AMQPMessageSymbolSearch {

   // used to quick search for MessageAnnotations
   private static final Map<Class<?>, Boolean> MSG_ANNOTATIONS_STOPSET;
   private static final Map<Class<?>, Boolean> APPLICATION_PROPERTIES_STOPSET;

   static {
      // we're including MessageAnnotations here because it will still cause termination
      List<Class<?>> classList = Arrays.asList(MessageAnnotations.class, Properties.class,
              ApplicationProperties.class, Data.class,
              AmqpSequence.class, AmqpValue.class, Footer.class);
      MSG_ANNOTATIONS_STOPSET = new IdentityHashMap<>(classList.size());
      classList.forEach(clazz -> MSG_ANNOTATIONS_STOPSET.put(clazz, Boolean.TRUE));

      // we're including ApplicationProperties here because it will still cause termination
      classList = Arrays.asList(ApplicationProperties.class, Data.class,
              AmqpSequence.class, AmqpValue.class, Footer.class);
      APPLICATION_PROPERTIES_STOPSET = new IdentityHashMap<>(classList.size());
      classList.forEach(clazz -> APPLICATION_PROPERTIES_STOPSET.put(clazz, Boolean.TRUE));
   }

   public static KMPNeedle kmpNeedleOf(Symbol symbol) {
      return KMPNeedle.of(symbol.toString().getBytes(StandardCharsets.US_ASCII));
   }

   public static KMPNeedle kmpNeedleOf(String symbol) {
      return KMPNeedle.of(symbol.getBytes(StandardCharsets.US_ASCII));
   }


   public static boolean anyMessageAnnotations(final ReadableBuffer data, final KMPNeedle[] needles) {
      return lookupOnSection(MSG_ANNOTATIONS_STOPSET, MessageAnnotations.class, data, needles, 0);
   }

   public static boolean anyApplicationProperties(final ReadableBuffer data, final KMPNeedle[] needles, int startAt) {
      return lookupOnSection(APPLICATION_PROPERTIES_STOPSET, ApplicationProperties.class, data, needles, startAt);
   }

   private static boolean lookupOnSection(Map<Class<?>, Boolean> stopSet, final Class section, final ReadableBuffer data, final KMPNeedle[] needles, final int startAt) {
      DecoderImpl decoder = TLSEncode.getDecoder();
      final int position = data.position();
      decoder.setBuffer(data.position(startAt));
      try {
         while (data.hasRemaining()) {
            TypeConstructor<?> constructor = decoder.readConstructor();
            final Class<?> typeClass = constructor.getTypeClass();
            if (stopSet.containsKey(typeClass)) {
               if (section.equals(typeClass)) {
                  final int start = data.position();
                  constructor.skipValue();
                  final int end = data.position();
                  for (int i = 0, count = needles.length; i < count; i++) {
                     final int foundIndex = needles[i].searchInto(data, start, end);
                     if (foundIndex != -1) {
                        return true;
                     }
                  }
               }
               return false;
            }
            constructor.skipValue();
         }
         return false;
      } finally {
         decoder.setBuffer(null);
         data.position(position);
      }
   }

}

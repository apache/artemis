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
package org.apache.activemq.artemis.tests.performance.jmh;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.postoffice.impl.AddressMap;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@Fork(2)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
public class AddressMapPerfTest {


   public AddressMap<Object> objectAddressMap;

   @Param({"2", "8", "10"})
   int entriesLog2;
   int entries;
   private static final WildcardConfiguration WILDCARD_CONFIGURATION;
   SimpleString[] keys;

   static {
      WILDCARD_CONFIGURATION = new WildcardConfiguration();
      WILDCARD_CONFIGURATION.setAnyWords('>');
      WILDCARD_CONFIGURATION.setSingleWord('*');
   }

   @Setup
   public void init() {
      objectAddressMap =
         new AddressMap<>(WILDCARD_CONFIGURATION.getAnyWordsString(), WILDCARD_CONFIGURATION.getSingleWordString(), WILDCARD_CONFIGURATION.getDelimiter());

      entries = 1 << entriesLog2;
      keys = new SimpleString[entries];
      for (int i = 0; i < entries; i++) {
         keys[i] = SimpleString.of("topic." + i % entriesLog2 + "." + i);
      }
   }

   @State(value = Scope.Thread)
   public static class ThreadState {

      long next;
      SimpleString[] keys;
      AtomicInteger counter = new AtomicInteger();

      @Setup
      public void init(AddressMapPerfTest benchmarkState) {
         keys = benchmarkState.keys;
      }

      public SimpleString nextKeyValue() {
         final long current = next;
         next = current + 1;
         final int index = (int) (current & (keys.length - 1));
         return keys[index];
      }
   }

   @Benchmark
   @Group("both")
   @GroupThreads(2)
   public void testPutWhileRemove(ThreadState state) {
      SimpleString s = state.nextKeyValue();
      objectAddressMap.put(s, s);
   }

   @Benchmark
   @Group("both")
   @GroupThreads(2)
   public void testRemoveWhilePut(ThreadState state) {
      SimpleString s = state.nextKeyValue();
      objectAddressMap.remove(s, s);
   }

   @Benchmark
   @GroupThreads(4)
   public void testPutAndVisit(final ThreadState state) throws Exception {
      SimpleString s = state.nextKeyValue();
      objectAddressMap.put(s, s);

      // look for it
      objectAddressMap.visitMatchingWildcards(s, value -> state.counter.incrementAndGet());
   }


}


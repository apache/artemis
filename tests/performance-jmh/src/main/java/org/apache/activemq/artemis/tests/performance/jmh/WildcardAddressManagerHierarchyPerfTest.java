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

import java.text.MessageFormat;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.impl.WildcardAddressManager;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@Fork(2)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
public class WildcardAddressManagerHierarchyPerfTest {

   public WildcardAddressManager addressManager;

   @Param({"2", "8", "10"})
   int topicsLog2;

   @Param({"true", "false"})
   boolean verifyWildcardBinding;

   int topics;
   AtomicLong topicCounter;
   int partitions;
   private static final WildcardConfiguration WILDCARD_CONFIGURATION;
   SimpleString[] addresses;
   Binding[] bindings;

   static {
      WILDCARD_CONFIGURATION = new WildcardConfiguration();
      WILDCARD_CONFIGURATION.setAnyWords('>');
   }

   @Setup
   public void init() throws Exception {
      addressManager = new WildcardAddressManager(new BindingFactoryFake(), WILDCARD_CONFIGURATION, null, null);
      topics = 1 << topicsLog2;
      addresses = new SimpleString[topics];
      bindings = new Binding[topics];
      partitions = topicsLog2 * 2;
      for (int i = 0; i < topics; i++) {

         if (verifyWildcardBinding) {
            // ensure simple matches present
            addresses[i] = SimpleString.of(MessageFormat.format("Topic1.abc-{0}.def-{0}.{1}", i % partitions, i));
            addressManager.addBinding(new BindingFactoryFake.BindingFake(addresses[i], SimpleString.of("" + i), i));
         } else {
            // ensure wildcard matches present
            addresses[i] = SimpleString.of(MessageFormat.format("Topic1.abc-{0}.*.{1}", i % partitions, i));
            addressManager.addBinding(new BindingFactoryFake.BindingFake(addresses[i], SimpleString.of("" + i), i));

         }
      }

      topicCounter = new AtomicLong(0);
      topicCounter.set(topics);
   }

   private long nextId() {
      return topicCounter.incrementAndGet();
   }

   @State(value = Scope.Thread)
   public static class ThreadState {

      long next;
      SimpleString[] addresses;
      Binding binding;

      @Setup
      public void init(WildcardAddressManagerHierarchyPerfTest benchmarkState) {
         final long id = benchmarkState.nextId();
         addresses = benchmarkState.addresses;
         if (benchmarkState.verifyWildcardBinding) {
            binding = new BindingFactoryFake.BindingFake(SimpleString.of(MessageFormat.format("Topic1.abc-{0}.def-{1}.>", id % benchmarkState.partitions, id)), SimpleString.of("" + id), id);
         } else {
            binding = new BindingFactoryFake.BindingFake(SimpleString.of(MessageFormat.format("Topic1.abc-{0}.def-{0}.{1}", id % benchmarkState.partitions, id)), SimpleString.of("" + id), id);
         }
      }

      public SimpleString nextAddress() {
         final long current = next;
         next = current + 1;
         final int index = (int) (current & (addresses.length - 1));
         return addresses[index];
      }
   }

   @Benchmark
   @Threads(4)
   public Binding testJustAddRemoveNewBinding(ThreadState state) throws Exception {
      final Binding binding = state.binding;
      addressManager.addBinding(binding);
      addressManager.getBindingsForRoutingAddress(state.nextAddress());
      return addressManager.removeBinding(binding.getUniqueName(), null);
   }

}


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

import org.apache.activemq.artemis.utils.collections.ConcurrentAppendOnlyChunkedList;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
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
public class ConcurrentAppendOnlyChunkListLookupBenchmark {

   private static final Integer MSG = 0;

   @Param({"1000", "10000"})
   int size;

   @Param({"32"})
   int chunkSize;

   private ConcurrentAppendOnlyChunkedList<Integer> list;

   @Setup
   public void init() {
      list = new ConcurrentAppendOnlyChunkedList<>(chunkSize);
      for (int i = 0; i < size; i++) {
         list.add(MSG);
      }
   }

   @State(Scope.Thread)
   public static class Index {

      private int size;
      private int index;

      @Setup
      public void init(ConcurrentAppendOnlyChunkListLookupBenchmark benchmark) {
         index = 0;
         size = benchmark.size;
      }

      public int next() {
         index++;
         if (index == size) {
            index = 0;
            return 0;
         }
         return index;
      }
   }

   @Benchmark
   public Integer lookup(Index index) {
      return list.get(index.next());
   }

}

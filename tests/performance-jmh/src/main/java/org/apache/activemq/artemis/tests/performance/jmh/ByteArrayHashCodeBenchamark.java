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

import java.util.Arrays;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.utils.ByteUtil;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(2)
@Warmup(iterations = 5, time = 200, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 8, time = 200, timeUnit = TimeUnit.MILLISECONDS)
public class ByteArrayHashCodeBenchamark {

   @Param({"7", "8", "36"})
   private int length;

   @Param("0")
   private int seed;

   /**
    * The higher the less predictable for the CPU branch predictor.
    */
   @Param({"4", "10"})
   private int logPermutations;

   @Param({"true"})
   private boolean unsafe;

   private long seq;
   private int inputMask;
   private byte[][] inputs;

   @Setup
   public void init() {
      System.setProperty("io.netty.noUnsafe", Boolean.valueOf(!unsafe).toString());
      int inputSize = 1 << logPermutations;
      inputs = new byte[inputSize][];
      inputMask = inputs.length - 1;
      // splittable random can create repeatable sequence of inputs
      SplittableRandom random = new SplittableRandom(seed);
      for (int i = 0; i < inputs.length; i++) {
         final byte[] bytes = new byte[length];
         for (int b = 0; b < length; b++) {
            bytes[b] = (byte) random.nextInt(Byte.MIN_VALUE, Byte.MAX_VALUE + 1);
         }
         inputs[i] = bytes;
      }
   }

   private byte[] nextInput() {
      final long seq = this.seq;
      final int index = (int) (seq & inputMask);
      this.seq = seq + 1;
      return inputs[index];
   }

   @Benchmark
   public int artemisHashCode() {
      return ByteUtil.hashCode(nextInput());
   }

   @Benchmark
   public int arraysHashCode() {
      return Arrays.hashCode(nextInput());
   }

}

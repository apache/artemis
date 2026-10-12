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

import org.apache.activemq.artemis.api.core.management.AddressControl;
import org.apache.activemq.artemis.core.management.impl.view.AddressField;
import org.apache.activemq.artemis.core.management.impl.view.AddressView;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.Mode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@Fork(value = 1)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 5, time = 1)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class AbstractViewPerfTest {

   @Param({"1000", "10000"})
   private int addressCount;

   @Param({"50"})
   private int pageSize;

   private AddressView view;
   private int middlePage;

   @Setup(Level.Trial)
   public void init() {
      view = new AddressView(null);

      // 1. Configure the view options using the internal JSON parsing mechanism
      // This switches the target field to MESSAGE_COUNT and sets the sorting loop active
      String optionsJson = "{"
         + "\"field\":\"\","
         + "\"operation\":\"\","
         + "\"value\":\"\","
         + "\"sortField\":\"" + AddressField.MESSAGE_COUNT.getName() + "\","
         + "\"sortOrder\":\"desc\""
         + "}";
      view.setOptions(optionsJson);

      // 2. Generate data rows
      List<AddressControl> addresses = new ArrayList<>(addressCount);
      for (int i = 0; i < addressCount; i++) {
         addresses.add(stubAddress(i));
      }

      // 3. Shuffle dataset to break TimSort's best-case O(N) shortcut paths
      Collections.shuffle(addresses);

      view.setCollection(addresses);
      middlePage = (addressCount / 2) / pageSize + 1;
   }

   @Benchmark
   public List<AddressControl> testFirstPage() {
      return view.getPagedResult(1, pageSize);
   }

   @Benchmark
   public List<AddressControl> testMiddlePage() {
      return view.getPagedResult(middlePage, pageSize);
   }

   @Benchmark
   public List<AddressControl> testAllResults() {
      return view.getPagedResult(-1, -1);
   }

   private static AddressControl stubAddress(long id) {
      final long simulatedMessageCount = (id * 31) % 10000;
      final String addressName = "address-" + id;

      return new AddressControl() {
         @Override public long getId() {
            return id;
         }
         @Override public String getAddress() {
            return addressName;
         }

         @Override
         public long getMessageCount() {
            // Mimic production cost: CPU loop simulating reading multiple live
            // internal queue components and consolidating dynamic tracking fields.
            long infrastructureTraversalCost = 0;
            for (int i = 0; i < 4; i++) {
               infrastructureTraversalCost += Thread.currentThread().hashCode() + simulatedMessageCount;
            }
            return simulatedMessageCount + (infrastructureTraversalCost & 0);
         }

         @Override public String[] getRoutingTypes() {
            return new String[0];
         }
         @Override public String getRoutingTypesAsJSON() {
            return "[]";
         }
         @Override public Object[] getRoles() {
            return new Object[0];
         }
         @Override public String getRolesAsJSON() {
            return "[]";
         }
         @Override public long getAddressSize() {
            return 0L;
         }
         @Override public int getMaxPageReadBytes() {
            return 0;
         }
         @Override public int getMaxPageReadMessages() {
            return 0;
         }
         @Override public int getPrefetchPageBytes() {
            return 0;
         }
         @Override public int getPrefetchPageMessages() {
            return 0;
         }
         @Override public void schedulePageCleanup() {
         }
         @Override public long getNumberOfMessages() {
            return 0L;
         }
         @Override public String[] getRemoteQueueNames() {
            return new String[0];
         }
         @Override public String[] getQueueNames() {
            return new String[0];
         }
         @Override public String[] getAllQueueNames() {
            return new String[0];
         }
         @Override public long getNumberOfPages() {
            return 0L;
         }
         @Override public boolean isPaging() {
            return false;
         }
         @Override public int getAddressLimitPercent() {
            return 0;
         }
         @Override public boolean block() {
            return false;
         }
         @Override public void unblock() {
         }
         @Override public boolean isBlockedViaManagement() {
            return false;
         }
         @Override public long getNumberOfBytesPerPage() {
            return 0L;
         }
         @Override public String[] getBindingNames() {
            return new String[0];
         }
         @Override public long getQueueCount() {
            return 0L;
         }
         @Override public long getRoutedMessageCount() {
            return 0L;
         }
         @Override public long getUnRoutedMessageCount() {
            return 0L;
         }
         @Override public void pause() {
         }
         @Override public void pause(boolean persist) {
         }
         @Override public void resume() {
         }
         @Override public boolean isPaused() {
            return false;
         }
         @Override public boolean isRetroactiveResource() {
            return false;
         }
         @Override public long getCurrentDuplicateIdCacheSize() {
            return 0L;
         }
         @Override public boolean clearDuplicateIdCache() {
            return false;
         }
         @Override public boolean isAutoCreated() {
            return false;
         }
         @Override public boolean isInternal() {
            return false;
         }
         @Override public boolean isTemporary() {
            return false;
         }
         @Override public long purge() {
            return 0L;
         }
         @Override public void replay(String target, String filter) {
         }
         @Override public void replay(String startScan, String endScan, String target, String filter) {
         }
         @Override public String sendMessage(Map<String, String> headers, int type, String body, boolean durable, String user, String password) {
            return null;
         }
         @Override public String sendMessage(Map<String, String> headers, int type, String body, boolean durable, String user, String password, boolean createMessageId) {
            return null;
         }
      };
   }
}
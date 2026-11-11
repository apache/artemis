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

package org.apache.activemq.artemis.utils.runnables;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class RunnableList {

   private final Set<AtomicRunnable> list = new HashSet<>();

   public RunnableList() {
   }

   public synchronized void add(AtomicRunnable runnable) {
      runnable.setAcceptedList(this);
      list.add(runnable);
   }

   public int size() {
      return list.size();
   }

   public synchronized void remove(AtomicRunnable runnable) {
      list.remove(runnable);
   }

   public synchronized void cancel() {
      list.forEach(this::cancel);
      list.clear();
   }

   private void cancel(AtomicRunnable atomicRunnable) {
      atomicRunnable.cancel();
   }

   public void forEach(Consumer<AtomicRunnable> consumerRunnable) {
      list.forEach(consumerRunnable);
   }

}

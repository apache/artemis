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
package org.apache.activemq.artemis.core.server.routing.targets;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class MockTargetProbe extends TargetProbe {
   private final Map<Target, Integer> targetExecutions = new ConcurrentHashMap<>();

   private volatile boolean checked;

   public boolean isChecked() {
      return checked;
   }

   public void setChecked(boolean checked) {
      this.checked = checked;
   }

   public MockTargetProbe(String name, boolean checked) {
      super(name);

      this.checked = checked;
   }

   public int getTargetExecutions(Target target) {
      return Objects.requireNonNullElse(targetExecutions.get(target), 0);
   }

   public int setTargetExecutions(Target target, int executions) {
      return targetExecutions.put(target, executions);
   }

   public void clearTargetExecutions() {
      targetExecutions.clear();
   }

   @Override
   public boolean check(Target target) {
      targetExecutions.compute(target, (t, e) -> e == null ? 1 : e++);

      return checked;
   }
}

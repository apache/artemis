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
package org.apache.activemq.artemis.tests.smoke.lockmanager.kubetimeskew;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.activemq.artemis.lockmanager.DistributedLock;
import org.apache.artemis.lock.kube.KubeLockManager;

public class KubeLockManagerTimeSkew extends KubeLockManager {

   private static final String TIME_ADJUSTMENT = "time-adjustment";
   private static final Set<String> ADDITIONAL_VALID_PARAMS = Stream.of(TIME_ADJUSTMENT).collect(Collectors.toSet());

   private final int timeAdjustment;

   public KubeLockManagerTimeSkew(Map<String, String> config) {
      super(config);
      this.timeAdjustment = config.get(TIME_ADJUSTMENT) != null ? Integer.parseInt(config.get(TIME_ADJUSTMENT)) : 0;
   }

   public KubeLockManagerTimeSkew(String hostname, String namespace, int leaseTimeout, int timeAdjustment) {
      super(hostname, namespace, leaseTimeout);
      this.timeAdjustment = timeAdjustment;
   }

   @Override
   protected Set<String> getValidParams() {
      Set<String> params = super.getValidParams();
      return Stream.concat(params.stream(), ADDITIONAL_VALID_PARAMS.stream()).collect(Collectors.toSet());
   }

   @Override
   public DistributedLock getDistributedLock(String lockId) throws InterruptedException, ExecutionException, TimeoutException {
      return new KubeLockTimeSkew(hostname, namespace, lockId, leaseTimeout, timeAdjustment);
   }
}

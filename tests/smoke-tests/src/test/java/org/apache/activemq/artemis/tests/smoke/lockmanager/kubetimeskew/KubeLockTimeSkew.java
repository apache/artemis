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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.apache.artemis.lock.kube.KubeLock;

public class KubeLockTimeSkew extends KubeLock {

   private final int timeAdjustment;

   public KubeLockTimeSkew(String hostname, String namespace, String id, int leasePeriodSeconds, int timeAdjustment) {
      super(hostname, namespace, id, leasePeriodSeconds);
      this.timeAdjustment = timeAdjustment;
   }

   @Override
   protected OffsetDateTime currentTime() {
      return OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(timeAdjustment);
   }
}

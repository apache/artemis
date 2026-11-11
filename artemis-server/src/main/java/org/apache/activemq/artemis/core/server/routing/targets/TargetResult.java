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

public class TargetResult {

   public static final TargetResult REFUSED_UNAVAILABLE_RESULT = new TargetResult(Status.REFUSED_UNAVAILABLE);
   public static final TargetResult REFUSED_USE_ANOTHER_RESULT = new TargetResult(Status.REFUSED_USE_ANOTHER);

   private final Status status;
   private final Target target;

   public Status getStatus() {
      return status;
   }

   public Target getTarget() {
      return target;
   }

   public TargetResult(Target target) {
      this.status = Status.OK;
      this.target = target;
   }

   private TargetResult(Status s) {
      this.status = s;
      this.target = null;
   }

   public enum Status {
      OK,
      REFUSED_UNAVAILABLE, // pool is not yet ready, possibly transient
      REFUSED_USE_ANOTHER  // rejected, go else where, non-transient
   }
}

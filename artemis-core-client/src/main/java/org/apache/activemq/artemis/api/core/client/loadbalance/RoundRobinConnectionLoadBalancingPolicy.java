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
package org.apache.activemq.artemis.api.core.client.loadbalance;

import java.io.Serializable;

import org.apache.activemq.artemis.utils.RandomUtil;

/**
 * RoundRobinConnectionLoadBalancingPolicy corresponds to a round-robin load-balancing policy.
 * <p>
 * The first call to {@link #select(int)} will return a random integer between {@code 0} (inclusive) and {@code max}
 * (exclusive). Subsequent calls will then return an integer in a round-robin fashion.
 */
public final class RoundRobinConnectionLoadBalancingPolicy implements ConnectionLoadBalancingPolicy, Serializable {

   private static final long serialVersionUID = 7511196010141439559L;

   private int pos = -1;

   @Override
   public int select(final int max) {
      if (pos == -1) {
         // We start on a random one
         pos = RandomUtil.randomInterval(0, max);

      } else {
         pos++;

         if (pos >= max) {
            pos = 0;
         }
      }

      return pos;
   }
}

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
package org.apache.activemq.artemis.core.server.impl;

import org.apache.activemq.artemis.utils.collections.NoOpMap;

/**
 * Implementation of MessageGroups that simply uses a NoOpMap, and in essence disables message grouping for queues that
 * use it.
 *
 * @param <C> the value type.
 */
public class DisabledMessageGroups<C> extends MapMessageGroups<C> {

   private static final DisabledMessageGroups INSTANCE = new DisabledMessageGroups();

   @SuppressWarnings("unchecked")
   public static <C> DisabledMessageGroups<C> instance() {
      return (DisabledMessageGroups<C>) INSTANCE;
   }

   private DisabledMessageGroups() {
      super(NoOpMap.instance());
   }
}

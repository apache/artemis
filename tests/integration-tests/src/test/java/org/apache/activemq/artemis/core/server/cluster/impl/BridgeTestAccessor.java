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
package org.apache.activemq.artemis.core.server.cluster.impl;

import java.util.Map;
import java.util.function.Function;

import org.apache.activemq.artemis.core.server.MessageReference;

/**
 * Provide accessors for Bridge during testing. Do not use this outside of the context of UnitTesting.
 */
public class BridgeTestAccessor {

   public static Map<Long, MessageReference> getRefs(BridgeImpl bridge) {
      return bridge.refs;
   }

   public static boolean withinRefs(BridgeImpl bridge, Function<Map<Long, MessageReference>, Boolean> function) {
      Map<Long, MessageReference> refs = getRefs(bridge);
      return function.apply(refs);
   }

}

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
package org.apache.activemq.artemis.core.server.cluster.quorum;

import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;

public abstract class Vote<T> {

   public Map<String, Object> getVoteMap() {
      Map<String, Object> map = new HashMap<>();
      return map;
   }

   public abstract void encode(ActiveMQBuffer buff);

   public abstract void decode(ActiveMQBuffer buff);

   /**
    * Whether we should ask the target server for an answer or decide ourselves, for instance if we couldn't connect to
    * the node in the first place
    */
   public abstract boolean isRequestServerVote();

   public abstract T getVote();
}

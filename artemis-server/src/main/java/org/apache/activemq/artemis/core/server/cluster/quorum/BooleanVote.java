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

import java.util.Map;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;

/**
 * a simple yes.no vote
 */
public class BooleanVote extends Vote<Boolean> {

   protected boolean vote;

   public BooleanVote(boolean vote) {
      this.vote = vote;
   }

   @Override
   public boolean isRequestServerVote() {
      return false;
   }

   @Override
   public Boolean getVote() {
      return vote;
   }

   @Override
   public Map<String, Object> getVoteMap() {
      return null;
   }

   @Override
   public void encode(ActiveMQBuffer buff) {
      buff.writeBoolean(vote);
   }

   @Override
   public void decode(ActiveMQBuffer buff) {
      vote = buff.readBoolean();
   }

   @Override
   public String toString() {
      return "BooleanVote [vote=" + vote + "]";
   }
}

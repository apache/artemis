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
package org.apache.activemq.artemis.core.protocol.core.impl.wireformat;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.server.cluster.quorum.QuorumVoteHandler;
import org.apache.activemq.artemis.core.server.cluster.quorum.Vote;

public class QuorumVoteReplyMessage extends PacketImpl {

   private SimpleString handler;
   private Vote vote;
   private ActiveMQBuffer voteBuffer;

   public QuorumVoteReplyMessage(SimpleString handler, Vote vote) {
      super(QUORUM_VOTE_REPLY);
      this.handler = handler;
      this.vote = vote;
   }

   public QuorumVoteReplyMessage() {
      super(QUORUM_VOTE_REPLY);
   }

   public Vote getVote() {
      return vote;
   }

   public SimpleString getHandler() {
      return handler;
   }

   @Override
   public boolean isResponse() {
      return true;
   }

   @Override
   public void encodeRest(ActiveMQBuffer buffer) {
      super.encodeRest(buffer);
      buffer.writeSimpleString(handler);
      vote.encode(buffer);
   }

   @Override
   public void decodeRest(ActiveMQBuffer buffer) {
      super.decodeRest(buffer);
      handler = buffer.readSimpleString();
      voteBuffer = ActiveMQBuffers.fixedBuffer(buffer.readableBytes());
      buffer.readBytes(voteBuffer);
   }

   public void decodeRest(QuorumVoteHandler voteHandler) {
      vote = voteHandler.decode(voteBuffer);
   }
}

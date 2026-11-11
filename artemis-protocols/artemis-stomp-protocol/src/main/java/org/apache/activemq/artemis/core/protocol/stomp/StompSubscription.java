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
package org.apache.activemq.artemis.core.protocol.stomp;

import org.apache.activemq.artemis.api.core.SimpleString;

public class StompSubscription {

   private final String subID;

   private final String ack;

   private final SimpleString queueName;

   // whether or not this subscription follows multicast semantics (e.g. for a JMS topic)
   private final boolean multicast;

   private final boolean noLocal;

   private final int consumerWindowSize;

   public StompSubscription(String subID, String ack, SimpleString queueName, boolean multicast, boolean noLocal, int consumerWindowSize) {
      this.subID = subID;
      this.ack = ack;
      this.queueName = queueName;
      this.multicast = multicast;
      this.noLocal = noLocal;
      this.consumerWindowSize = consumerWindowSize;
   }

   public String getAck() {
      return ack;
   }

   public String getID() {
      return subID;
   }

   public SimpleString getQueueName() {
      return queueName;
   }

   public boolean isMulticast() {
      return multicast;
   }

   public boolean isNoLocal() {
      return noLocal;
   }

   public int getConsumerWindowSize() {
      return consumerWindowSize;
   }

   @Override
   public String toString() {
      return "StompSubscription[id=" + subID + ", ack=" + ack + ", queueName=" + queueName + ", multicast=" + multicast + ", noLocal=" + noLocal + ", consumerWindowSize=" + consumerWindowSize + "]";
   }
}

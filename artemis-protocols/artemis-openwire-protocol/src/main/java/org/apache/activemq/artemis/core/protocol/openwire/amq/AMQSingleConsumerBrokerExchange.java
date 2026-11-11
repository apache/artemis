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
package org.apache.activemq.artemis.core.protocol.openwire.amq;

import org.apache.activemq.command.MessageAck;
import org.apache.activemq.command.MessagePull;

public class AMQSingleConsumerBrokerExchange extends AMQConsumerBrokerExchange {

   private AMQConsumer consumer;

   public AMQSingleConsumerBrokerExchange(AMQSession amqSession, AMQConsumer consumer) {
      super(amqSession);
      this.consumer = consumer;
   }

   @Override
   public void processMessagePull(MessagePull messagePull) throws Exception {
      consumer.processMessagePull(messagePull);
   }

   @Override
   public void removeConsumer() throws Exception {
      consumer.removeConsumer();
   }

   @Override
   public void acknowledge(MessageAck ack) throws Exception {
      consumer.acknowledge(ack);
   }

   @Override
   public void updateConsumerPrefetchSize(int prefetch) {
      consumer.setPrefetchSize(prefetch);
   }

}

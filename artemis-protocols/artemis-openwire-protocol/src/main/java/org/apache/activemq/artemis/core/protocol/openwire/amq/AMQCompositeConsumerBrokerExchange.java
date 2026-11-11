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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.MessageAck;
import org.apache.activemq.command.MessagePull;

public class AMQCompositeConsumerBrokerExchange extends AMQConsumerBrokerExchange {

   private final Map<ActiveMQDestination, AMQConsumer> consumerMap;

   public AMQCompositeConsumerBrokerExchange(AMQSession amqSession, List<AMQConsumer> consumerList) {
      super(amqSession);
      this.consumerMap = new HashMap<>();
      for (AMQConsumer consumer : consumerList) {
         consumerMap.put(consumer.getOpenwireDestination(), consumer);
      }
   }

   @Override
   public void processMessagePull(MessagePull messagePull) throws Exception {
      AMQConsumer amqConsumer = consumerMap.get(messagePull.getDestination());
      if (amqConsumer != null) {
         amqConsumer.processMessagePull(messagePull);
      }
   }

   @Override
   public void acknowledge(MessageAck ack) throws Exception {
      AMQConsumer amqConsumer = consumerMap.get(ack.getDestination());
      if (amqConsumer != null) {
         amqConsumer.acknowledge(ack);
      }
   }

   @Override
   public void removeConsumer() throws Exception {
      for (AMQConsumer amqConsumer : consumerMap.values()) {
         amqConsumer.removeConsumer();
      }
   }

   @Override
   public void updateConsumerPrefetchSize(int prefetch) {
      for (AMQConsumer amqConsumer : consumerMap.values()) {
         amqConsumer.setPrefetchSize(prefetch);
      }
   }
}

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

package org.apache.activemq.artemis.protocol.amqp.converter.message;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPStandardMessage;
import org.apache.activemq.artemis.protocol.amqp.converter.CoreAmqpConverter;
import org.junit.jupiter.api.Test;

public class DirectConvertTest {

   @Test
   public void testConvertScheduledAMQPCore() {
      long deliveryTime = System.currentTimeMillis() + 10_000;
      AMQPStandardMessage standardMessage = AMQPStandardMessage.createMessage(1, 0,
                                                                              null, null, null,
                                                                              null, null, null, null, null);
      standardMessage.setScheduledDeliveryTime(deliveryTime);

      ICoreMessage coreMessage = standardMessage.toCore();

      assertEquals((Long)deliveryTime, coreMessage.getScheduledDeliveryTime());
   }


   @Test
   public void testConvertTTLdAMQPCore() {
      long time = System.currentTimeMillis() + 10_000;
      AMQPStandardMessage standardMessage = AMQPStandardMessage.createMessage(1, 0,
                                                                              null, null, null,
                                                                              null, null, null, null, null);
      standardMessage.setExpiration(time);

      ICoreMessage coreMessage = standardMessage.toCore();

      assertEquals(time, coreMessage.getExpiration());
   }

   @Test
   public void testConvertScheduledCoreAMQP() throws Exception {
      long deliveryTime = System.currentTimeMillis() + 10_000;
      CoreMessage coreMessage = new CoreMessage();
      coreMessage.setScheduledDeliveryTime(deliveryTime);
      coreMessage.initBuffer(1024);

      AMQPMessage amqpMessage = CoreAmqpConverter.fromCore(coreMessage, new NullStorageManager());
      assertEquals((Long)deliveryTime, amqpMessage.getScheduledDeliveryTime());
   }

   @Test
   public void testConvertTTLCoreAMQP() throws Exception {
      long time = System.currentTimeMillis() + 10_000;
      CoreMessage coreMessage = new CoreMessage();
      coreMessage.setExpiration(time);
      coreMessage.initBuffer(1024);

      AMQPMessage amqpMessage = CoreAmqpConverter.fromCore(coreMessage, new NullStorageManager());
      assertEquals(time, amqpMessage.getExpiration());
   }
}

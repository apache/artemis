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
package org.apache.activemq.artemis.protocol.amqp.converter;

import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.core.persistence.CoreMessageObjectPools;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPMessage;
import org.apache.activemq.artemis.spi.core.protocol.MessageConverter;


public class AMQPConverter implements MessageConverter<AMQPMessage> {

   private static final AMQPConverter theInstance = new AMQPConverter();

   private AMQPConverter() {
   }

   public static AMQPConverter getInstance() {
      return theInstance;
   }

   @Override
   public AMQPMessage fromCore(ICoreMessage coreMessage, StorageManager storageManager) throws Exception {
      return CoreAmqpConverter.fromCore(coreMessage, storageManager);
   }

   @Override
   public ICoreMessage toCore(AMQPMessage messageSource, CoreMessageObjectPools coreMessageObjectPools) throws Exception {
      return AmqpCoreConverter.toCore(messageSource, coreMessageObjectPools);
   }
}

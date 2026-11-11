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
package org.apache.activemq.artemis.jms.client;

import java.util.Map;

import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.core.client.impl.ClientMessageImpl;
import org.apache.activemq.artemis.utils.UUID;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test conversion from Core message to JMS message.
 */
public class ConversionTest {

   @Test
   public void testCoreToJMSConversion() {
      UUID uuid = UUIDGenerator.getInstance().generateUUID();
      ICoreMessage clientMessage = new ClientMessageImpl();
      clientMessage.setDurable(true)
              .setPriority((byte) 9)
              .setExpiration(123456)
              .setUserID(uuid);
      Map<String, Object> messageMap = clientMessage.toMap();
      Map<String, Object> jmsMap = ActiveMQMessage.coreMaptoJMSMap(messageMap);

      Object priority = jmsMap.get("JMSPriority");
      assertInstanceOf(Integer.class, priority);
      assertEquals(9, priority);
      assertNotNull(jmsMap.get("JMSMessageID"));
      assertEquals("ID:" + uuid, jmsMap.get("JMSMessageID"));
   }
}

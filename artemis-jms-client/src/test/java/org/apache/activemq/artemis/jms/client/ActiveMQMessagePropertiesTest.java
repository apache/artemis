/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.jms.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Enumeration;
import javax.jms.JMSException;
import org.apache.activemq.artemis.core.client.impl.ClientMessageImpl;
import org.junit.jupiter.api.Test;

public class ActiveMQMessagePropertiesTest {

   @Test
   public void testJMSXGroupID() throws JMSException {
      ActiveMQMessage activeMQMessage = newBlankActiveMQMessage();

      assertFalse(contains(activeMQMessage.getPropertyNames(), "JMSXGroupID"));

      activeMQMessage.setStringProperty("JMSXGroupID", "Bob");
      assertEquals("Bob", activeMQMessage.getStringProperty("JMSXGroupID"));

      assertTrue(contains(activeMQMessage.getPropertyNames(), "JMSXGroupID"));
   }

   @Test
   public void testJMSXUserID() throws JMSException {
      ActiveMQMessage activeMQMessage = newBlankActiveMQMessage();

      assertFalse(contains(activeMQMessage.getPropertyNames(), "JMSXUserID"));

      activeMQMessage.setStringProperty("JMSXUserID", "Bob");
      assertEquals("Bob", activeMQMessage.getStringProperty("JMSXUserID"));

      assertTrue(contains(activeMQMessage.getPropertyNames(), "JMSXUserID"));
   }

   private boolean contains(Enumeration<String> enumeration, String key) {
      while (enumeration.hasMoreElements()) {
         if (enumeration.nextElement().equals(key)) {
            return true;
         }
      }
      return false;
   }

   private ActiveMQMessage newBlankActiveMQMessage() throws JMSException {
      ActiveMQMessage activeMQMessage = new ActiveMQMessage(new ClientMessageImpl(), null);
      activeMQMessage.clearBody();
      activeMQMessage.clearProperties();
      return activeMQMessage;
   }
}

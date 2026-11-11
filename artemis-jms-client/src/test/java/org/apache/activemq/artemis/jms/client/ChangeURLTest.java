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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.JMSException;

import org.junit.jupiter.api.Test;

public class ChangeURLTest {

   @Test
   public void testChangeURL() throws Exception {
      ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("tcp://localhost:61616?user=nono");
      assertEquals("nono", factory.getUser());

      factory.setBrokerURL("tcp://localhost:61616?user=changed");
      assertEquals("changed", factory.getUser());

      boolean failed = false;
      try {
         factory.createConnection();
      } catch (Throwable expected) {
         // there is no broker running, this is expected and somewhat required to fail, however readOnly should be set to true here
         // so the next assertion is the important one.
         // The goal here is to make connectionFactory.readOnly=true
         failed = true;
      }

      assertTrue(failed, "failure expected");

      failed = false;

      try {
         factory.setBrokerURL("tcp://localhost:61618?user=changed");
      } catch (JMSException ex) {
         failed = true;
      }

      assertTrue(failed, "failure expected");
   }

}

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
package org.apache.activemq.artemis.junit;

import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class ActiveMQDynamicProducerResourceWithoutAddressExceptionTest {

   static final SimpleString TEST_QUEUE_ONE = SimpleString.of("test.queue.one");
   static final String TEST_BODY = "Test Message";
   static final Map<String, Object> TEST_PROPERTIES;

   static {
      TEST_PROPERTIES = new HashMap<String, Object>(2);
      TEST_PROPERTIES.put("PropertyOne", "Property Value 1");
      TEST_PROPERTIES.put("PropertyTwo", "Property Value 2");
   }

   EmbeddedActiveMQResource server = new EmbeddedActiveMQResource();

   ActiveMQDynamicProducerResource producer = new ActiveMQDynamicProducerResource(server.getVmURL());

   @After
   public void tear() {
      server.stop();
   }

   @Rule
   public RuleChain ruleChain = RuleChain.outerRule(server).around(producer);

   @Before
   public void setUp() throws Exception {
      producer.setAutoCreateQueue(false);
      server.createQueue(TEST_QUEUE_ONE, TEST_QUEUE_ONE);
   }

   @Test(expected = IllegalArgumentException.class)
   public void testSendBytesToDefaultAddress() throws Exception {
      producer.sendMessage(TEST_BODY.getBytes());
   }

   @Test(expected = IllegalArgumentException.class)
   public void testSendStringToDefaultAddress() throws Exception {
      producer.sendMessage(TEST_BODY);
   }

   @Test(expected = IllegalArgumentException.class)
   public void testSendBytesAndPropertiesToDefaultAddress() throws Exception {
      producer.sendMessage(TEST_BODY.getBytes(), TEST_PROPERTIES);
   }

   @Test(expected = IllegalArgumentException.class)
   public void testSendStringAndPropertiesToDefaultAddress() throws Exception {
      producer.sendMessage(TEST_BODY, TEST_PROPERTIES);
   }
}

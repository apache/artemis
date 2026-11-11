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

import javax.jms.Message;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static org.junit.Assert.assertNotNull;

public class EmbeddedJMSResourceQueueTest {

   static final String TEST_DESTINATION_NAME = "queue://test.queue";
   static final String TEST_BODY = "Test Message";
   static final Map<String, Object> TEST_MAP_BODY;
   static final Map<String, Object> TEST_PROPERTIES;

   static final String ASSERT_PUSHED_FORMAT = "Message should have been pushed a message to %s";
   static final String ASSERT_COUNT_FORMAT = "Unexpected message count in destination %s";

   static {
      TEST_MAP_BODY = new HashMap<>(2);
      TEST_MAP_BODY.put("Element 1", "Value 1");
      TEST_MAP_BODY.put("Element 2", "Value 2");

      TEST_PROPERTIES = new HashMap<String, Object>(2);
      TEST_PROPERTIES.put("PropertyOne", "Property Value 1");
      TEST_PROPERTIES.put("PropertyTwo", "Property Value 2");
   }

   public EmbeddedJMSResource jmsServer = new EmbeddedJMSResource();

   @Rule
   public RuleChain rulechain = RuleChain.outerRule(jmsServer);

   Message pushed = null;

   @After
   public void tearDown() throws Exception {
      assertNotNull(String.format(ASSERT_PUSHED_FORMAT, TEST_DESTINATION_NAME), pushed);
   }

   @Test
   public void testPushBytesMessage() throws Exception {
      pushed = jmsServer.pushMessage(TEST_DESTINATION_NAME, TEST_BODY.getBytes());
   }

   @Test
   public void testPushTextMessage() throws Exception {
      pushed = jmsServer.pushMessage(TEST_DESTINATION_NAME, TEST_BODY);
   }

   @Test
   public void testPushMapMessage() throws Exception {
      pushed = jmsServer.pushMessage(TEST_DESTINATION_NAME, TEST_MAP_BODY);
   }

   @Test
   public void testPushObjectMessage() throws Exception {
      pushed = jmsServer.pushMessage(TEST_DESTINATION_NAME, (Serializable) TEST_BODY);
   }

   @Test
   public void testPushBytesMessageWithProperties() throws Exception {
      pushed = jmsServer.pushMessageWithProperties(TEST_DESTINATION_NAME, TEST_BODY.getBytes(), TEST_PROPERTIES);
   }

   @Test
   public void testPushTextMessageWithProperties() throws Exception {
      pushed = jmsServer.pushMessageWithProperties(TEST_DESTINATION_NAME, TEST_BODY, TEST_PROPERTIES);
   }

   @Test
   public void testPushMapMessageWithProperties() throws Exception {
      pushed = jmsServer.pushMessageWithProperties(TEST_DESTINATION_NAME, TEST_MAP_BODY, TEST_PROPERTIES);
   }

   @Test
   public void testPushObjectMessageWithProperties() throws Exception {
      pushed = jmsServer.pushMessageWithProperties(TEST_DESTINATION_NAME, (Serializable) TEST_BODY, TEST_PROPERTIES);
   }

}

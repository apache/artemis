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

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static org.junit.Assert.assertNotNull;

public class MultipleEmbeddedActiveMQResourcesTest {

   static final SimpleString TEST_QUEUE_ONE = SimpleString.of("test.queue.one");
   static final SimpleString TEST_QUEUE_TWO = SimpleString.of("test.queue.two");
   static final SimpleString TEST_ADDRESS_ONE = SimpleString.of("test.address.one");
   static final SimpleString TEST_ADDRESS_TWO = SimpleString.of("test.address.two");

   static final String TEST_BODY = "Test Message";

   static final String ASSERT_SENT_FORMAT = "Message should have been sent to %s";
   static final String ASSERT_RECEIVED_FORMAT = "Message should have been received from %s";
   static final String ASSERT_COUNT_FORMAT = "Unexpected message count in queue %s";

   public EmbeddedActiveMQResource serverOne = new EmbeddedActiveMQResource(0);

   public EmbeddedActiveMQResource serverTwo = new EmbeddedActiveMQResource(1);

   @Rule
   public RuleChain rulechain = RuleChain.outerRule(serverOne).around(serverTwo);

   @Before
   public void setUp() throws Exception {
      serverOne.getServer().getActiveMQServer().getAddressSettingsRepository().addMatch("#", new AddressSettings().setDeadLetterAddress(SimpleString.of("DLA")).setExpiryAddress(SimpleString.of("Expiry")));
      serverTwo.getServer().getActiveMQServer().getAddressSettingsRepository().addMatch("#", new AddressSettings().setDeadLetterAddress(SimpleString.of("DLA")).setExpiryAddress(SimpleString.of("Expiry")));

      serverOne.createQueue(TEST_ADDRESS_ONE, TEST_QUEUE_ONE);
      serverTwo.createQueue(TEST_ADDRESS_TWO, TEST_QUEUE_TWO);
   }

   @Test
   public void testMultipleServers() throws Exception {
      ClientMessage sentOne = serverOne.sendMessage(TEST_ADDRESS_ONE, TEST_BODY);
      assertNotNull(String.format(ASSERT_SENT_FORMAT, TEST_QUEUE_ONE), sentOne);

      ClientMessage receivedOne = serverOne.receiveMessage(TEST_QUEUE_ONE);
      assertNotNull(String.format(ASSERT_RECEIVED_FORMAT, TEST_QUEUE_TWO), receivedOne);

      ClientMessage sentTwo = serverTwo.sendMessage(TEST_ADDRESS_TWO, TEST_BODY);
      assertNotNull(String.format(ASSERT_SENT_FORMAT, TEST_QUEUE_TWO), sentOne);

      ClientMessage receivedTwo = serverTwo.receiveMessage(TEST_QUEUE_TWO);
      assertNotNull(String.format(ASSERT_RECEIVED_FORMAT, TEST_QUEUE_TWO), receivedOne);
   }

}

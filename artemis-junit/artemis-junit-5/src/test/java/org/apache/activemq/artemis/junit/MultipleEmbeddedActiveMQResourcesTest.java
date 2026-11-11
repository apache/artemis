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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
public class MultipleEmbeddedActiveMQResourcesTest {

   static final SimpleString TEST_QUEUE_ONE = SimpleString.of("test.queue.one");
   static final SimpleString TEST_QUEUE_TWO = SimpleString.of("test.queue.two");
   static final SimpleString TEST_ADDRESS_ONE = SimpleString.of("test.address.one");
   static final SimpleString TEST_ADDRESS_TWO = SimpleString.of("test.address.two");

   static final String TEST_BODY = "Test Message";

   static final String ASSERT_SENT_FORMAT = "Message should have been sent to %s";
   static final String ASSERT_RECEIVED_FORMAT = "Message should have been received from %s";

   @RegisterExtension
   @Order(1)
   public static EmbeddedActiveMQExtension serverOne = new EmbeddedActiveMQExtension(1);

   @RegisterExtension
   @Order(2)
   public static EmbeddedActiveMQExtension serverTwo = new EmbeddedActiveMQExtension(2);

   @BeforeAll
   public void setUp() throws Exception {
      serverOne.getServer().getActiveMQServer().getAddressSettingsRepository().addMatch("#", new AddressSettings().setDeadLetterAddress(SimpleString.of("DLA")).setExpiryAddress(SimpleString.of("Expiry")));
      serverTwo.getServer().getActiveMQServer().getAddressSettingsRepository().addMatch("#", new AddressSettings().setDeadLetterAddress(SimpleString.of("DLA")).setExpiryAddress(SimpleString.of("Expiry")));

      serverOne.createQueue(TEST_ADDRESS_ONE, TEST_QUEUE_ONE);
      serverTwo.createQueue(TEST_ADDRESS_TWO, TEST_QUEUE_TWO);
   }

   @Test
   public void testMultipleServers() throws Exception {
      ClientMessage sentOne = serverOne.sendMessage(TEST_ADDRESS_ONE, TEST_BODY);
      assertNotNull(sentOne, String.format(ASSERT_SENT_FORMAT, TEST_QUEUE_ONE));

      ClientMessage receivedOne = serverOne.receiveMessage(TEST_QUEUE_ONE);
      assertNotNull(receivedOne, String.format(ASSERT_RECEIVED_FORMAT, TEST_QUEUE_TWO));

      ClientMessage sentTwo = serverTwo.sendMessage(TEST_ADDRESS_TWO, TEST_BODY);
      assertNotNull(sentOne, String.format(ASSERT_SENT_FORMAT, TEST_QUEUE_TWO));

      ClientMessage receivedTwo = serverTwo.receiveMessage(TEST_QUEUE_TWO);
      assertNotNull(receivedOne, String.format(ASSERT_RECEIVED_FORMAT, TEST_QUEUE_TWO));
   }

}

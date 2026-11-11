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

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import static org.junit.Assert.assertNotNull;

public class MultipleEmbeddedJMSResourcesTest {

   static final String TEST_QUEUE_ONE = "queue://test.queue.one";
   static final String TEST_QUEUE_TWO = "queue://test.queue.two";
   static final String TEST_BODY = "Test Message";

   static final String ASSERT_PUSHED_FORMAT = "Message should have been pushed a message to %s";
   static final String ASSERT_COUNT_FORMAT = "Unexpected message count in destination %s";

   public EmbeddedJMSResource jmsServerOne = new EmbeddedJMSResource(0);

   public EmbeddedJMSResource jmsServerTwo = new EmbeddedJMSResource(1);

   @Rule
   public RuleChain rulechain = RuleChain.outerRule(jmsServerOne).around(jmsServerTwo);

   @Test
   public void testMultipleServers() throws Exception {
      jmsServerOne.getJmsServer().getActiveMQServer().getAddressSettingsRepository().addMatch("#", new AddressSettings().setDeadLetterAddress(SimpleString.of("DLA")).setExpiryAddress(SimpleString.of("Expiry")));
      jmsServerTwo.getJmsServer().getActiveMQServer().getAddressSettingsRepository().addMatch("#", new AddressSettings().setDeadLetterAddress(SimpleString.of("DLA")).setExpiryAddress(SimpleString.of("Expiry")));

      Message pushedOne = jmsServerOne.pushMessage(TEST_QUEUE_ONE, TEST_BODY);
      assertNotNull(String.format(ASSERT_PUSHED_FORMAT, TEST_QUEUE_ONE), pushedOne);

      Message pushedTwo = jmsServerTwo.pushMessage(TEST_QUEUE_TWO, TEST_BODY);
      assertNotNull(String.format(ASSERT_PUSHED_FORMAT, TEST_QUEUE_TWO), pushedTwo);
   }

}
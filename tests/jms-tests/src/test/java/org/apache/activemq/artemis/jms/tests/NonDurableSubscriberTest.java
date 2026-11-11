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
package org.apache.activemq.artemis.jms.tests;

import javax.jms.InvalidSelectorException;
import javax.jms.Session;
import javax.jms.TopicConnection;
import javax.jms.TopicSession;

import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.Test;

/**
 * Non-durable subscriber tests.
 */
public class NonDurableSubscriberTest extends JMSTestCase {

   /**
    * Test introduced as a result of a TCK failure.
    */
   @Test
   public void testNonDurableSubscriberOnNullTopic() throws Exception {
      TopicConnection conn = createTopicConnection();

      TopicSession ts = conn.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);

      try {
         ts.createSubscriber(null);
         ProxyAssertSupport.fail("this should fail");
      } catch (javax.jms.InvalidDestinationException e) {
         // OK
      }
   }

   /**
    * Test introduced as a result of a TCK failure.
    */
   @Test
   public void testNonDurableSubscriberInvalidUnsubscribe() throws Exception {
      TopicConnection conn = createTopicConnection();
      conn.setClientID("clientIDxyz123");

      TopicSession ts = conn.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);

      try {
         ts.unsubscribe("invalid-subscription-name");
         ProxyAssertSupport.fail("this should fail");
      } catch (javax.jms.InvalidDestinationException e) {
         // OK
      }
   }

   @Test
   public void testInvalidSelectorOnSubscription() throws Exception {
      TopicConnection c = createTopicConnection();
      c.setClientID("something");

      TopicSession s = c.createTopicSession(false, Session.AUTO_ACKNOWLEDGE);

      try {
         s.createSubscriber(ActiveMQServerTestCase.topic1, "=TEST 'test'", false);
         ProxyAssertSupport.fail("this should fail");
      } catch (InvalidSelectorException e) {
         // OK
      }
   }

}

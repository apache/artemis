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
package org.apache.activemq.artemis.tests.integration.jms.client;

import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MessageProducerTest extends JMSTestBase {

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      conn = cf.createConnection();
   }

   @Test
   public void testNoDefaultDestination() throws JMSException {
      Session session = conn.createSession();
      try {
         MessageProducer producer = session.createProducer(null);
         Message m = session.createMessage();
         try {
            producer.send(m);
            fail("must not be reached");
         } catch (UnsupportedOperationException cause) {
            // expected
         }
      } finally {
         session.close();
      }
   }

   @Test
   public void testHasDefaultDestination() throws Exception {
      Session session = conn.createSession();
      try {
         Queue queue = createQueue(name);
         Queue queue2 = createQueue(name + "2");
         MessageProducer producer = session.createProducer(queue);
         Message m = session.createMessage();
         try {
            producer.send(queue2, m);
            fail("must not be reached");
         } catch (UnsupportedOperationException cause) {
            // expected
         }
         try {
            producer.send(queue, m);
            fail("tck7 requires an UnsupportedOperationException " + "even if the destination is the same as the default one");
         } catch (UnsupportedOperationException cause) {
            // expected
         }
      } finally {
         session.close();
      }
   }
}

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
package org.apache.activemq.artemis.jms.tests.message;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;

import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A test that sends/receives text messages to the JMS provider and verifies their integrity.
 */
public class TextMessageTest extends MessageTestBase {


   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      message = session.createTextMessage();
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      message = null;
      super.tearDown();
   }

   @Test
   public void testClearProperties() throws Exception {
      ((TextMessage) message).setText("something");
      queueProd.send(message);

      TextMessage rm = (TextMessage) queueCons.receive();

      rm.clearProperties();

      ProxyAssertSupport.assertEquals("something", rm.getText());
   }

   // Protected -----------------------------------------------------

   @Override
   protected void prepareMessage(final Message m) throws JMSException {
      super.prepareMessage(m);

      TextMessage tm = (TextMessage) m;
      tm.setText("this is the payload");
   }

   @Override
   protected void assertEquivalent(final Message m, final int mode, final boolean redelivery) throws JMSException {
      super.assertEquivalent(m, mode, redelivery);

      TextMessage tm = (TextMessage) m;
      ProxyAssertSupport.assertEquals("this is the payload", tm.getText());
   }
}

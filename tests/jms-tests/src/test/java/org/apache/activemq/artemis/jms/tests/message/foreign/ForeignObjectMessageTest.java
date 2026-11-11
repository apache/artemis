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
package org.apache.activemq.artemis.jms.tests.message.foreign;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.ObjectMessage;

import org.apache.activemq.artemis.jms.tests.message.SimpleJMSObjectMessage;
import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * Tests the delivery/receipt of a foreign object message
 */
public class ForeignObjectMessageTest extends ForeignMessageTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private ForeignTestObject testObj;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      testObj = new ForeignTestObject("hello", 2.2D);
      super.setUp();

   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      super.tearDown();
      testObj = null;
   }

   @Override
   protected Message createForeignMessage() throws Exception {
      SimpleJMSObjectMessage m = new SimpleJMSObjectMessage();

      logger.debug("creating JMS Message type {}", m.getClass().getName());

      m.setObject(testObj);

      return m;
   }

   @Override
   protected void assertEquivalent(final Message m, final int mode, final boolean redelivery) throws JMSException {
      super.assertEquivalent(m, mode, redelivery);

      ObjectMessage obj = (ObjectMessage) m;

      ProxyAssertSupport.assertNotNull(obj.getObject());
      ProxyAssertSupport.assertEquals(obj.getObject(), testObj);
   }

}

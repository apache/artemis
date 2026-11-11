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
package org.objectweb.jtests.jms.conform.message;

import javax.jms.DeliveryMode;
import javax.jms.Message;

import org.junit.Assert;
import org.junit.Test;
import org.objectweb.jtests.jms.framework.JMSTestCase;
// FIXME include in TestSuite @RunWith(Suite.class)@Suite.SuiteClasses(...)

/**
 * Test the default constants of the {@code javax.jms.Message} interface.
 */
public class MessageDefaultTest extends JMSTestCase {

   /**
    * test that the {@code DEFAULT_ROUTING_TYPE} of {@code javax.jms.Message} corresponds to
    * {@code javax.jms.Delivery.PERSISTENT}.
    */
   @Test
   public void testDEFAULT_DELIVERY_MODE() {
      Assert.assertEquals("The delivery mode is persistent by default.\n", DeliveryMode.PERSISTENT, Message.DEFAULT_DELIVERY_MODE);
   }

   /**
    * test that the {@code DEFAULT_PRIORITY} of {@code javax.jms.Message} corresponds to 4.
    */
   @Test
   public void testDEFAULT_PRIORITY() {
      Assert.assertEquals("The default priority is 4.\n", 4, Message.DEFAULT_PRIORITY);
   }
}

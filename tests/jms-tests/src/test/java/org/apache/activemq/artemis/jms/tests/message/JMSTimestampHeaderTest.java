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

import javax.jms.Message;

import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.Test;

public class JMSTimestampHeaderTest extends MessageHeaderTestBase {



   @Test
   public void testJMSTimestamp() throws Exception {
      Message m = queueProducerSession.createMessage();

      long t1 = System.currentTimeMillis();
      queueProducer.send(m);
      long t2 = System.currentTimeMillis();
      long timestamp = queueConsumer.receive().getJMSTimestamp();

      ProxyAssertSupport.assertTrue(timestamp >= t1);
      ProxyAssertSupport.assertTrue(timestamp <= t2);
   }

   @Test
   public void testDisabledTimestamp() throws Exception {
      Message m = queueProducerSession.createMessage();

      queueProducer.setDisableMessageTimestamp(true);
      queueProducer.send(m);
      ProxyAssertSupport.assertEquals(0L, queueConsumer.receive().getJMSTimestamp());
   }

}

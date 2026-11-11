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

public class JMSCorrelationIDHeaderTest extends MessageHeaderTestBase {



   @Test
   public void testJMSDestination() throws Exception {
      Message m1 = queueProducerSession.createMessage();

      // Test with correlation id containing a message id
      final String messageID = "ID:812739812378";
      m1.setJMSCorrelationID(messageID);

      queueProducer.send(m1);
      Message m2 = queueConsumer.receive();
      ProxyAssertSupport.assertEquals(messageID, m2.getJMSCorrelationID());

      // Test with correlation id containing an application defined string
      Message m3 = queueProducerSession.createMessage();
      final String appDefinedID = "oiwedjiwjdoiwejdoiwjd";
      m3.setJMSCorrelationID(appDefinedID);

      queueProducer.send(m3);
      Message m4 = queueConsumer.receive();
      ProxyAssertSupport.assertEquals(appDefinedID, m4.getJMSCorrelationID());

      // Test with correlation id containing a byte[]
      Message m5 = queueProducerSession.createMessage();
      final byte[] bytes = new byte[]{-111, 45, 106, 3, -44};
      m5.setJMSCorrelationIDAsBytes(bytes);

      queueProducer.send(m5);
      Message m6 = queueConsumer.receive();
      assertByteArraysEqual(bytes, m6.getJMSCorrelationIDAsBytes());

   }




   private void assertByteArraysEqual(final byte[] bytes1, final byte[] bytes2) {
      if (bytes1 == null || bytes2 == null) {
         ProxyAssertSupport.fail();
      }

      if (bytes1.length != bytes2.length) {
         ProxyAssertSupport.fail();
      }

      for (int i = 0; i < bytes1.length; i++) {
         ProxyAssertSupport.assertEquals(bytes1[i], bytes2[i]);
      }

   }


}

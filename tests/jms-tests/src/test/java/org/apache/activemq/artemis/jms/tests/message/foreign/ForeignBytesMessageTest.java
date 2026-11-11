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

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;

import org.apache.activemq.artemis.jms.tests.message.SimpleJMSBytesMessage;
import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * Tests the delivery/receipt of a foreign byte message
 */
public class ForeignBytesMessageTest extends ForeignMessageTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Override
   protected Message createForeignMessage() throws Exception {
      SimpleJMSBytesMessage m = new SimpleJMSBytesMessage();

      logger.debug("creating JMS Message type {}", m.getClass().getName());

      String bytes = "ActiveMQ";
      m.writeBytes(bytes.getBytes());
      return m;
   }

   @Override
   protected void assertEquivalent(final Message m, final int mode, final boolean redelivery) throws JMSException {
      super.assertEquivalent(m, mode, redelivery);

      BytesMessage byteMsg = (BytesMessage) m;

      StringBuilder sb = new StringBuilder();
      byte[] buffer = new byte[1024];
      int n = byteMsg.readBytes(buffer);
      while (n != -1) {
         sb.append(new String(buffer, 0, n));
         n = byteMsg.readBytes(buffer);
      }
      ProxyAssertSupport.assertEquals("ActiveMQ", sb.toString());
   }
}
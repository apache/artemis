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
import javax.jms.StreamMessage;

import org.apache.activemq.artemis.jms.tests.message.SimpleJMSStreamMessage;
import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * Tests the delivery/receipt of a foreign stream message
 */
public class ForeignStreamMessageTest extends ForeignMessageTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Override
   protected Message createForeignMessage() throws Exception {
      SimpleJMSStreamMessage m = new SimpleJMSStreamMessage();

      logger.debug("creating JMS Message type {}", m.getClass().getName());

      m.writeBoolean(true);
      m.writeBytes("jboss".getBytes());
      m.writeChar('c');
      m.writeDouble(1.0D);
      m.writeFloat(2.0F);
      m.writeInt(3);
      m.writeLong(4L);
      m.writeObject("object");
      m.writeShort((short) 5);
      m.writeString("stringvalue");

      return m;
   }

   @Override
   protected void assertEquivalent(final Message m, final int mode, final boolean redelivery) throws JMSException {
      super.assertEquivalent(m, mode, redelivery);

      StreamMessage sm = (StreamMessage) m;

      ProxyAssertSupport.assertTrue(sm.readBoolean());

      byte[] bytes = new byte[5];
      sm.readBytes(bytes);
      String s = new String(bytes);
      ProxyAssertSupport.assertEquals("jboss", s);
      ProxyAssertSupport.assertEquals(-1, sm.readBytes(bytes));

      ProxyAssertSupport.assertEquals(sm.readChar(), 'c');
      ProxyAssertSupport.assertEquals(sm.readDouble(), 1.0D, 0.0D);
      ProxyAssertSupport.assertEquals(sm.readFloat(), 2.0F, 0.0F);
      ProxyAssertSupport.assertEquals(sm.readInt(), 3);
      ProxyAssertSupport.assertEquals(sm.readLong(), 4L);
      ProxyAssertSupport.assertEquals(sm.readObject(), "object");
      ProxyAssertSupport.assertEquals(sm.readShort(), (short) 5);
      ProxyAssertSupport.assertEquals(sm.readString(), "stringvalue");
   }

}

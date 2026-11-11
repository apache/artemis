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
import javax.jms.StreamMessage;

import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A test that sends/receives stream messages to the JMS provider and verifies their integrity.
 */
public class StreamMessageTest extends MessageTestBase {


   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      message = session.createStreamMessage();
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      message = null;
      super.tearDown();
   }

   @Test
   public void testNullValue() throws Exception {
      StreamMessage m = session.createStreamMessage();

      m.writeString(null);

      queueProd.send(m);

      conn.start();

      StreamMessage rm = (StreamMessage) queueCons.receive();

      ProxyAssertSupport.assertNull(rm.readString());
   }

   // Protected -----------------------------------------------------

   @Override
   protected void prepareMessage(final Message m) throws JMSException {
      super.prepareMessage(m);

      StreamMessage sm = (StreamMessage) m;

      sm.writeBoolean(true);
      sm.writeByte((byte) 3);
      sm.writeBytes(new byte[]{(byte) 4, (byte) 5, (byte) 6});
      sm.writeChar((char) 7);
      sm.writeDouble(8.0);
      sm.writeFloat(9.0f);
      sm.writeInt(10);
      sm.writeLong(11L);
      sm.writeObject("this is an object");
      sm.writeShort((short) 12);
      sm.writeString("this is a String");
   }

   @Override
   protected void assertEquivalent(final Message m, final int mode, final boolean redelivery) throws JMSException {
      super.assertEquivalent(m, mode, redelivery);

      StreamMessage sm = (StreamMessage) m;

      sm.reset();

      ProxyAssertSupport.assertTrue(sm.readBoolean());
      ProxyAssertSupport.assertEquals((byte) 3, sm.readByte());
      byte[] bytes = new byte[3];
      sm.readBytes(bytes);
      ProxyAssertSupport.assertEquals((byte) 4, bytes[0]);
      ProxyAssertSupport.assertEquals((byte) 5, bytes[1]);
      ProxyAssertSupport.assertEquals((byte) 6, bytes[2]);
      ProxyAssertSupport.assertEquals(-1, sm.readBytes(bytes));
      ProxyAssertSupport.assertEquals((char) 7, sm.readChar());
      ProxyAssertSupport.assertEquals(8.0, sm.readDouble());
      ProxyAssertSupport.assertEquals(9.0f, sm.readFloat());
      ProxyAssertSupport.assertEquals(10, sm.readInt());
      ProxyAssertSupport.assertEquals(11L, sm.readLong());
      ProxyAssertSupport.assertEquals("this is an object", sm.readObject());
      ProxyAssertSupport.assertEquals((short) 12, sm.readShort());
      ProxyAssertSupport.assertEquals("this is a String", sm.readString());
   }

}

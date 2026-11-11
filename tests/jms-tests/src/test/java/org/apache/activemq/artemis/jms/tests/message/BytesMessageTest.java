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

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;

import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * A test that sends/receives bytes messages to the JMS provider and verifies their integrity.
 */
public class BytesMessageTest extends MessageTestBase {

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      message = session.createBytesMessage();
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      message = null;
      super.tearDown();
   }

   // Protected -----------------------------------------------------

   @Override
   protected void prepareMessage(final Message m) throws JMSException {
      super.prepareMessage(m);

      BytesMessage bm = (BytesMessage) m;

      bm.writeBoolean(true);
      bm.writeByte((byte) 3);
      bm.writeBytes(new byte[]{(byte) 4, (byte) 5, (byte) 6});
      bm.writeChar((char) 7);
      bm.writeDouble(8.0);
      bm.writeFloat(9.0f);
      bm.writeInt(10);
      bm.writeLong(11L);
      bm.writeShort((short) 12);
      bm.writeUTF("this is an UTF String");
      bm.reset();
   }

   @Override
   protected void assertEquivalent(final Message m, final int mode, final boolean redelivered) throws JMSException {
      super.assertEquivalent(m, mode, redelivered);

      BytesMessage bm = (BytesMessage) m;

      ProxyAssertSupport.assertTrue(bm.readBoolean());
      ProxyAssertSupport.assertEquals((byte) 3, bm.readByte());
      byte[] bytes = new byte[3];
      bm.readBytes(bytes);
      ProxyAssertSupport.assertEquals((byte) 4, bytes[0]);
      ProxyAssertSupport.assertEquals((byte) 5, bytes[1]);
      ProxyAssertSupport.assertEquals((byte) 6, bytes[2]);
      ProxyAssertSupport.assertEquals((char) 7, bm.readChar());
      ProxyAssertSupport.assertEquals(8.0, bm.readDouble());
      ProxyAssertSupport.assertEquals(9.0f, bm.readFloat());
      ProxyAssertSupport.assertEquals(10, bm.readInt());
      ProxyAssertSupport.assertEquals(11L, bm.readLong());
      ProxyAssertSupport.assertEquals((short) 12, bm.readShort());
      ProxyAssertSupport.assertEquals("this is an UTF String", bm.readUTF());
   }

}

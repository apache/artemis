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
import javax.jms.MapMessage;
import javax.jms.Message;

import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A test that sends/receives map messages to the JMS provider and verifies their integrity.
 */
public class MapMessageTest extends MessageTestBase {


   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      message = session.createMapMessage();
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      message = null;

      super.tearDown();
   }

   @Test
   public void testNullValue() throws Exception {
      MapMessage m = session.createMapMessage();

      m.setString("nullValue", null);

      queueProd.send(m);

      MapMessage rm = (MapMessage) queueCons.receive(2000);

      ProxyAssertSupport.assertNotNull(rm);

      ProxyAssertSupport.assertNull(rm.getString("nullValue"));
   }

   // Protected -----------------------------------------------------

   @Override
   protected void prepareMessage(final Message m) throws JMSException {
      super.prepareMessage(m);

      MapMessage mm = (MapMessage) m;

      mm.setBoolean("boolean", true);
      mm.setByte("byte", (byte) 3);
      mm.setBytes("bytes", new byte[]{(byte) 3, (byte) 4, (byte) 5});
      mm.setChar("char", (char) 6);
      mm.setDouble("double", 7.0);
      mm.setFloat("float", 8.0f);
      mm.setInt("int", 9);
      mm.setLong("long", 10L);
      mm.setObject("object", new String("this is an object"));
      mm.setShort("short", (short) 11);
      mm.setString("string", "this is a string");
   }

   @Override
   protected void assertEquivalent(final Message m, final int mode, final boolean redelivery) throws JMSException {
      super.assertEquivalent(m, mode, redelivery);

      MapMessage mm = (MapMessage) m;

      ProxyAssertSupport.assertTrue(mm.getBoolean("boolean"));
      ProxyAssertSupport.assertEquals((byte) 3, mm.getByte("byte"));
      byte[] bytes = mm.getBytes("bytes");
      ProxyAssertSupport.assertEquals((byte) 3, bytes[0]);
      ProxyAssertSupport.assertEquals((byte) 4, bytes[1]);
      ProxyAssertSupport.assertEquals((byte) 5, bytes[2]);
      ProxyAssertSupport.assertEquals((char) 6, mm.getChar("char"));
      ProxyAssertSupport.assertEquals(7.0, mm.getDouble("double"));
      ProxyAssertSupport.assertEquals(8.0f, mm.getFloat("float"));
      ProxyAssertSupport.assertEquals(9, mm.getInt("int"));
      ProxyAssertSupport.assertEquals(10L, mm.getLong("long"));
      ProxyAssertSupport.assertEquals("this is an object", mm.getObject("object"));
      ProxyAssertSupport.assertEquals((short) 11, mm.getShort("short"));
      ProxyAssertSupport.assertEquals("this is a string", mm.getString("string"));
   }
}

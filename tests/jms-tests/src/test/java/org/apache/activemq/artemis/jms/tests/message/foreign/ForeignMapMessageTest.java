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
import javax.jms.MapMessage;
import javax.jms.Message;

import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.core.client.impl.ClientMessageImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQMapMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQMessage;
import org.apache.activemq.artemis.jms.tests.message.SimpleJMSMapMessage;
import org.apache.activemq.artemis.jms.tests.util.ProxyAssertSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * Tests the delivery/receipt of a foreign map message
 */
public class ForeignMapMessageTest extends ForeignMessageTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final String obj = new String("stringobject");

   @Override
   protected Message createForeignMessage() throws Exception {
      SimpleJMSMapMessage m = new SimpleJMSMapMessage();

      logger.debug("creating JMS Message type {}", m.getClass().getName());

      m.setBoolean("boolean1", true);
      m.setChar("char1", 'c');
      m.setDouble("double1", 1.0D);
      m.setFloat("float1", 2.0F);
      m.setInt("int1", 3);
      m.setLong("long1", 4L);
      m.setObject("object1", obj);
      m.setShort("short1", (short) 5);
      m.setString("string1", "stringvalue");

      return m;
   }

   @Override
   protected void assertEquivalent(final Message m, final int mode, final boolean redelivery) throws JMSException {
      super.assertEquivalent(m, mode, redelivery);

      MapMessage map = (MapMessage) m;

      ProxyAssertSupport.assertTrue(map.getBoolean("boolean1"));
      ProxyAssertSupport.assertEquals(map.getChar("char1"), 'c');
      ProxyAssertSupport.assertEquals(map.getDouble("double1"), 1.0D, 0.0D);
      ProxyAssertSupport.assertEquals(map.getFloat("float1"), 2.0F, 0.0F);
      ProxyAssertSupport.assertEquals(map.getInt("int1"), 3);
      ProxyAssertSupport.assertEquals(map.getLong("long1"), 4L);
      ProxyAssertSupport.assertEquals(map.getObject("object1"), obj);
      ProxyAssertSupport.assertEquals(map.getShort("short1"), (short) 5);
      ProxyAssertSupport.assertEquals(map.getString("string1"), "stringvalue");
   }

   @Test
   public void testSerializeEmptyForeignMapMessage() throws Exception {
      ClientMessage clientMessage = new ClientMessageImpl(ActiveMQMapMessage.TYPE, true, 0, System.currentTimeMillis(), (byte) 4, 1000);
      var mockSession = Mockito.mock(ClientSession.class);
      Mockito.when(mockSession.createMessage(
            Mockito.anyByte(),
            Mockito.anyBoolean(),
            Mockito.anyLong(),
            Mockito.anyLong(),
            Mockito.anyByte()))
            .thenReturn(clientMessage);

      MapMessage emptyForeignMessage = new SimpleJMSMapMessage();

      ActiveMQMessage copy = new ActiveMQMapMessage(emptyForeignMessage, mockSession);

      copy.doBeforeSend();

      Assertions.assertEquals(1, copy.getCoreMessage().getBodySize());
   }
}

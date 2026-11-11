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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageFormatException;
import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class BodyIsAssignableFromTest extends MessageBodyTestCase {

   @Test
   public void testText() throws JMSException {
      bodyAssignableFrom(JmsMessageType.TEXT, String.class, CharSequence.class, Comparable.class, Serializable.class);
      bodyNotAssignableFrom(JmsMessageType.TEXT, List.class, StringBuilder.class, Map.class, File.class);
   }

   @Test
   public void testMap() throws JMSException {
      bodyAssignableFrom(JmsMessageType.MAP, Map.class, Object.class);
      bodyNotAssignableFrom(JmsMessageType.MAP, String.class, CharSequence.class, Comparable.class, Serializable.class);
   }

   @Test
   public void testStream() throws JMSException {
      bodyNotAssignableFrom(JmsMessageType.STREAM, Object.class, Serializable.class);
   }

   @Test
   public void testByte() throws JMSException {
      bodyAssignableFrom(JmsMessageType.BYTE, Object.class, byte[].class);
      bodyNotAssignableFrom(JmsMessageType.BYTE, String.class, CharSequence.class, Comparable.class);
   }

   @Test
   public void testObject() throws JMSException {
      bodyAssignableFrom(JmsMessageType.OBJECT, Object.class, Serializable.class, Comparable.class, Double.class);
      // we are sending a Double in the body, so the de-serialized Object will be an instanceof these:
      bodyAssignableFrom(JmsMessageType.OBJECT, Comparable.class, Double.class);
      bodyNotAssignableFrom(JmsMessageType.OBJECT, String.class, CharSequence.class, List.class);
   }

   private void bodyAssignableFrom(JmsMessageType type, Class... clazz) throws JMSException {
      bodyAssignableFrom(type, true, clazz);
   }

   private void bodyAssignableFrom(final JmsMessageType type, final boolean bool, Class... clazz) throws JMSException {
      assertNotNull(clazz, "clazz!=null");
      assertTrue(clazz.length > 0, "clazz[] not empty");
      Object body = createBodySendAndReceive(type);
      Message msg = queueConsumer.receive(500);
      assertNotNull(msg, "must have a msg");
      assertEquals(type.toString(), msg.getStringProperty("type"));
      for (Class<?> c : clazz) {
         assertEquals(bool, msg.isBodyAssignableTo(c), msg + " " + type + " & " + c + ": " + bool);
         if (bool) {
            Object receivedBody = msg.getBody(c);
            assertTrue(c.isInstance(receivedBody), "correct type " + c);
            if (body.getClass().isAssignableFrom(byte[].class)) {
               assertArrayEquals(byte[].class.cast(body), (byte[]) receivedBody);
            } else {
               assertEquals(body, receivedBody, "clazz=" + c + ", bodies must match.. " + body.equals(receivedBody));
            }
         } else {
            try {
               Object foo = msg.getBody(c);
               assertNull(foo, "body should be null");
            } catch (MessageFormatException e) {
               // expected
            }
         }
      }
   }

   private Object createBodySendAndReceive(JmsMessageType type) throws JMSException {
      Object res = null;
      Message msg = null;
      switch (type) {
         case BYTE:
            BytesMessage mByte = queueProducerSession.createBytesMessage();
            final int size = 20;
            byte[] resByte = new byte[size];
            for (int i = 0; i < size; i++) {
               resByte[i] = (byte) i;
               mByte.writeByte((byte) i);
            }
            msg = mByte;
            res = resByte;
            break;
         case TEXT:
            res = "JMS2";
            msg = queueProducerSession.createTextMessage("JMS2");
            break;
         case STREAM:
            msg = queueProducerSession.createStreamMessage();
            break;
         case OBJECT:
            res = 37.6;
            msg = queueProducerSession.createObjectMessage(37.6);
            break;
         case MAP:
            MapMessage msg1 = queueProducerSession.createMapMessage();
            msg1.setInt("int", 13);
            msg1.setLong("long", 37L);
            msg1.setString("string", "crocodile");
            msg = msg1;
            Map<String, Object> map = new HashMap<>();
            map.put("int", 13);
            map.put("long", 37L);
            map.put("string", "crocodile");
            res = map;
            break;
         default:
            fail("no default...");
      }
      assertNotNull(msg);
      msg.setStringProperty("type", type.toString());
      queueProducer.send(msg);
      return res;
   }

   private void bodyNotAssignableFrom(JmsMessageType type, Class... clazz) throws JMSException {
      bodyAssignableFrom(type, false, clazz);
   }
}

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
package org.apache.activemq.artemis.tests.unit.core.message.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ClientMessageImpl;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.persistence.CoreMessageObjectPools;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionSendMessage;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.UUID;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class MessageImplTest extends ActiveMQTestBase {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   public void getSetAttributes() {
      for (int j = 0; j < 10; j++) {
         byte[] bytes = new byte[1000];
         for (int i = 0; i < bytes.length; i++) {
            bytes[i] = RandomUtil.randomByte();
         }

         final byte type = RandomUtil.randomByte();
         final boolean durable = RandomUtil.randomBoolean();
         final long expiration = RandomUtil.randomLong();
         final long timestamp = RandomUtil.randomLong();
         final byte priority = RandomUtil.randomByte();
         ICoreMessage message1 = new ClientMessageImpl(type, durable, expiration, timestamp, priority, 100);

         ICoreMessage message = message1;

         assertEquals(type, message.getType());
         assertEquals(durable, message.isDurable());
         assertEquals(expiration, message.getExpiration());
         assertEquals(timestamp, message.getTimestamp());
         assertEquals(priority, message.getPriority());

         final SimpleString destination = SimpleString.of(RandomUtil.randomUUIDString());
         final boolean durable2 = RandomUtil.randomBoolean();
         final long expiration2 = RandomUtil.randomLong();
         final long timestamp2 = RandomUtil.randomLong();
         final byte priority2 = RandomUtil.randomByte();

         message.setAddress(destination);
         assertEquals(destination, message.getAddressSimpleString());

         message.setDurable(durable2);
         assertEquals(durable2, message.isDurable());

         message.setExpiration(expiration2);
         assertEquals(expiration2, message.getExpiration());

         message.setTimestamp(timestamp2);
         assertEquals(timestamp2, message.getTimestamp());

         message.setPriority(priority2);
         assertEquals(priority2, message.getPriority());

      }
   }

   @Test
   public void testExpired() {
      Message message = new ClientMessageImpl();

      assertEquals(0, message.getExpiration());
      assertFalse(message.isExpired());

      message.setExpiration(System.currentTimeMillis() + 1000);
      assertFalse(message.isExpired());

      message.setExpiration(System.currentTimeMillis() - 1);
      assertTrue(message.isExpired());

      message.setExpiration(System.currentTimeMillis() - 1000);
      assertTrue(message.isExpired());

      message.setExpiration(0);
      assertFalse(message.isExpired());
   }

   @Test
   public void testProperties() {
      for (int j = 0; j < 10; j++) {
         Message msg = new ClientMessageImpl();

         SimpleString prop1 = SimpleString.of("prop1");
         boolean val1 = RandomUtil.randomBoolean();
         msg.putBooleanProperty(prop1, val1);

         SimpleString prop2 = SimpleString.of("prop2");
         byte val2 = RandomUtil.randomByte();
         msg.putByteProperty(prop2, val2);

         SimpleString prop3 = SimpleString.of("prop3");
         byte[] val3 = RandomUtil.randomBytes();
         msg.putBytesProperty(prop3, val3);

         SimpleString prop4 = SimpleString.of("prop4");
         double val4 = RandomUtil.randomDouble();
         msg.putDoubleProperty(prop4, val4);

         SimpleString prop5 = SimpleString.of("prop5");
         float val5 = RandomUtil.randomFloat();
         msg.putFloatProperty(prop5, val5);

         SimpleString prop6 = SimpleString.of("prop6");
         int val6 = RandomUtil.randomInt();
         msg.putIntProperty(prop6, val6);

         SimpleString prop7 = SimpleString.of("prop7");
         long val7 = RandomUtil.randomLong();
         msg.putLongProperty(prop7, val7);

         SimpleString prop8 = SimpleString.of("prop8");
         short val8 = RandomUtil.randomShort();
         msg.putShortProperty(prop8, val8);

         SimpleString prop9 = SimpleString.of("prop9");
         SimpleString val9 = SimpleString.of(RandomUtil.randomUUIDString());
         msg.putStringProperty(prop9, val9);

         assertEquals(9, msg.getPropertyNames().size());
         assertTrue(msg.getPropertyNames().contains(prop1));
         assertTrue(msg.getPropertyNames().contains(prop2));
         assertTrue(msg.getPropertyNames().contains(prop3));
         assertTrue(msg.getPropertyNames().contains(prop4));
         assertTrue(msg.getPropertyNames().contains(prop5));
         assertTrue(msg.getPropertyNames().contains(prop6));
         assertTrue(msg.getPropertyNames().contains(prop7));
         assertTrue(msg.getPropertyNames().contains(prop8));
         assertTrue(msg.getPropertyNames().contains(prop9));

         assertTrue(msg.containsProperty(prop1));
         assertTrue(msg.containsProperty(prop2));
         assertTrue(msg.containsProperty(prop3));
         assertTrue(msg.containsProperty(prop4));
         assertTrue(msg.containsProperty(prop5));
         assertTrue(msg.containsProperty(prop6));
         assertTrue(msg.containsProperty(prop7));
         assertTrue(msg.containsProperty(prop8));
         assertTrue(msg.containsProperty(prop9));

         assertEquals(val1, msg.getObjectProperty(prop1));
         assertEquals(val2, msg.getObjectProperty(prop2));
         assertEquals(val3, msg.getObjectProperty(prop3));
         assertEquals(val4, msg.getObjectProperty(prop4));
         assertEquals(val5, msg.getObjectProperty(prop5));
         assertEquals(val6, msg.getObjectProperty(prop6));
         assertEquals(val7, msg.getObjectProperty(prop7));
         assertEquals(val8, msg.getObjectProperty(prop8));
         assertEquals(val9, msg.getObjectProperty(prop9));

         SimpleString val10 = SimpleString.of(RandomUtil.randomUUIDString());
         // test overwrite
         msg.putStringProperty(prop9, val10);
         assertEquals(val10, msg.getObjectProperty(prop9));

         int val11 = RandomUtil.randomInt();
         msg.putIntProperty(prop9, val11);
         assertEquals(val11, msg.getObjectProperty(prop9));

         msg.removeProperty(prop1);
         assertEquals(8, msg.getPropertyNames().size());
         assertTrue(msg.getPropertyNames().contains(prop2));
         assertTrue(msg.getPropertyNames().contains(prop3));
         assertTrue(msg.getPropertyNames().contains(prop4));
         assertTrue(msg.getPropertyNames().contains(prop5));
         assertTrue(msg.getPropertyNames().contains(prop6));
         assertTrue(msg.getPropertyNames().contains(prop7));
         assertTrue(msg.getPropertyNames().contains(prop8));
         assertTrue(msg.getPropertyNames().contains(prop9));

         msg.removeProperty(prop2);
         assertEquals(7, msg.getPropertyNames().size());
         assertTrue(msg.getPropertyNames().contains(prop3));
         assertTrue(msg.getPropertyNames().contains(prop4));
         assertTrue(msg.getPropertyNames().contains(prop5));
         assertTrue(msg.getPropertyNames().contains(prop6));
         assertTrue(msg.getPropertyNames().contains(prop7));
         assertTrue(msg.getPropertyNames().contains(prop8));
         assertTrue(msg.getPropertyNames().contains(prop9));

         msg.removeProperty(prop9);
         assertEquals(6, msg.getPropertyNames().size());
         assertTrue(msg.getPropertyNames().contains(prop3));
         assertTrue(msg.getPropertyNames().contains(prop4));
         assertTrue(msg.getPropertyNames().contains(prop5));
         assertTrue(msg.getPropertyNames().contains(prop6));
         assertTrue(msg.getPropertyNames().contains(prop7));
         assertTrue(msg.getPropertyNames().contains(prop8));

         msg.removeProperty(prop3);
         msg.removeProperty(prop4);
         msg.removeProperty(prop5);
         msg.removeProperty(prop6);
         msg.removeProperty(prop7);
         msg.removeProperty(prop8);
         assertEquals(0, msg.getPropertyNames().size());
      }
   }

   @Test
   public void testMessageCopyIssue() throws Exception {
      for (long i = 0; i < 300; i++) {
         if (i % 10 == 0) {
            logger.debug("#test {}", i);
         }
         internalMessageCopy();
      }
   }

   @Test
   public void testCopyAndGetBuffer() throws Exception {
      ClientMessageImpl messageImpl = new ClientMessageImpl((byte)0, true, 0L, 0L, (byte)0, 100, new CoreMessageObjectPools());
      ICoreMessage copy = messageImpl.copy();
      copy.getBodyBuffer();
   }

   @Test
   public void testMessageCopyHeadersAndProperties() {
      CoreMessage msg1 = new CoreMessage(123, 18);
      SimpleString address = SimpleString.of("address");
      msg1.setAddress(address);
      UUID uid = UUIDGenerator.getInstance().generateUUID();
      msg1.setUserID(uid);
      byte type = 3;
      msg1.setType(type);
      boolean durable = true;
      msg1.setDurable(durable);
      long expiration = System.currentTimeMillis();
      msg1.setExpiration(expiration);
      long timestamp = System.currentTimeMillis();
      msg1.setTimestamp(timestamp);
      byte priority = 9;
      msg1.setPriority(priority);

      String routeTo = "_HQ_ROUTE_TOsomething";
      String value = "Byte array substitute";
      msg1.putStringProperty(routeTo, value);

      CoreMessage msg2 = new CoreMessage(456, 18);

      msg2.moveHeadersAndProperties(msg1);

      assertEquals(msg1.getAddress(), msg2.getAddress());
      assertEquals(msg1.getUserID(), msg2.getUserID());
      assertEquals(msg1.getType(), msg2.getType());
      assertEquals(msg1.isDurable(), msg2.isDurable());
      assertEquals(msg1.getExpiration(), msg2.getExpiration());
      assertEquals(msg1.getTimestamp(), msg2.getTimestamp());
      assertEquals(msg1.getPriority(), msg2.getPriority());

      assertEquals(value, msg2.getStringProperty(routeTo));

      //now change the property on msg2 shouldn't affect msg1
      msg2.setAddress(address.concat("new"));
      msg2.setUserID(UUIDGenerator.getInstance().generateUUID());
      msg2.setType(--type);
      msg2.setDurable(!durable);
      msg2.setExpiration(expiration + 1000);
      msg2.setTimestamp(timestamp + 1000);
      msg2.setPriority(--priority);

      msg2.putStringProperty(routeTo, value + "new");

      assertNotEquals(msg1.getAddress(), msg2.getAddress());
      assertNotEquals(msg1.getUserID(), msg2.getUserID());
      assertNotEquals(msg1.getType(), msg2.getType());
      assertNotEquals(msg1.isDurable(), msg2.isDurable());
      assertNotEquals(msg1.getExpiration(), msg2.getExpiration());
      assertNotEquals(msg1.getTimestamp(), msg2.getTimestamp());
      assertNotEquals(msg1.getPriority(), msg2.getPriority());

      assertNotEquals(msg1.getStringProperty(routeTo), msg2.getStringProperty(routeTo));

   }

   private void internalMessageCopy() throws Exception {
      final long RUNS = 2;
      final CoreMessage msg = new CoreMessage(123, 18);

      msg.setMessageID(RandomUtil.randomLong());
      msg.setAddress(SimpleString.of("Batatantkashf aksjfh aksfjh askfdjh askjfh "));

      final AtomicInteger errors = new AtomicInteger(0);

      int T1_number = 10;
      int T2_number = 10;

      final CountDownLatch latchAlign = new CountDownLatch(T1_number + T2_number);
      final CountDownLatch latchReady = new CountDownLatch(1);
      class T1 extends Thread {

         @Override
         public void run() {
            latchAlign.countDown();
            try {
               latchReady.await();
            } catch (Exception ignored) {
            }

            for (int i = 0; i < RUNS; i++) {
               try {
                  Message newMsg = msg.copy();
               } catch (Throwable e) {
                  e.printStackTrace();
                  errors.incrementAndGet();
               }
            }
         }
      }

      class T2 extends Thread {

         @Override
         public void run() {
            latchAlign.countDown();
            try {
               latchReady.await();
            } catch (Exception ignored) {
            }

            for (int i = 0; i < RUNS; i++) {
               ActiveMQBuffer buf = null;
               try {
                  SessionSendMessage ssm = new SessionSendMessage(msg);
                  buf = ssm.encode(null);
                  simulateRead(buf);
               } catch (Throwable e) {
                  e.printStackTrace();
                  errors.incrementAndGet();
               } finally {
                  if (buf != null) {
                     buf.release();
                  }
               }
            }
         }
      }

      List<Thread> threads = new ArrayList<>();

      for (int i = 0; i < T1_number; i++) {
         T1 t = new T1();
         threads.add(t);
         t.start();
      }

      for (int i = 0; i < T2_number; i++) {
         T2 t2 = new T2();
         threads.add(t2);
         t2.start();
      }

      latchAlign.await();

      latchReady.countDown();

      for (Thread t : threads) {
         t.join();
      }

      assertEquals(0, errors.get());
   }

   private void simulateRead(ActiveMQBuffer buf) {
      buf.setIndex(buf.capacity() / 2, buf.capacity() / 2);

      // ok this is not actually happening during the read process, but changing this shouldn't affect the buffer on copy
      // this is to exaggerate the isolation on this test
      buf.writeBytes(new byte[1024]);
   }

   @Test
   public void testCloseCallBuffer() throws Exception {

      SimpleString ADDRESS = SimpleString.of("SimpleAddress");

      final int messageSize = 1024 * 1024 - 64;

      final int journalsize = 10 * 1024 * 1024;

      ServerLocator locator = createInVMNonHALocator();

      locator.setMinLargeMessageSize(1024 * 1024);

      ClientSession session = null;

      ConfigurationImpl config = (ConfigurationImpl)createDefaultConfig(false);
      config.setJournalFileSize(journalsize).setJournalBufferSize_AIO(1024 * 1024).setJournalBufferSize_NIO(1024 * 1024);

      ActiveMQServer server = createServer(true, config);

      server.start();

      ClientSessionFactory sf = addSessionFactory(createSessionFactory(locator));

      session = addClientSession(sf.createSession(false, false, 0));

      session.createQueue(QueueConfiguration.of(ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);

      ClientConsumer consumer = session.createConsumer(ADDRESS);

      ClientMessage clientFile = session.createMessage(true);
      for (int i = 0; i < messageSize; i++) {
         clientFile.getBodyBuffer().writeByte(getSamplebyte(i));
      }

      producer.send(clientFile);

      session.commit();

      session.start();

      ClientMessage msg1 = consumer.receive(1000);

      Wait.assertTrue(server::isActive);

      assertNotNull(msg1);
   }

}

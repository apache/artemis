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
package org.apache.activemq.artemis.tests.integration.jms.largemessage;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageNotWriteableException;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class JMSLargeMessageTest extends JMSTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   Queue queue1;


   @Override
   protected boolean usePersistence() {
      return true;
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      queue1 = createQueue("queue1");
   }

   @Test
   public void testSimpleLargeMessage() throws Exception {

      conn = cf.createConnection();

      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageProducer prod = session.createProducer(queue1);

      BytesMessage m = session.createBytesMessage();

      m.setObjectProperty("JMS_AMQ_InputStream", ActiveMQTestBase.createFakeLargeStream(1024 * 1024));

      prod.send(m);

      conn.close();

      conn = cf.createConnection();

      session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageConsumer cons = session.createConsumer(queue1);

      conn.start();

      BytesMessage rm = (BytesMessage) cons.receive(10000);

      byte[] data = new byte[1024];

      for (int i = 0; i < 1024 * 1024; i += 1024) {
         int numberOfBytes = rm.readBytes(data);
         assertEquals(1024, numberOfBytes);
         for (int j = 0; j < 1024; j++) {
            assertEquals(ActiveMQTestBase.getSamplebyte(i + j), data[j]);
         }
      }

      assertNotNull(rm);
   }

   @Test
   public void testSimpleLargeMessage2() throws Exception {
      conn = cf.createConnection();

      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageProducer prod = session.createProducer(queue1);

      BytesMessage m = session.createBytesMessage();

      m.setObjectProperty("JMS_AMQ_InputStream", ActiveMQTestBase.createFakeLargeStream(10));

      prod.send(m);

      conn.close();

      conn = cf.createConnection();

      session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageConsumer cons = session.createConsumer(queue1);

      conn.start();

      BytesMessage rm = (BytesMessage) cons.receive(10000);

      byte[] data = new byte[1024];

      int numberOfBytes = rm.readBytes(data);
      assertEquals(10, numberOfBytes);
      for (int j = 0; j < numberOfBytes; j++) {
         assertEquals(ActiveMQTestBase.getSamplebyte(j), data[j]);
      }

      assertNotNull(rm);
   }

   @Test
   public void testExceptionsOnSettingNonStreaming() throws Exception {
      conn = cf.createConnection();

      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      TextMessage msg = session.createTextMessage();

      try {
         msg.setObjectProperty("JMS_AMQ_InputStream", ActiveMQTestBase.createFakeLargeStream(10));
         fail("Exception was expected");
      } catch (JMSException e) {
      }

      msg.setText("hello");

      MessageProducer prod = session.createProducer(queue1);

      prod.send(msg);

      conn.close();

      conn = cf.createConnection();

      session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageConsumer cons = session.createConsumer(queue1);

      conn.start();

      TextMessage rm = (TextMessage) cons.receive(10000);

      try {
         rm.setObjectProperty("JMS_AMQ_OutputStream", new OutputStream() {
            @Override
            public void write(final int b) throws IOException {
               logger.debug("b = {}", b);
            }

         });
         fail("Exception was expected");
      } catch (JMSException e) {
      }

      assertEquals("hello", rm.getText());

      assertNotNull(rm);

   }

   @Test
   public void testWaitOnOutputStream() throws Exception {
      int msgSize = 1024 * 1024;

      conn = cf.createConnection();

      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageProducer prod = session.createProducer(queue1);

      BytesMessage m = session.createBytesMessage();

      m.setObjectProperty("JMS_AMQ_InputStream", ActiveMQTestBase.createFakeLargeStream(msgSize));

      prod.send(m);

      conn.close();

      conn = cf.createConnection();

      session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageConsumer cons = session.createConsumer(queue1);

      conn.start();

      BytesMessage rm = (BytesMessage) cons.receive(10000);
      assertNotNull(rm);

      final AtomicLong numberOfBytes = new AtomicLong(0);

      final AtomicInteger numberOfErrors = new AtomicInteger(0);

      OutputStream out = new OutputStream() {

         int position = 0;

         @Override
         public void write(final int b) throws IOException {
            numberOfBytes.incrementAndGet();
            if (ActiveMQTestBase.getSamplebyte(position++) != b) {
               logger.warn("Wrong byte at position {}", position);
               numberOfErrors.incrementAndGet();
            }
         }

      };

      try {
         rm.setObjectProperty("JMS_AMQ_InputStream", ActiveMQTestBase.createFakeLargeStream(100));
         fail("Exception expected!");
      } catch (MessageNotWriteableException expected) {
      }

      rm.setObjectProperty("JMS_AMQ_SaveStream", out);

      assertEquals(msgSize, numberOfBytes.get());

      assertEquals(0, numberOfErrors.get());

   }

   @Test
   public void testHugeString() throws Exception {
      int msgSize = 1024 * 1024;

      conn = cf.createConnection();

      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageProducer prod = session.createProducer(queue1);

      TextMessage m = session.createTextMessage();

      final String originalString = RandomUtil.randomAlphaNumericString(msgSize);

      m.setText(originalString);

      prod.send(m);

      conn.close();

      validateNoFilesOnLargeDir(server.getConfiguration().getLargeMessagesDirectory(), 1);

      conn = cf.createConnection();

      session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageConsumer cons = session.createConsumer(queue1);

      conn.start();

      TextMessage rm = (TextMessage) cons.receive(10000);
      assertNotNull(rm);

      String str = rm.getText();
      assertEquals(originalString, str);
      conn.close();
      validateNoFilesOnLargeDir(server.getConfiguration().getLargeMessagesDirectory(), 0);

   }

   class ThreadReader extends Thread {

      CountDownLatch latch;

      ThreadReader(final CountDownLatch latch) {
         this.latch = latch;
      }

      @Override
      public void run() {
      }
   }

}

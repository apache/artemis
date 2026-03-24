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
package org.apache.activemq.artemis.tests.integration.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicLong;

import io.netty.util.internal.PlatformDependent;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.StoreConfiguration;
import org.apache.activemq.artemis.core.management.impl.QueueControlImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;

import javax.management.openmbean.CompositeData;

/**
 * A LargeMessageCompressTest
 * <br>
 * Just extend the LargeMessageTest
 */
public class LargeMessageCompressTest extends LargeMessageTest {


   public LargeMessageCompressTest(StoreConfiguration.StoreType storeType) {
      super(storeType);
      isCompressedTest = true;
   }

   @Override
   protected void validateLargeMessageComplete(ActiveMQServer server) throws Exception {
   }

   @Override
   protected boolean isNetty() {
      return false;
   }

   @Override
   protected ServerLocator createFactory(final boolean isNetty) throws Exception {
      return super.createFactory(isNetty).setCompressLargeMessage(true);
   }

   @Test
   public void testLargeMessageCompressionNotCompressedAndBrowsed() throws Exception {
      final int messageSize = (int) (3.5 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);

      ActiveMQServer server = createServer(true, isNetty());

      server.start();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, false, false));

      session.createQueue(new QueueConfiguration(ADDRESS).setAddress(ADDRESS).setDurable(false).setTemporary(true));

      ClientProducer producer = session.createProducer(ADDRESS);

      Message clientFile = createLargeClientMessageStreaming(session, messageSize, true);

      clientFile.setType(Message.TEXT_TYPE);

      producer.send(clientFile);

      session.commit();

      session.close();

      QueueControlImpl queueControl = (QueueControlImpl) server.getManagementService().getResource("queue.SimpleAddress");

      CompositeData[] browse = queueControl.browse();

      Assert.assertNotNull(browse);

      Assert.assertEquals(browse.length, 1);

      Assert.assertEquals(browse[0].get("text"), "[compressed]");

      //clean up
      session = addClientSession(sf.createSession(false, false, false));

      session.start();

      ClientConsumer consumer = session.createConsumer(ADDRESS);
      ClientMessage msg1 = consumer.receive(1000);
      Assert.assertNotNull(msg1);

      for (int i = 0; i < messageSize; i++) {
         byte b = msg1.getBodyBuffer().readByte();
         assertEquals("position = " + i, getSamplebyte(i), b);
      }

      msg1.acknowledge();
      session.commit();

      consumer.close();

      session.close();

      validateNoFilesOnLargeDir();
   }

   @Test
   public void testNoDirectByteBufLeaksOnLargeMessageCompression() throws Exception {
      Assume.assumeThat(PlatformDependent.usedDirectMemory(), not(equalTo(Long.valueOf(-1))));
      final int messageSize = (int) (3.5 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);

      ActiveMQServer server = createServer(true, isNetty());

      server.start();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, false, false));

      session.createQueue(new QueueConfiguration(ADDRESS).setAddress(ADDRESS).setDurable(false).setTemporary(true));

      ClientProducer producer = session.createProducer(ADDRESS);

      Message clientFile = createLargeClientMessageStreaming(session, messageSize, true);

      producer.send(clientFile);

      session.commit();

      session.start();

      ClientConsumer consumer = session.createConsumer(ADDRESS);
      final long usedDirectMemoryBeforeReceive = PlatformDependent.usedDirectMemory();
      ClientMessage msg1 = consumer.receive(1000);
      Assert.assertNotNull(msg1);
      final long usedDirectMemoryAfterReceive = PlatformDependent.usedDirectMemory();
      Assert.assertEquals("large message compression is leaking some Netty direct ByteBuff",
                          usedDirectMemoryBeforeReceive, usedDirectMemoryAfterReceive);
      msg1.acknowledge();
      session.commit();

      consumer.close();

      session.close();
   }

   @Test
   public void testLargeMessageCompression() throws Exception {
      final int messageSize = (int) (3.5 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);

      ActiveMQServer server = createServer(true, isNetty());

      server.start();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, false, false));

      session.createQueue(new QueueConfiguration(ADDRESS).setAddress(ADDRESS).setDurable(false).setTemporary(true));

      ClientProducer producer = session.createProducer(ADDRESS);

      Message clientFile = createLargeClientMessageStreaming(session, messageSize, true);

      producer.send(clientFile);

      session.commit();

      session.start();

      ClientConsumer consumer = session.createConsumer(ADDRESS);
      ClientMessage msg1 = consumer.receive(1000);
      Assert.assertNotNull(msg1);

      for (int i = 0; i < messageSize; i++) {
         byte b = msg1.getBodyBuffer().readByte();
         assertEquals("position = " + i, getSamplebyte(i), b);
      }

      msg1.acknowledge();
      session.commit();

      consumer.close();

      session.close();

      validateNoFilesOnLargeDir();
   }

   @Test
   public void testLargeMessageCompression2() throws Exception {
      final int messageSize = (int) (3.5 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);

      ActiveMQServer server = createServer(true, isNetty());

      server.start();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, false, false));

      session.createQueue(new QueueConfiguration(ADDRESS).setAddress(ADDRESS).setDurable(false).setTemporary(true));

      ClientProducer producer = session.createProducer(ADDRESS);

      Message clientFile = createLargeClientMessageStreaming(session, messageSize, true);

      producer.send(clientFile);

      session.commit();

      session.start();

      ClientConsumer consumer = session.createConsumer(ADDRESS);
      ClientMessage msg1 = consumer.receive(1000);
      Assert.assertNotNull(msg1);

      String testDir = getTestDir();
      File testFile = new File(testDir, "async_large_message");
      FileOutputStream output = new FileOutputStream(testFile);

      msg1.setOutputStream(output);

      msg1.waitOutputStreamCompletion(0);

      msg1.acknowledge();

      output.close();

      session.commit();

      consumer.close();

      session.close();

      //verify
      FileInputStream input = new FileInputStream(testFile);
      for (int i = 0; i < messageSize; i++) {
         byte b = (byte) input.read();
         assertEquals("position = " + i, getSamplebyte(i), b);
      }
      input.close();
      testFile.delete();
      validateNoFilesOnLargeDir();

   }

   @Test
   public void testLargeMessageCompression3() throws Exception {
      final int messageSize = (int) (3.5 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);

      ActiveMQServer server = createServer(true, isNetty());

      server.start();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, false, false));

      session.createQueue(new QueueConfiguration(ADDRESS).setAddress(ADDRESS).setDurable(false).setTemporary(true));

      ClientProducer producer = session.createProducer(ADDRESS);

      Message clientFile = createLargeClientMessageStreaming(session, messageSize, true);

      producer.send(clientFile);

      session.commit();

      session.start();

      ClientConsumer consumer = session.createConsumer(ADDRESS);
      ClientMessage msg1 = consumer.receive(1000);
      Assert.assertNotNull(msg1);

      String testDir = getTestDir();
      File testFile = new File(testDir, "async_large_message");
      FileOutputStream output = new FileOutputStream(testFile);

      msg1.saveToOutputStream(output);

      msg1.acknowledge();

      output.close();

      session.commit();

      consumer.close();

      session.close();

      //verify
      FileInputStream input = new FileInputStream(testFile);
      for (int i = 0; i < messageSize; i++) {
         byte b = (byte) input.read();
         assertEquals("position = " + i, getSamplebyte(i), b);
      }
      input.close();

      testFile.delete();
      validateNoFilesOnLargeDir();
   }

   // This test will send 1 Gig of spaces. There shouldn't be enough memory to uncompress the file in memory
   // but this will make sure we can work through compressed channels on saving it to stream
   @Test
   public void testHugeStreamingSpacesCompressed() throws Exception {
      final long messageSize = 1024L * 1024L;

      ActiveMQServer server = createServer(true, isNetty());

      server.start();

      // big enough to hold the whole message compressed on a single message (about 1M on our tests)
      locator.setMinLargeMessageSize(100 * 1024 * 1024);

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, false, false));

      session.createQueue(new QueueConfiguration(ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);

      ClientMessage clientMessage = session.createMessage(true);

      clientMessage.setBodyInputStream(new InputStream() {
         private long count;

         private boolean closed = false;

         @Override
         public void close() throws IOException {
            super.close();
            closed = true;
         }

         @Override
         public int read() throws IOException {
            if (closed) {
               throw new IOException("Stream was closed");
            }

            if (count++ < messageSize) {
               return ' ';
            } else {
               return -1;
            }
         }
      });

      producer.send(clientMessage);

      session.commit();

      // this is to make sure the message was sent as a regular message (not taking a file on server)
      validateNoFilesOnLargeDir();

      session.start();

      ClientConsumer consumer = session.createConsumer(ADDRESS);
      ClientMessage msg1 = consumer.receive(1000);
      Assert.assertNotNull(msg1);

      final AtomicLong numberOfSpaces = new AtomicLong();

      msg1.saveToOutputStream(new OutputStream() {
         @Override
         public void write(int content) {
            if (content == ' ') {
               numberOfSpaces.incrementAndGet();
            }
         }
      });

      assertEquals(messageSize, numberOfSpaces.get());

      msg1.acknowledge();

      session.commit();

      session.close();
   }

   @Test
   public void testLargeMessageCompressionRestartAndCheckSize() throws Exception {
      final int messageSize = 1024 * 1024;

      ActiveMQServer server = createServer(true, isNetty());

      server.start();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, false, false));

      session.createQueue(new QueueConfiguration(ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);

      byte[] msgs = new byte[1024 * 1024];
      for (int i = 0; i < msgs.length; i++) {
         msgs[i] = RandomUtil.randomByte();
      }

      Message clientFile = createLargeClientMessage(session, msgs, true);

      producer.send(clientFile);

      session.commit();

      session.close();

      sf.close();

      locator.close();

      server.stop();

      server = createServer(true, isNetty());

      server.start();

      locator = createFactory(isNetty());

      sf = createSessionFactory(locator);

      session = sf.createSession();

      session.start();

      ClientConsumer consumer = session.createConsumer(ADDRESS);
      ClientMessage msg1 = consumer.receive(1000);
      Assert.assertNotNull(msg1);

      assertEquals(messageSize, msg1.getBodySize());

      String testDir = getTestDir();
      File testFile = new File(testDir, "async_large_message");
      FileOutputStream output = new FileOutputStream(testFile);

      msg1.saveToOutputStream(output);

      msg1.acknowledge();

      session.commit();

      consumer.close();

      session.close();

      //verify
      FileInputStream input = new FileInputStream(testFile);
      for (int i = 0; i < messageSize; i++) {
         byte b = (byte) input.read();
         assertEquals("position = " + i, msgs[i], b);
      }
      input.close();

      testFile.delete();
      validateNoFilesOnLargeDir();
   }

   @Override
   @Test
   public void testSendServerMessage() throws Exception {
      // doesn't make sense as compressed
   }
}

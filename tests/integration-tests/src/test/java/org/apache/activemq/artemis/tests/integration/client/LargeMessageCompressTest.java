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

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.management.openmbean.CompositeData;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.Deflater;

import io.netty.util.internal.PlatformDependent;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.core.config.StoreConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.integration.largemessage.LargeMessageTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

//Parameters set in superclass
@ExtendWith(ParameterizedTestExtension.class)
public class LargeMessageCompressTest extends LargeMessageTestBase {


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

   @TestTemplate
   public void testLargeMessageCompressionNotCompressedAndBrowsed() throws Exception {
      final int messageSize = (int) (3.5 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);

      ActiveMQServer server = createServer(true, isNetty());

      server.start();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, false, false));

      session.createQueue(QueueConfiguration.of(ADDRESS).setAddress(ADDRESS).setDurable(false).setTemporary(true));

      ClientProducer producer = session.createProducer(ADDRESS);

      Message clientFile = createLargeClientMessageStreaming(session, messageSize, true);

      clientFile.setType(Message.TEXT_TYPE);

      producer.send(clientFile);

      session.commit();

      session.close();

      QueueControl queueControl = server.getManagementService().getQueueControl(ADDRESS.toString());

      CompositeData[] browse = queueControl.browse();

      assertNotNull(browse);

      assertEquals(1, browse.length);

      assertEquals("[compressed]", browse[0].get("text"));

      //clean up
      session = addClientSession(sf.createSession(false, false, false));

      session.start();

      ClientConsumer consumer = session.createConsumer(ADDRESS);
      ClientMessage msg1 = consumer.receive(1000);
      assertNotNull(msg1);

      for (int i = 0; i < messageSize; i++) {
         byte b = msg1.getBodyBuffer().readByte();
         assertEquals(getSamplebyte(i), b, "position = " + i);
      }

      msg1.acknowledge();
      session.commit();

      consumer.close();

      session.close();

      validateNoFilesOnLargeDir();
   }

   @TestTemplate
   public void testNoDirectByteBufLeaksOnLargeMessageCompression() throws Exception {
      assumeTrue(PlatformDependent.usedDirectMemory() != -1);
      final int messageSize = (int) (3.5 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);

      ActiveMQServer server = createServer(true, isNetty());

      server.start();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, false, false));

      session.createQueue(QueueConfiguration.of(ADDRESS).setAddress(ADDRESS).setDurable(false).setTemporary(true));

      ClientProducer producer = session.createProducer(ADDRESS);

      Message clientFile = createLargeClientMessageStreaming(session, messageSize, true);

      producer.send(clientFile);

      session.commit();

      session.start();

      ClientConsumer consumer = session.createConsumer(ADDRESS);
      final long usedDirectMemoryBeforeReceive = PlatformDependent.usedDirectMemory();
      ClientMessage msg1 = consumer.receive(1000);
      assertNotNull(msg1);
      final long usedDirectMemoryAfterReceive = PlatformDependent.usedDirectMemory();
      assertEquals(usedDirectMemoryBeforeReceive, usedDirectMemoryAfterReceive, "large message compression is leaking some Netty direct ByteBuff");
      msg1.acknowledge();
      session.commit();

      consumer.close();

      session.close();
   }

   @TestTemplate
   public void testLargeMessageCompression() throws Exception {
      final int messageSize = (int) (3.5 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);

      ActiveMQServer server = createServer(true, isNetty());

      server.start();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, false, false));

      session.createQueue(QueueConfiguration.of(ADDRESS).setAddress(ADDRESS).setDurable(false).setTemporary(true));

      ClientProducer producer = session.createProducer(ADDRESS);

      Message clientFile = createLargeClientMessageStreaming(session, messageSize, true);

      producer.send(clientFile);

      session.commit();

      session.start();

      ClientConsumer consumer = session.createConsumer(ADDRESS);
      ClientMessage msg1 = consumer.receive(1000);
      assertNotNull(msg1);

      for (int i = 0; i < messageSize; i++) {
         byte b = msg1.getBodyBuffer().readByte();
         assertEquals(getSamplebyte(i), b, "position = " + i);
      }

      msg1.acknowledge();
      session.commit();

      consumer.close();

      session.close();

      validateNoFilesOnLargeDir();
   }

   @TestTemplate
   public void testLargeMessageCompression2() throws Exception {
      final int messageSize = (int) (3.5 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);

      ActiveMQServer server = createServer(true, isNetty());

      server.start();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, false, false));

      session.createQueue(QueueConfiguration.of(ADDRESS).setAddress(ADDRESS).setDurable(false).setTemporary(true));

      ClientProducer producer = session.createProducer(ADDRESS);

      Message clientFile = createLargeClientMessageStreaming(session, messageSize, true);

      producer.send(clientFile);

      session.commit();

      session.start();

      ClientConsumer consumer = session.createConsumer(ADDRESS);
      ClientMessage msg1 = consumer.receive(1000);
      assertNotNull(msg1);

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
         assertEquals(getSamplebyte(i), b, "position = " + i);
      }
      input.close();
      testFile.delete();
      validateNoFilesOnLargeDir();

   }

   @TestTemplate
   public void testLargeMessageCompression3() throws Exception {
      final int messageSize = (int) (3.5 * ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE);

      ActiveMQServer server = createServer(true, isNetty());

      server.start();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, false, false));

      session.createQueue(QueueConfiguration.of(ADDRESS).setAddress(ADDRESS).setDurable(false).setTemporary(true));

      ClientProducer producer = session.createProducer(ADDRESS);

      Message clientFile = createLargeClientMessageStreaming(session, messageSize, true);

      producer.send(clientFile);

      session.commit();

      session.start();

      ClientConsumer consumer = session.createConsumer(ADDRESS);
      ClientMessage msg1 = consumer.receive(1000);
      assertNotNull(msg1);

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
         assertEquals(getSamplebyte(i), b, "position = " + i);
      }
      input.close();

      testFile.delete();
      validateNoFilesOnLargeDir();
   }

   // This test will send 1 Gig of spaces. There shouldn't be enough memory to uncompress the file in memory
   // but this will make sure we can work through compressed channels on saving it to stream
   @TestTemplate
   public void testHugeStreamingSpacesCompressed() throws Exception {
      final long messageSize = 1024L * 1024L;

      ActiveMQServer server = createServer(true, isNetty());

      server.start();

      // big enough to hold the whole message compressed on a single message (about 1M on our tests)
      locator.setMinLargeMessageSize(100 * 1024 * 1024);

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, false, false));

      session.createQueue(QueueConfiguration.of(ADDRESS));

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
      assertNotNull(msg1);

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

   @TestTemplate
   public void testLargeMessageCompressionRestartAndCheckSize() throws Exception {
      final int messageSize = 1024 * 1024;

      ActiveMQServer server = createServer(true, isNetty());

      server.start();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = addClientSession(sf.createSession(false, false, false));

      session.createQueue(QueueConfiguration.of(ADDRESS));

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
      assertNotNull(msg1);

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
         assertEquals(msgs[i], b, "position = " + i);
      }
      input.close();

      testFile.delete();
      validateNoFilesOnLargeDir();
   }

   @TestTemplate
   public void testPreviouslyCompressedMessageCleanup() throws Exception {
      final int messageSize = 1024 * 1024;

      byte[] payload = new byte[messageSize];
      byte[] response = new byte[messageSize];

      ActiveMQServer server = createServer(true, isNetty());
      server.start();

      server.createQueue(QueueConfiguration.of(ADDRESS).setRoutingType(RoutingType.ANYCAST));

      for (int i = 0; i < payload.length; i++) {
         payload[i] = RandomUtil.randomByte();
      }

      try (ClientSessionFactory sf = locator.createSessionFactory();
           ClientSession session = sf.createSession(true, true);
           ClientProducer producer = session.createProducer(ADDRESS)) {

         ClientMessage message = session.createMessage(true);
         message.getBodyBuffer().writeBytes(payload);
         assertNull(message.getAnnotation(Message.HDR_LARGE_COMPRESSED));

         producer.send(message);
      }

      ServerLocator locator2 = createFactory(isNetty());
      locator2.setCompressLargeMessage(false);
      locator2.setMinLargeMessageSize(1024);

      try (ClientSessionFactory sf = locator2.createSessionFactory();
           ClientSession session = sf.createSession(true, true);
           ClientConsumer consumer = session.createConsumer(ADDRESS);
           ClientProducer producer = session.createProducer(ADDRESS)) {

         ClientMessage message = session.createMessage(true);
         ICoreMessage serverMessage = server.locateQueue(ADDRESS).browserIterator().next().getMessage().copy().toCore();

         message.moveHeadersAndProperties(serverMessage);
         message.getBodyBuffer().writeBytes(serverMessage.getReadOnlyBodyBuffer(), serverMessage.getBodyBufferSize());

         assertTrue(message.getBooleanProperty(Message.HDR_LARGE_COMPRESSED));

         producer.send(message);
         session.start();

         for (int i = 0; i < 2; i++) {
            message = consumer.receive(2000);
            assertNotNull(message);

            message.getBodyBuffer().readBytes(response);
            assertArrayEquals(payload, response);
            message.acknowledge();
         }
      }

      locator2.close();
   }

   @TestTemplate
   public void testCompressedMessageRouting() throws Exception {
      SimpleString DATA = SimpleString.of(RandomUtil.randomAlphaNumericString(110 * 1024));

      ActiveMQServer server = createServer(true, isNetty());
      server.start();

      server.createQueue(QueueConfiguration.of(ADDRESS).setRoutingType(RoutingType.ANYCAST));

      locator.setAckBatchSize(0);

      try (ServerLocator locator2 = createFactory(isNetty()); ServerLocator locator3 = createFactory(isNetty())) {
         locator2.setMinLargeMessageSize(10240);
         //Any sufficiently large value here causes a "java.lang.NegativeArraySizeException"
         locator3.setMinLargeMessageSize(1024000);

         ClientSessionFactory sf = locator.createSessionFactory();
         ClientSession session = sf.createSession(true, true);
         ClientProducer producer = session.createProducer(ADDRESS);
         ClientConsumer consumer = session.createConsumer(ADDRESS);

         ClientMessage message = session.createMessage(true);
         message.getBodyBuffer().writeNullableSimpleString(DATA);
         producer.send(message);

         session.start();
         message = consumer.receive(2000);
         assertNotNull(message);
         assertTrue(message.getBooleanProperty(Message.HDR_LARGE_COMPRESSED));
         message.checkCompletion();
         message.acknowledge();

         ClientSessionFactory sf2 = locator2.createSessionFactory();
         ClientSessionFactory sf3 = locator3.createSessionFactory();
         ClientSession session2 = sf2.createSession(true, true);
         ClientSession session3 = sf3.createSession(true, true);
         ClientProducer producer2 = session2.createProducer(ADDRESS);
         ClientProducer producer3 = session3.createProducer(ADDRESS);
         ClientMessage receivedMessage;

         //Notice the _AMQ_LARGE_SIZE value changing part way through
         for (int i = 0; i < 3; i++) {
            producer.send(message);
            producer2.send(message);
            producer3.send(message);
         }

         for (int i = 0; i < 9; i++) {
            receivedMessage = consumer.receive(2000);
            assertNotNull(receivedMessage);
            assertEquals(DATA, receivedMessage.getBodyBuffer().readNullableSimpleString());
            receivedMessage.acknowledge();
         }

         consumer.close();
      }

   }

   @TestTemplate
   public void testLargeMessageCompressionLevel() throws Exception {
      SimpleString DATA = SimpleString.of(RandomUtil.randomAlphaNumericString(1024 * 1024));

      SimpleString address1 = SimpleString.of("address1");
      SimpleString address2 = SimpleString.of("address2");
      SimpleString address3 = SimpleString.of("address3");

      ActiveMQServer server = createServer(true, true);
      server.start();

      ServerLocator locator1 = ActiveMQClient.createServerLocator("tcp://localhost:61616?compressionLevel=1");
      ServerLocator locator2 = ActiveMQClient.createServerLocator("vm://0?compressionLevel=5");
      ServerLocator locator3 = ActiveMQClient.createServerLocator("vm://0");
      locator1.setCompressLargeMessage(true);
      locator2.setCompressLargeMessage(true);
      locator3.setCompressLargeMessage(true);
      locator3.setCompressionLevel(9);

      ClientSessionFactory sf1 = locator1.createSessionFactory();
      ClientSessionFactory sf2 = locator2.createSessionFactory();
      ClientSessionFactory sf3 = locator3.createSessionFactory();
      ClientSession session1 = sf1.createSession(false, true, true);
      ClientSession session2 = sf2.createSession(false, true, true);
      ClientSession session3 = sf3.createSession(false, true, true);

      ClientProducer producer1 = session1.createProducer(address1);
      ClientProducer producer2 = session2.createProducer(address2);
      ClientProducer producer3 = session3.createProducer(address3);

      session1.createQueue(QueueConfiguration.of(address1));
      session2.createQueue(QueueConfiguration.of(address2));
      session3.createQueue(QueueConfiguration.of(address3));

      ClientMessage message1 = session1.createMessage(true);
      ClientMessage message2 = session2.createMessage(true);
      ClientMessage message3 = session3.createMessage(true);
      message1.getBodyBuffer().writeNullableSimpleString(DATA);
      message2.getBodyBuffer().writeNullableSimpleString(DATA);
      message3.getBodyBuffer().writeNullableSimpleString(DATA);

      producer1.send(message1);
      producer2.send(message2);
      producer3.send(message3);

      QueueControl queueControl1 = server.getManagementService().getQueueControl(address1.toString());
      QueueControl queueControl2 = server.getManagementService().getQueueControl(address2.toString());
      QueueControl queueControl3 = server.getManagementService().getQueueControl(address3.toString());

      assertEquals(1, queueControl1.getMessageCount());
      assertEquals(1, queueControl2.getMessageCount());
      assertEquals(1, queueControl3.getMessageCount());

      assertTrue(message1.getPersistentSize() > queueControl1.getPersistentSize());
      assertTrue(queueControl1.getPersistentSize() > queueControl2.getPersistentSize());
      assertTrue(queueControl2.getPersistentSize() > queueControl3.getPersistentSize());

      ClientConsumer consumer1 = session1.createConsumer(address1);
      ClientConsumer consumer2 = session2.createConsumer(address2);
      ClientConsumer consumer3 = session3.createConsumer(address3);
      session1.start();
      session2.start();
      session3.start();

      ClientMessage message;
      message = consumer1.receive(2000);
      assertNotNull(message);
      assertEquals(DATA, message.getBodyBuffer().readNullableSimpleString());
      message.acknowledge();

      message = consumer2.receive(2000);
      assertNotNull(message);
      assertEquals(DATA, message.getBodyBuffer().readNullableSimpleString());
      message.acknowledge();

      message = consumer3.receive(2000);
      assertNotNull(message);
      assertEquals(DATA, message.getBodyBuffer().readNullableSimpleString());
      message.acknowledge();

      sf1.close();
      sf2.close();
      sf3.close();
      locator1.close();
      locator2.close();
      locator3.close();

   }

   // https://issues.apache.org/jira/projects/ARTEMIS/issues/ARTEMIS-3751
   @TestTemplate
   public void testOverrideSize() throws Exception {
      ActiveMQServer server = createServer(true, true);

      server.start();

      ConnectionFactory cf = new ActiveMQConnectionFactory("tcp://localhost:61616?minLargeMessageSize=" + (200 * 1024) + "&compressLargeMessage=true");
      Connection connection = cf.createConnection();

      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

      connection.start();
      Destination queue = session.createQueue("testQueue");

      MessageProducer producer = session.createProducer(queue);
      MessageConsumer consumer = session.createConsumer(queue);

      // What we want is to get a message witch size is larger than client's minLargeMessageSize (200 kibs here) while compressed the size must be lower than that
      // and at same time it must be greater than server minLargeMessageSize (which is 100 kibs by default)
      byte[] data = new byte[1024 * 300];
      new DeflateGenerator(new Random(42), 2.0, 2.0).generate(data, 2);

      assertCompressionSize(data, 100 * 1024, 200 * 1024);

      BytesMessage outMessage = session.createBytesMessage();
      outMessage.writeBytes(data);

      producer.send(outMessage);

      javax.jms.Message inMessage = consumer.receive(1000);

      assertEqualsByteArrays(data, inMessage.getBody(byte[].class));
   }

   private void assertCompressionSize(byte[] data, int min, int max) {
      byte[] output = new byte[data.length];

      Deflater deflater = new Deflater();
      deflater.setInput(data);
      deflater.finish();
      int compressed = deflater.deflate(output);
      deflater.end();

      assertTrue(compressed > min);
      assertTrue(compressed < max);
   }

   /**
    * Generate compressible data.
    * <p>
    * Based on "SDGen: Mimicking Datasets for Content Generation in Storage Benchmarks" by Raúl Gracia-Tinedo et al.
    * (https://www.usenix.org/node/188461) and https://github.com/jibsen/lzdatagen
    */
   public static class DeflateGenerator {

      private static final int MIN_LENGTH = 3;
      private static final int MAX_LENGTH = 258;
      private static final int NUM_LENGTH = MAX_LENGTH - MIN_LENGTH;

      private static final int LENGTH_PER_CHUNK = 512;

      private final Random rnd;

      private final double dataExp;
      private final double lengthExp;

      public DeflateGenerator(Random rnd, double dataExp, double lengthExp) {
         this.rnd = rnd;
         this.dataExp = dataExp;
         this.lengthExp = lengthExp;
      }

      private void nextBytes(byte[] buffer, int size) {
         for (int i = 0; i < size; i++) {
            buffer[i] = ((byte) ((double) 256 * Math.pow(rnd.nextDouble(), dataExp)));
         }
      }

      private byte[] nextBytes(int size) {
         byte[] buffer = new byte[size];
         nextBytes(buffer, size);
         return buffer;
      }

      private void nextLengthFrequencies(int[] frequencies) {
         Arrays.fill(frequencies, 0);

         for (int i = 0; i < LENGTH_PER_CHUNK; i++) {
            int length = (int) ((double) frequencies.length * Math.pow(rnd.nextDouble(), lengthExp));

            frequencies[length]++;
         }
      }

      public void generate(byte[] result, double ratio) {
         ByteBuffer generated = generate(result.length, ratio);
         generated.get(result);
      }

      public ByteBuffer generate(int size, double ratio) {
         ByteBuffer result = ByteBuffer.allocate(size);

         byte[] buffer = new byte[MAX_LENGTH];
         int[] frequencies = new int[NUM_LENGTH];

         int length = 0;
         int i = 0;
         boolean repeat = false;

         while (i < size) {
            while (frequencies[length] == 0) {
               if (length == 0) {
                  nextBytes(buffer, MAX_LENGTH);
                  nextLengthFrequencies(frequencies);

                  length = NUM_LENGTH;
               }

               length--;
            }

            int len = length + MIN_LENGTH;
            frequencies[length]--;

            if (len > size - i) {
               len = size - i;
            }

            if (rnd.nextDouble() < 1.0 / ratio) {
               result.put(nextBytes(len));
               repeat = false;
            } else {
               if (repeat) {
                  result.put(nextBytes(1));
                  i++;
               }

               result.put(buffer, 0, len);

               repeat = true;
            }

            i += len;
         }

         return result.flip();
      }
   }


}

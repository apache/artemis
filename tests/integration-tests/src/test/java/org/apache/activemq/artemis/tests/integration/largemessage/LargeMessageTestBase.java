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
package org.apache.activemq.artemis.tests.integration.largemessage;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.StoreConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.DataConstants;
import org.apache.activemq.artemis.utils.DeflaterReader;
import org.jboss.logging.Logger;
import org.junit.Assert;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public abstract class LargeMessageTestBase extends ActiveMQTestBase {


   private static final Logger log = Logger.getLogger(LargeMessageTestBase.class);

   protected final SimpleString ADDRESS = new SimpleString("SimpleAddress");






   protected StoreConfiguration.StoreType storeType;

   public LargeMessageTestBase(StoreConfiguration.StoreType storeType) {
      this.storeType = storeType;
   }

   @Override
   public void tearDown() throws Exception {
      super.tearDown();
      if (storeType == StoreConfiguration.StoreType.DATABASE) {
         destroyTables(Arrays.asList("BINDINGS", "LARGE_MESSAGE", "MESSAGE", "NODE_MANAGER_STORE"));
      }
   }

   @Parameterized.Parameters(name = "storeType={0}")
   public static Collection<Object[]> data() {
      Object[][] params = new Object[][]{{StoreConfiguration.StoreType.FILE}, {StoreConfiguration.StoreType.DATABASE}};
      return Arrays.asList(params);
   }

   protected void testChunks(final boolean isXA,
                             final boolean restartOnXA,
                             final boolean rollbackFirstSend,
                             final boolean useStreamOnConsume,
                             final boolean realFiles,
                             final boolean preAck,
                             final boolean sendingBlocking,
                             final boolean testBrowser,
                             final boolean useMessageConsumer,
                             final int numberOfMessages,
                             final long numberOfBytes,
                             final int waitOnConsumer,
                             final long delayDelivery) throws Exception {
      testChunks(isXA, restartOnXA, rollbackFirstSend, useStreamOnConsume, realFiles, preAck, sendingBlocking, testBrowser, useMessageConsumer, numberOfMessages, numberOfBytes, waitOnConsumer, delayDelivery, -1, 10 * 1024);
   }

   protected void testChunks(final boolean isXA,
                             final boolean restartOnXA,
                             final boolean rollbackFirstSend,
                             final boolean useStreamOnConsume,
                             final boolean realFiles,
                             final boolean preAck,
                             final boolean sendingBlocking,
                             final boolean testBrowser,
                             final boolean useMessageConsumer,
                             final int numberOfMessages,
                             final long numberOfBytes,
                             final int waitOnConsumer,
                             final long delayDelivery,
                             final int producerWindow,
                             final int minSize) throws Exception {
      clearDataRecreateServerDirs();

      Configuration configuration;
      if (storeType == StoreConfiguration.StoreType.DATABASE) {
         configuration = createDefaultJDBCConfig(true);
      } else {
         configuration = createDefaultConfig(false);
      }

      ActiveMQServer server = createServer(realFiles, configuration);
      server.start();

      ServerLocator locator = createInVMNonHALocator();
      try {

         if (sendingBlocking) {
            locator.setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setBlockOnAcknowledge(true);
         }

         if (producerWindow > 0) {
            locator.setConfirmationWindowSize(producerWindow);
         }

         locator.setMinLargeMessageSize(minSize);

         ClientSessionFactory sf = locator.createSessionFactory();

         ClientSession session;

         Xid xid = null;
         session = sf.createSession(null, null, isXA, false, false, preAck, 0);

         if (isXA) {
            xid = newXID();
            session.start(xid, XAResource.TMNOFLAGS);
         }

         session.createQueue(new QueueConfiguration(ADDRESS));

         ClientProducer producer = session.createProducer(ADDRESS);

         if (rollbackFirstSend) {
            sendMessages(numberOfMessages, numberOfBytes, delayDelivery, session, producer);

            if (isXA) {
               session.end(xid, XAResource.TMSUCCESS);
               session.prepare(xid);

               session.close();

               if (realFiles && restartOnXA) {
                  server.stop();
                  server.start();
                  sf = locator.createSessionFactory();
               }

               session = sf.createSession(null, null, isXA, false, false, preAck, 0);

               Xid[] xids = session.recover(XAResource.TMSTARTRSCAN);
               Assert.assertEquals(1, xids.length);
               Assert.assertEquals(xid, xids[0]);

               session.rollback(xid);
               producer = session.createProducer(ADDRESS);
               xid = newXID();
               session.start(xid, XAResource.TMNOFLAGS);
            } else {
               session.rollback();
            }

            validateNoFilesOnLargeDir();
         }

         sendMessages(numberOfMessages, numberOfBytes, delayDelivery, session, producer);

         if (isXA) {
            session.end(xid, XAResource.TMSUCCESS);
            session.prepare(xid);

            session.close();

            if (realFiles && restartOnXA) {
               server.stop();
               server.start();
               //we need to recreate sf's
               sf = locator.createSessionFactory();
            }

            session = sf.createSession(null, null, isXA, false, false, preAck, 0);

            Xid[] xids = session.recover(XAResource.TMSTARTRSCAN);
            Assert.assertEquals(1, xids.length);
            Assert.assertEquals(xid, xids[0]);

            producer = session.createProducer(ADDRESS);

            session.commit(xid, false);
            xid = newXID();
            session.start(xid, XAResource.TMNOFLAGS);
         } else {
            session.commit();
         }

         session.close();

         if (realFiles) {
            server.stop();

            server = createServer(realFiles, configuration);
            server.start();

            sf = locator.createSessionFactory();
         }

         session = sf.createSession(null, null, isXA, false, false, preAck, 0);

         if (isXA) {
            xid = newXID();
            session.start(xid, XAResource.TMNOFLAGS);
         }

         ClientConsumer consumer = null;

         for (int iteration = testBrowser ? 0 : 1; iteration < 2; iteration++) {
            session.stop();

            // first time with a browser
            consumer = session.createConsumer(ADDRESS, null, iteration == 0);

            if (useMessageConsumer) {
               final CountDownLatch latchDone = new CountDownLatch(numberOfMessages);
               final AtomicInteger errors = new AtomicInteger(0);

               MessageHandler handler = new MessageHandler() {
                  int msgCounter;

                  @Override
                  public void onMessage(final ClientMessage message) {
                     try {
                        if (delayDelivery > 0) {
                           long originalTime = (Long) message.getObjectProperty(new SimpleString("original-time"));
                           Assert.assertTrue(System.currentTimeMillis() - originalTime + "<" + delayDelivery, System.currentTimeMillis() - originalTime >= delayDelivery);
                        }

                        if (!preAck) {
                           message.acknowledge();
                        }

                        Assert.assertNotNull(message);

                        if (delayDelivery <= 0) {
                           // right now there is no guarantee of ordered delivered on multiple scheduledMessages with
                           // the same
                           // scheduled delivery time
                           Assert.assertEquals(msgCounter, ((Integer) message.getObjectProperty(new SimpleString("counter-message"))).intValue());
                        }

                        if (useStreamOnConsume) {
                           final AtomicLong bytesRead = new AtomicLong(0);
                           message.saveToOutputStream(new OutputStream() {

                              @Override
                              public void write(final byte[] b) throws IOException {
                                 if (b[0] == ActiveMQTestBase.getSamplebyte(bytesRead.get())) {
                                    bytesRead.addAndGet(b.length);
                                    LargeMessageTestBase.log.debug("Read position " + bytesRead.get() + " on consumer");
                                 } else {
                                    LargeMessageTestBase.log.warn("Received invalid packet at position " + bytesRead.get());
                                 }
                              }

                              @Override
                              public void write(final int b) throws IOException {
                                 if (b == ActiveMQTestBase.getSamplebyte(bytesRead.get())) {
                                    bytesRead.incrementAndGet();
                                 } else {
                                    LargeMessageTestBase.log.warn("byte not as expected!");
                                 }
                              }
                           });

                           Assert.assertEquals(numberOfBytes, bytesRead.get());
                        } else {

                           ActiveMQBuffer buffer = message.getBodyBuffer();
                           buffer.resetReaderIndex();
                           for (long b = 0; b < numberOfBytes; b++) {
                              if (b % (1024L * 1024L) == 0) {
                                 LargeMessageTestBase.log.debug("Read " + b + " bytes");
                              }

                              Assert.assertEquals(ActiveMQTestBase.getSamplebyte(b), buffer.readByte());
                           }

                           try {
                              buffer.readByte();
                              Assert.fail("Supposed to throw an exception");
                           } catch (Exception e) {
                           }
                        }
                     } catch (Throwable e) {
                        e.printStackTrace();
                        LargeMessageTestBase.log.warn("Got an error", e);
                        errors.incrementAndGet();
                     } finally {
                        latchDone.countDown();
                        msgCounter++;
                     }
                  }
               };

               session.start();

               consumer.setMessageHandler(handler);

               Assert.assertTrue(latchDone.await(waitOnConsumer, TimeUnit.MILLISECONDS));
               Assert.assertEquals(0, errors.get());
            } else {

               session.start();

               for (int i = 0; i < numberOfMessages; i++) {
                  ClientMessage message = consumer.receive(waitOnConsumer + delayDelivery);

                  Assert.assertNotNull(message);

                  if (delayDelivery > 0) {
                     long originalTime = (Long) message.getObjectProperty(new SimpleString("original-time"));
                     Assert.assertTrue(System.currentTimeMillis() - originalTime + "<" + delayDelivery, System.currentTimeMillis() - originalTime >= delayDelivery);
                  }

                  if (!preAck) {
                     message.acknowledge();
                  }

                  Assert.assertNotNull(message);

                  if (delayDelivery <= 0) {
                     // right now there is no guarantee of ordered delivered on multiple scheduledMessages with the same
                     // scheduled delivery time
                     Assert.assertEquals(i, ((Integer) message.getObjectProperty(new SimpleString("counter-message"))).intValue());
                  }

                  if (useStreamOnConsume) {
                     final AtomicLong bytesRead = new AtomicLong(0);
                     message.saveToOutputStream(new OutputStream() {

                        @Override
                        public void write(final byte[] b) throws IOException {
                           if (b.length > 0) {
                              if (b[0] == ActiveMQTestBase.getSamplebyte(bytesRead.get())) {
                                 bytesRead.addAndGet(b.length);
                              } else {
                                 LargeMessageTestBase.log.warn("Received invalid packet at position " + bytesRead.get());
                              }
                           }
                        }

                        @Override
                        public void write(final int b) throws IOException {
                           if (bytesRead.get() % (1024L * 1024L) == 0) {
                              LargeMessageTestBase.log.debug("Read " + bytesRead.get() + " bytes");
                           }
                           if (b == (byte) 'a') {
                              bytesRead.incrementAndGet();
                           } else {
                              LargeMessageTestBase.log.warn("byte not as expected!");
                           }
                        }
                     });

                     Assert.assertEquals(numberOfBytes, bytesRead.get());
                  } else {
                     ActiveMQBuffer buffer = message.getBodyBuffer();
                     buffer.resetReaderIndex();

                     for (long b = 0; b < numberOfBytes; b++) {
                        if (b % (1024L * 1024L) == 0L) {
                           LargeMessageTestBase.log.debug("Read " + b + " bytes");
                        }
                        Assert.assertEquals(ActiveMQTestBase.getSamplebyte(b), buffer.readByte());
                     }
                  }

               }

            }
            consumer.close();

            if (iteration == 0) {
               if (isXA) {
                  session.end(xid, XAResource.TMSUCCESS);
                  session.rollback(xid);
                  xid = newXID();
                  session.start(xid, XAResource.TMNOFLAGS);
               } else {
                  session.rollback();
               }
            } else {
               if (isXA) {
                  session.end(xid, XAResource.TMSUCCESS);
                  session.commit(xid, true);
                  xid = newXID();
                  session.start(xid, XAResource.TMNOFLAGS);
               } else {
                  session.commit();
               }
            }
         }

         session.close();

         Assert.assertEquals(0, ((Queue) server.getPostOffice().getBinding(ADDRESS).getBindable()).getDeliveringCount());
         Assert.assertEquals(0, ((Queue) server.getPostOffice().getBinding(ADDRESS).getBindable()).getMessageCount());

         validateNoFilesOnLargeDir();

      } catch (Throwable e) {
         e.printStackTrace();
         throw e;
      } finally {
         locator.close();
         try {
            server.stop();
         } catch (Throwable ignored) {
            ignored.printStackTrace();
         }
      }
   }

   /**
    * @param numberOfMessages
    * @param numberOfBytes
    * @param delayDelivery
    * @param session
    * @param producer
    * @throws Exception
    * @throws IOException
    * @throws ActiveMQException
    */
   private void sendMessages(final int numberOfMessages,
                             final long numberOfBytes,
                             final long delayDelivery,
                             final ClientSession session,
                             final ClientProducer producer) throws Exception {
      LargeMessageTestBase.log.debug("NumberOfBytes = " + numberOfBytes);
      for (int i = 0; i < numberOfMessages; i++) {
         ClientMessage message = session.createMessage(true);

         // If the test is using more than 1M, we will only use the Streaming, as it require too much memory from the
         // test
         if (numberOfBytes > 1024 * 1024 || i % 2 == 0) {
            LargeMessageTestBase.log.debug("Sending message (stream)" + i);
            message.setBodyInputStream(ActiveMQTestBase.createFakeLargeStream(numberOfBytes));
         } else {
            LargeMessageTestBase.log.debug("Sending message (array)" + i);
            byte[] bytes = new byte[(int) numberOfBytes];
            for (int j = 0; j < bytes.length; j++) {
               bytes[j] = ActiveMQTestBase.getSamplebyte(j);
            }
            message.getBodyBuffer().writeBytes(bytes);
         }
         message.putIntProperty(new SimpleString("counter-message"), i);
         if (delayDelivery > 0) {
            long time = System.currentTimeMillis();
            message.putLongProperty(new SimpleString("original-time"), time);
            message.putLongProperty(Message.HDR_SCHEDULED_DELIVERY_TIME, time + delayDelivery);

            producer.send(message);
         } else {
            producer.send(message);
         }
      }
   }

   protected ActiveMQBuffer createLargeBuffer(final int numberOfIntegers) {
      ActiveMQBuffer body = ActiveMQBuffers.fixedBuffer(DataConstants.SIZE_INT * numberOfIntegers);

      for (int i = 0; i < numberOfIntegers; i++) {
         body.writeInt(i);
      }

      return body;

   }

   protected ClientMessage createLargeClientMessageStreaming(final ClientSession session,
                                                             final int numberOfBytes) throws Exception {
      return createLargeClientMessageStreaming(session, numberOfBytes, true);
   }

   protected ClientMessage createLargeClientMessage(final ClientSession session,
                                                    final byte[] buffer,
                                                    final boolean durable) throws Exception {
      ClientMessage msgs = session.createMessage(durable);
      msgs.getBodyBuffer().writeBytes(buffer);
      return msgs;
   }

   protected ClientMessage createLargeClientMessageStreaming(final ClientSession session,
                                                             final long numberOfBytes,
                                                             final boolean persistent) throws Exception {

      ClientMessage clientMessage = session.createMessage(persistent);

      clientMessage.setBodyInputStream(ActiveMQTestBase.createFakeLargeStream(numberOfBytes));

      return clientMessage;
   }

   /**
    * @param session
    * @param queueToRead
    * @param numberOfBytes
    * @throws ActiveMQException
    * @throws IOException
    */
   protected void readMessage(final ClientSession session,
                              final SimpleString queueToRead,
                              final int numberOfBytes) throws ActiveMQException, IOException {
      session.start();

      ClientConsumer consumer = session.createConsumer(queueToRead);

      ClientMessage clientMessage = consumer.receive(5000);

      Assert.assertNotNull(clientMessage);

      clientMessage.acknowledge();

      session.commit();

      consumer.close();
   }

   protected OutputStream createFakeOutputStream() throws Exception {

      return new OutputStream() {
         private boolean closed = false;

         private int count;

         @Override
         public void close() throws IOException {
            super.close();
            closed = true;
         }

         @Override
         public void write(final int b) throws IOException {
            if (count++ % 1024 * 1024 == 0) {
               LargeMessageTestBase.log.debug("OutputStream received " + count + " bytes");
            }
            if (closed) {
               throw new IOException("Stream was closed");
            }
         }

      };

   }

   //depending on the value of regular argument, it can produce a text stream
   //whose size is above minLargeMessageSize but whose compressed size is either
   //below minLargeMessageSize (regular = true) or above it (regular = false)
   public static void adjustLargeCompression(boolean regular,
                                             TestLargeMessageInputStream stream,
                                             int step) throws IOException {
      int absoluteStep = Math.abs(step);
      while (true) {
         DeflaterReader compressor = new DeflaterReader(stream, new AtomicLong());
         try {
            byte[] buffer = new byte[1048 * 50];

            int totalCompressed = 0;
            int n = compressor.read(buffer);
            while (n != -1) {
               totalCompressed += n;
               n = compressor.read(buffer);
            }

            // check compressed size
            if (regular && (totalCompressed < stream.getMinLarge())) {
               // ok it can be sent as regular
               stream.resetAdjust(0);
               break;
            } else if ((!regular) && (totalCompressed > stream.getMinLarge())) {
               // now it cannot be sent as regular
               stream.resetAdjust(0);
               break;
            } else {
               stream.resetAdjust(regular ? -absoluteStep : absoluteStep);
            }
         } finally {
            compressor.close();
         }
      }
   }

   public static class TestLargeMessageInputStream extends InputStream {

      private final int minLarge;
      private int size;
      private int pos;
      private boolean random;

      public TestLargeMessageInputStream(int minLarge) {
         this(minLarge, false);
      }

      public TestLargeMessageInputStream(int minLarge, boolean random) {
         pos = 0;
         this.minLarge = minLarge;
         this.size = minLarge + 1024;
         this.random = random;
      }

      public int getChar(int index) {
         if (random) {
            Random r = new Random();
            return 'A' + r.nextInt(26);
         } else {
            return 'A' + index % 26;
         }
      }

      public void setSize(int size) {
         this.size = size;
      }

      public TestLargeMessageInputStream(TestLargeMessageInputStream other) {
         this.minLarge = other.minLarge;
         this.size = other.size;
         this.pos = other.pos;
      }

      public int getSize() {
         return size;
      }

      public int getMinLarge() {
         return this.minLarge;
      }

      @Override
      public int read() throws IOException {
         if (pos == size)
            return -1;
         pos++;

         return getChar(pos - 1);
      }

      public void resetAdjust(int step) {
         size += step;
         if (size <= minLarge) {
            throw new IllegalStateException("Couldn't adjust anymore, size smaller than minLarge " + minLarge);
         }
         pos = 0;
      }

      @Override
      public TestLargeMessageInputStream clone() {
         return new TestLargeMessageInputStream(this);
      }

      public char[] toArray() throws IOException {
         char[] result = new char[size];
         for (int i = 0; i < result.length; i++) {
            result[i] = (char) read();
         }
         return result;
      }
   }


}

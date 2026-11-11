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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.netty.buffer.ByteBuf;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQPropertyConversionException;
import org.apache.activemq.artemis.api.core.ICoreMessage;
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
import org.apache.activemq.artemis.core.client.impl.ClientSessionInternal;
import org.apache.activemq.artemis.core.persistence.CoreMessageObjectPools;
import org.apache.activemq.artemis.core.persistence.Persister;
import org.apache.activemq.artemis.core.protocol.core.impl.ActiveMQConsumerContext;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.spi.core.remoting.ConsumerContext;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AcknowledgeTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public final SimpleString addressA = SimpleString.of("addressA");

   public final SimpleString queueA = SimpleString.of("queueA");

   public final SimpleString queueB = SimpleString.of("queueB");

   public final SimpleString queueC = SimpleString.of("queueC");

   @Test
   public void testReceiveAckLastMessageOnly() throws Exception {
      ActiveMQServer server = createServer(false);
      server.start();
      ServerLocator locator = createInVMNonHALocator().setAckBatchSize(0).setBlockOnAcknowledge(true);
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession sendSession = cf.createSession(false, true, true);
      ClientSession session = cf.createSession(false, true, true);
      sendSession.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setDurable(false));
      ClientProducer cp = sendSession.createProducer(addressA);
      ClientConsumer cc = session.createConsumer(queueA);
      int numMessages = 100;
      for (int i = 0; i < numMessages; i++) {
         cp.send(sendSession.createMessage(false));
      }
      session.start();
      ClientMessage cm = null;
      for (int i = 0; i < numMessages; i++) {
         cm = cc.receive(5000);
         assertNotNull(cm);
      }
      cm.acknowledge();
      Queue q = (Queue) server.getPostOffice().getBinding(queueA).getBindable();

      assertEquals(0, q.getDeliveringCount());
      session.close();
      sendSession.close();
   }

   @Test
   public void testAsyncConsumerNoAck() throws Exception {
      ActiveMQServer server = createServer(false);

      server.start();
      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession sendSession = cf.createSession(false, true, true);
      ClientSession session = cf.createSession(false, true, true);
      sendSession.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setDurable(false));
      ClientProducer cp = sendSession.createProducer(addressA);
      ClientConsumer cc = session.createConsumer(queueA);
      int numMessages = 3;
      for (int i = 0; i < numMessages; i++) {
         cp.send(sendSession.createMessage(false));
      }

      Thread.sleep(500);
      logger.debug("woke up");

      final CountDownLatch latch = new CountDownLatch(numMessages);
      session.start();
      cc.setMessageHandler(message -> latch.countDown());
      assertTrue(latch.await(5, TimeUnit.SECONDS));
      Queue q = (Queue) server.getPostOffice().getBinding(queueA).getBindable();
      assertEquals(numMessages, q.getDeliveringCount());
      sendSession.close();
      session.close();
   }

   @Test
   public void testAsyncConsumerAck() throws Exception {
      ActiveMQServer server = createServer(false);
      server.start();
      ServerLocator locator = createInVMNonHALocator().setBlockOnAcknowledge(true).setAckBatchSize(0);
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession sendSession = cf.createSession(false, true, true);
      final ClientSession session = cf.createSession(false, true, true);
      sendSession.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setDurable(false));
      ClientProducer cp = sendSession.createProducer(addressA);
      ClientConsumer cc = session.createConsumer(queueA);
      int numMessages = 100;
      for (int i = 0; i < numMessages; i++) {
         cp.send(sendSession.createMessage(false));
      }
      final CountDownLatch latch = new CountDownLatch(numMessages);
      session.start();
      cc.setMessageHandler(message -> {
         try {
            message.acknowledge();
         } catch (ActiveMQException e) {
            try {
               session.close();
            } catch (ActiveMQException e1) {
               e1.printStackTrace();
            }
         }
         latch.countDown();
      });
      assertTrue(latch.await(5, TimeUnit.SECONDS));
      Queue q = (Queue) server.getPostOffice().getBinding(queueA).getBindable();
      assertEquals(0, q.getDeliveringCount());
      sendSession.close();
      session.close();
   }

   /**
    * This is validating a case where a consumer will try to ack a message right after failover, but the consumer at the
    * target server didn't receive the message yet. on that case the system should rollback any acks done and redeliver
    * any messages
    */
   @Test
   public void testInvalidACK() throws Exception {
      ActiveMQServer server = createServer(false);
      server.start();

      ServerLocator locator = createInVMNonHALocator().setAckBatchSize(0).setBlockOnAcknowledge(true);

      ClientSessionFactory cf = createSessionFactory(locator);

      int numMessages = 100;

      ClientSession sessionConsumer = cf.createSession(true, true, 0);

      sessionConsumer.start();

      sessionConsumer.createQueue(QueueConfiguration.of(queueA).setAddress(addressA));

      ClientConsumer consumer = sessionConsumer.createConsumer(queueA);

      // sending message
      {
         ClientSession sendSession = cf.createSession(false, true, true);

         ClientProducer cp = sendSession.createProducer(addressA);

         for (int i = 0; i < numMessages; i++) {
            ClientMessage msg = sendSession.createMessage(true);
            msg.putIntProperty("seq", i);
            cp.send(msg);
         }

         sendSession.close();
      }

      {

         ClientMessage msg = consumer.receive(5000);

         // need to way some time before all the possible references are sent to the consumer
         // as we need to guarantee the order on cancellation on this test
         Thread.sleep(1000);

         try {
            // pretending to be an unbehaved client doing an invalid ack right after failover
            ((ClientSessionInternal) sessionConsumer).acknowledge(new FakeConsumerWithID(0), new FakeMessageWithID(12343));
            fail("supposed to throw an exception here");
         } catch (Exception e) {
         }

         try {
            // pretending to be an unbehaved client doing an invalid ack right after failover
            ((ClientSessionInternal) sessionConsumer).acknowledge(new FakeConsumerWithID(3), new FakeMessageWithID(12343));
            fail("supposed to throw an exception here");
         } catch (Exception e) {
            e.printStackTrace();
         }

         consumer.close();

         consumer = sessionConsumer.createConsumer(queueA);

         for (int i = 0; i < numMessages; i++) {
            msg = consumer.receive(5000);
            assertNotNull(msg);
            assertEquals(i, msg.getIntProperty("seq").intValue());
            msg.acknowledge();
         }
      }
   }

   @Test
   public void testAsyncConsumerAckLastMessageOnly() throws Exception {
      ActiveMQServer server = createServer(false);
      server.start();
      ServerLocator locator = createInVMNonHALocator().setBlockOnAcknowledge(true).setAckBatchSize(0);
      ClientSessionFactory cf = createSessionFactory(locator);
      ClientSession sendSession = cf.createSession(false, true, true);
      final ClientSession session = cf.createSession(false, true, true);
      sendSession.createQueue(QueueConfiguration.of(queueA).setAddress(addressA).setDurable(false));
      ClientProducer cp = sendSession.createProducer(addressA);
      ClientConsumer cc = session.createConsumer(queueA);
      int numMessages = 100;
      for (int i = 0; i < numMessages; i++) {
         cp.send(sendSession.createMessage(false));
      }
      final CountDownLatch latch = new CountDownLatch(numMessages);
      session.start();
      cc.setMessageHandler(message -> {
         if (latch.getCount() == 1) {
            try {
               message.acknowledge();
            } catch (ActiveMQException e) {
               try {
                  session.close();
               } catch (ActiveMQException e1) {
                  e1.printStackTrace();
               }
            }
         }
         latch.countDown();
      });
      assertTrue(latch.await(5, TimeUnit.SECONDS));
      Queue q = (Queue) server.getPostOffice().getBinding(queueA).getBindable();
      assertEquals(0, q.getDeliveringCount());
      sendSession.close();
      session.close();
   }

   class FakeConsumerWithID implements ClientConsumer {

      final long id;

      FakeConsumerWithID(long id) {
         this.id = id;
      }

      @Override
      public ConsumerContext getConsumerContext() {
         return new ActiveMQConsumerContext(this.id);
      }

      @Override
      public ClientMessage receive() throws ActiveMQException {
         return null;
      }

      @Override
      public ClientMessage receive(long timeout) throws ActiveMQException {
         return null;
      }

      @Override
      public ClientMessage receiveImmediate() throws ActiveMQException {
         return null;
      }

      @Override
      public MessageHandler getMessageHandler() throws ActiveMQException {
         return null;
      }

      @Override
      public FakeConsumerWithID setMessageHandler(MessageHandler handler) throws ActiveMQException {
         return this;
      }

      @Override
      public void close() throws ActiveMQException {

      }

      @Override
      public boolean isClosed() {
         return false;
      }

      @Override
      public Exception getLastException() {
         return null;
      }
   }

   class FakeMessageWithID implements Message {

      final long id;

      @Override
      public String getProtocolName() {
         // should normally not be visible in GUI
         return getClass().getName();
      }

      @Override
      public void setPaged() {

      }

      @Override
      public boolean isPaged() {
         return false;
      }

      @Override
      public SimpleString getReplyTo() {
         return null;
      }

      @Override
      public Message setReplyTo(SimpleString address) {
         return null;
      }

      @Override
      public Object removeAnnotation(SimpleString key) {
         return null;
      }

      @Override
      public Object getAnnotation(SimpleString key) {
         return null;
      }

      @Override
      public int getPersistSize() {
         return 0;
      }

      @Override
      public int getDurableCount() {
         return 0;
      }

      @Override
      public void persist(ActiveMQBuffer targetRecord) {
      }

      @Override
      public Persister<Message> getPersister() {
         return null;
      }

      @Override
      public void reloadPersistence(ActiveMQBuffer record, CoreMessageObjectPools pools) {

      }

      @Override
      public Long getScheduledDeliveryTime() {
         return null;
      }

      @Override
      public ICoreMessage toCore() {
         return toCore(null);
      }

      @Override
      public ICoreMessage toCore(CoreMessageObjectPools coreMessageObjectPools) {
         return null;
      }

      @Override
      public void receiveBuffer(ByteBuf buffer) {

      }

      @Override
      public void sendBuffer(ByteBuf buffer, int count) {

      }
      @Override
      public Message setUserID(Object userID) {
         return null;
      }

      @Override
      public void messageChanged() {

      }

      @Override
      public Message copy() {
         return null;
      }

      @Override
      public Message copy(long newID) {
         return null;
      }

      @Override
      public Message setMessageID(long id) {
         return null;
      }

      @Override
      public int getRefCount() {
         return 0;
      }

      @Override
      public int getUsage() {
         return 0;
      }

      @Override
      public int usageUp() {
         return 0;
      }

      @Override
      public int usageDown() {
         return 0;
      }

      @Override
      public int refUp() {
         return 0;
      }

      @Override
      public int refDown() {
         return 0;
      }

      @Override
      public int durableUp() {
         return 0;
      }

      @Override
      public int durableDown() {
         return 0;
      }

      @Override
      public int getMemoryEstimate() {
         return 0;
      }

      FakeMessageWithID(final long id) {
         this.id = id;
      }

      @Override
      public long getMessageID() {
         return id;
      }

      @Override
      public UUID getUserID() {
         return null;
      }

      @Override
      public String getAddress() {
         return null;
      }

      @Override
      public SimpleString getAddressSimpleString() {
         return null;
      }

      @Override
      public Message setAddress(String address) {
         return null;
      }

      @Override
      public Message setAddress(SimpleString address) {
         return null;
      }

      @Override
      public boolean isDurable() {
         return false;
      }

      @Override
      public FakeMessageWithID setDurable(boolean durable) {
         return this;
      }

      @Override
      public long getExpiration() {
         return 0;
      }

      @Override
      public boolean isExpired() {
         return false;
      }

      @Override
      public FakeMessageWithID setExpiration(long expiration) {
         return this;
      }

      @Override
      public long getTimestamp() {
         return 0;
      }

      @Override
      public FakeMessageWithID setTimestamp(long timestamp) {
         return this;
      }

      @Override
      public byte getPriority() {
         return 0;
      }

      @Override
      public FakeMessageWithID setPriority(byte priority) {
         return this;
      }

      @Override
      public int getEncodeSize() {
         return 0;
      }

      @Override
      public boolean isLargeMessage() {
         return false;
      }

      @Override
      public Message putBooleanProperty(SimpleString key, boolean value) {
         return null;
      }

      @Override
      public Message putBooleanProperty(String key, boolean value) {
         return null;
      }

      @Override
      public Message putByteProperty(SimpleString key, byte value) {
         return null;
      }

      @Override
      public Message putByteProperty(String key, byte value) {
         return null;
      }

      @Override
      public Message putBytesProperty(SimpleString key, byte[] value) {
         return null;
      }

      @Override
      public Message putBytesProperty(String key, byte[] value) {
         return null;
      }

      @Override
      public Message putShortProperty(SimpleString key, short value) {
         return null;
      }

      @Override
      public Message putShortProperty(String key, short value) {
         return null;
      }

      @Override
      public Message putCharProperty(SimpleString key, char value) {
         return null;
      }

      @Override
      public Message putCharProperty(String key, char value) {
         return null;
      }

      @Override
      public Message putIntProperty(SimpleString key, int value) {
         return null;
      }

      @Override
      public Message putIntProperty(String key, int value) {
         return null;
      }

      @Override
      public Message putLongProperty(SimpleString key, long value) {
         return null;
      }

      @Override
      public Message putLongProperty(String key, long value) {
         return null;
      }

      @Override
      public Message putFloatProperty(SimpleString key, float value) {
         return null;
      }

      @Override
      public Message putFloatProperty(String key, float value) {
         return null;
      }

      @Override
      public Message putDoubleProperty(SimpleString key, double value) {
         return null;
      }

      @Override
      public Message putDoubleProperty(String key, double value) {
         return null;
      }

      @Override
      public Message putStringProperty(SimpleString key, SimpleString value) {
         return null;
      }

      @Override
      public Message putStringProperty(SimpleString key, String value) {
         return null;
      }

      @Override
      public Message putStringProperty(String key, String value) {
         return null;
      }

      @Override
      public Message putObjectProperty(SimpleString key, Object value) throws ActiveMQPropertyConversionException {
         return null;
      }

      @Override
      public Message putObjectProperty(String key, Object value) throws ActiveMQPropertyConversionException {
         return null;
      }

      @Override
      public Object removeProperty(SimpleString key) {
         return null;
      }

      @Override
      public Object removeProperty(String key) {
         return null;
      }

      @Override
      public boolean containsProperty(SimpleString key) {
         return false;
      }

      @Override
      public boolean containsProperty(String key) {
         return false;
      }

      @Override
      public Boolean getBooleanProperty(SimpleString key) throws ActiveMQPropertyConversionException {
         return null;
      }

      @Override
      public Boolean getBooleanProperty(String key) throws ActiveMQPropertyConversionException {
         return null;
      }

      @Override
      public Byte getByteProperty(SimpleString key) throws ActiveMQPropertyConversionException {
         return null;
      }

      @Override
      public Byte getByteProperty(String key) throws ActiveMQPropertyConversionException {
         return null;
      }

      @Override
      public Double getDoubleProperty(SimpleString key) throws ActiveMQPropertyConversionException {
         return null;
      }

      @Override
      public Double getDoubleProperty(String key) throws ActiveMQPropertyConversionException {
         return null;
      }

      @Override
      public Integer getIntProperty(SimpleString key) throws ActiveMQPropertyConversionException {
         return null;
      }

      @Override
      public Integer getIntProperty(String key) throws ActiveMQPropertyConversionException {
         return null;
      }

      @Override
      public Long getLongProperty(SimpleString key) throws ActiveMQPropertyConversionException {
         return null;
      }

      @Override
      public Long getLongProperty(String key) throws ActiveMQPropertyConversionException {
         return null;
      }

      @Override
      public Object getObjectProperty(SimpleString key) {
         return null;
      }

      @Override
      public Object getObjectProperty(String key) {
         return null;
      }

      @Override
      public Short getShortProperty(SimpleString key) throws ActiveMQPropertyConversionException {
         return null;
      }

      @Override
      public Short getShortProperty(String key) throws ActiveMQPropertyConversionException {
         return null;
      }

      @Override
      public Float getFloatProperty(SimpleString key) throws ActiveMQPropertyConversionException {
         return null;
      }

      @Override
      public Float getFloatProperty(String key) throws ActiveMQPropertyConversionException {
         return null;
      }

      @Override
      public String getStringProperty(SimpleString key) throws ActiveMQPropertyConversionException {
         return null;
      }

      @Override
      public String getStringProperty(String key) throws ActiveMQPropertyConversionException {
         return null;
      }

      @Override
      public SimpleString getSimpleStringProperty(SimpleString key) throws ActiveMQPropertyConversionException {
         return null;
      }

      @Override
      public SimpleString getSimpleStringProperty(String key) throws ActiveMQPropertyConversionException {
         return null;
      }

      @Override
      public byte[] getBytesProperty(SimpleString key) throws ActiveMQPropertyConversionException {
         return new byte[0];
      }

      @Override
      public byte[] getBytesProperty(String key) throws ActiveMQPropertyConversionException {
         return new byte[0];
      }

      @Override
      public Set<SimpleString> getPropertyNames() {
         return null;
      }

      @Override
      public Map<String, Object> toMap() {
         return null;
      }

      @Override
      public Map<String, Object> toPropertyMap() {
         return null;
      }

      @Override
      public long getPersistentSize() throws ActiveMQException {
         return 0;
      }

      @Override
      public Object getOwner() {
         return null;
      }

      @Override
      public void setOwner(Object object) {
      }

      @Override
      public Object getUserContext(Object key) {
         return null;
      }

      @Override
      public void setUserContext(Object key, Object value) {

      }
   }
}

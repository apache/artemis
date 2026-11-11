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
package org.apache.activemq.artemis.core.server.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.netty.buffer.ByteBuf;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQPropertyConversionException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.persistence.CoreMessageObjectPools;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscription;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.Persister;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.server.Consumer;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.ReferenceCounter;
import org.apache.activemq.artemis.utils.UUID;
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;
import org.apache.activemq.artemis.utils.collections.NodeStoreFactory;
import org.apache.activemq.artemis.utils.critical.CriticalComponentImpl;
import org.apache.activemq.artemis.utils.critical.EmptyCriticalAnalyzer;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScheduledDeliveryHandlerTest {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   public void testScheduleRandom() throws Exception {
      ScheduledDeliveryHandlerImpl handler = new ScheduledDeliveryHandlerImpl(null, new FakeQueueForScheduleUnitTest(0));

      long nextMessage = 0;
      long NUMBER_OF_SEQUENCES = 100000;

      for (int i = 0; i < NUMBER_OF_SEQUENCES; i++) {
         int numberOfMessages = RandomUtil.randomInt() % 10;
         if (numberOfMessages == 0) {
            numberOfMessages = 1;
         }

         long nextScheduledTime = RandomUtil.randomPositiveLong();

         for (int j = 0; j < numberOfMessages; j++) {
            boolean tail = RandomUtil.randomBoolean();

            addMessage(handler, nextMessage++, nextScheduledTime, tail);
         }
      }

      debugList(true, handler, nextMessage);

   }

   @Test
   public void testScheduleSameTimeHeadAndTail() throws Exception {
      ScheduledDeliveryHandlerImpl handler = new ScheduledDeliveryHandlerImpl(null, new FakeQueueForScheduleUnitTest(0));

      long time = System.currentTimeMillis() + 10000;
      for (int i = 10001; i < 20000; i++) {
         addMessage(handler, i, time, true);
      }
      addMessage(handler, 10000, time, false);

      time = System.currentTimeMillis() + 5000;
      for (int i = 1; i < 10000; i++) {
         addMessage(handler, i, time, true);
      }
      addMessage(handler, 0, time, false);

      debugList(true, handler, 20000);

      validateSequence(handler);

   }

   @Test
   public void testScheduleFixedSample() throws Exception {
      ScheduledDeliveryHandlerImpl handler = new ScheduledDeliveryHandlerImpl(null, new FakeQueueForScheduleUnitTest(0));

      addMessage(handler, 0, 48L, true);
      addMessage(handler, 1, 75L, true);
      addMessage(handler, 2, 56L, true);
      addMessage(handler, 3, 7L, false);
      addMessage(handler, 4, 69L, true);

      debugList(true, handler, 5);

   }

   @Test
   public void testScheduleWithAddHeads() throws Exception {
      ScheduledDeliveryHandlerImpl handler = new ScheduledDeliveryHandlerImpl(null, new FakeQueueForScheduleUnitTest(0));

      addMessage(handler, 0, 1, true);
      addMessage(handler, 1, 2, true);
      addMessage(handler, 2, 3, true);
      addMessage(handler, 3, 3, true);
      addMessage(handler, 4, 4, true);

      addMessage(handler, 10, 5, false);
      addMessage(handler, 9, 5, false);
      addMessage(handler, 8, 5, false);
      addMessage(handler, 7, 5, false);
      addMessage(handler, 6, 5, false);
      addMessage(handler, 5, 5, false);

      validateSequence(handler);

   }

   @Test
   public void testScheduleFixedSampleTailAndHead() throws Exception {
      ScheduledDeliveryHandlerImpl handler = new ScheduledDeliveryHandlerImpl(null, new FakeQueueForScheduleUnitTest(0));

      // mix a sequence of tails / heads, but at the end this was supposed to be all sequential
      addMessage(handler, 1, 48L, true);
      addMessage(handler, 2, 48L, true);
      addMessage(handler, 3, 48L, true);
      addMessage(handler, 4, 48L, true);
      addMessage(handler, 5, 48L, true);
      addMessage(handler, 0, 48L, false);

      addMessage(handler, 13, 59L, true);
      addMessage(handler, 14, 59L, true);
      addMessage(handler, 15, 59L, true);
      addMessage(handler, 16, 59L, true);
      addMessage(handler, 17, 59L, true);
      addMessage(handler, 12, 59L, false);

      addMessage(handler, 7, 49L, true);
      addMessage(handler, 8, 49L, true);
      addMessage(handler, 9, 49L, true);
      addMessage(handler, 10, 49L, true);
      addMessage(handler, 11, 49L, true);
      addMessage(handler, 6, 49L, false);

      validateSequence(handler);
   }

   @Test
   public void testScheduleNow() throws Exception {

      ExecutorService executor = Executors.newFixedThreadPool(50, ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1, ActiveMQThreadFactory.defaultThreadFactory(getClass().getName()));
      try {
         for (int i = 0; i < 100; i++) {
            // it's better to run the test a few times instead of run millions of messages here
            internalSchedule(executor, scheduler);
         }
      } finally {
         scheduler.shutdownNow();
         executor.shutdownNow();
      }
   }

   private void internalSchedule(ExecutorService executor, ScheduledThreadPoolExecutor scheduler) throws Exception {
      final int NUMBER_OF_MESSAGES = 200;
      int NUMBER_OF_THREADS = 20;

      final FakeQueueForScheduleUnitTest fakeQueue = new FakeQueueForScheduleUnitTest(NUMBER_OF_MESSAGES * NUMBER_OF_THREADS);
      final ScheduledDeliveryHandlerImpl handler = new ScheduledDeliveryHandlerImpl(scheduler, fakeQueue);

      final long now = System.currentTimeMillis();

      final CountDownLatch latchDone = new CountDownLatch(NUMBER_OF_THREADS);

      final AtomicInteger error = new AtomicInteger(0);

      class ProducerThread implements Runnable {

         @Override
         public void run() {
            try {
               for (int i = 0; i < NUMBER_OF_MESSAGES; i++) {
                  checkAndSchedule(handler, i, now, false, fakeQueue);
               }
            } catch (Exception e) {
               e.printStackTrace();
               error.incrementAndGet();
            } finally {
               latchDone.countDown();
            }

         }
      }

      for (int i = 0; i < NUMBER_OF_THREADS; i++) {
         executor.execute(new ProducerThread());
      }

      assertTrue(latchDone.await(1, TimeUnit.MINUTES));

      assertEquals(0, error.get());

      if (!fakeQueue.waitCompletion(2, TimeUnit.SECONDS)) {
         fail("Couldn't complete queue.add, expected " + NUMBER_OF_MESSAGES + ", still missing " + fakeQueue.expectedElements.toString());
      }
   }

   private void validateSequence(ScheduledDeliveryHandlerImpl handler) throws Exception {
      long lastSequence = -1;
      for (MessageReference ref : handler.getScheduledReferences()) {
         assertEquals(lastSequence + 1, ref.getMessage().getMessageID());
         lastSequence = ref.getMessage().getMessageID();
      }
   }

   private void addMessage(ScheduledDeliveryHandlerImpl handler,
                           long nextMessageID,
                           long nextScheduledTime,
                           boolean tail) {
      MessageReferenceImpl refImpl = new MessageReferenceImpl(new FakeMessage(nextMessageID), null);
      refImpl.setScheduledDeliveryTime(nextScheduledTime);
      handler.addInPlace(nextScheduledTime, refImpl, tail);
   }

   private void checkAndSchedule(ScheduledDeliveryHandlerImpl handler,
                                 long nextMessageID,
                                 long nextScheduledTime,
                                 boolean tail,
                                 Queue queue) {
      MessageReferenceImpl refImpl = new MessageReferenceImpl(new FakeMessage(nextMessageID), queue);
      refImpl.setScheduledDeliveryTime(nextScheduledTime);
      handler.checkAndSchedule(refImpl, tail);
   }

   private void debugList(boolean fail,
                          ScheduledDeliveryHandlerImpl handler,
                          long numberOfExpectedMessages) throws Exception {
      List<MessageReference> refs = handler.getScheduledReferences();

      Set<Long> messages = new HashSet<>();

      long lastTime = -1;

      for (MessageReference ref : refs) {
         assertFalse(messages.contains(ref.getMessage().getMessageID()));
         messages.add(ref.getMessage().getMessageID());

         if (fail) {
            assertTrue(ref.getScheduledDeliveryTime() >= lastTime);
         } else {
            if (ref.getScheduledDeliveryTime() < lastTime) {
               logger.debug("^^^fail at {}", ref.getScheduledDeliveryTime());
            }
         }
         lastTime = ref.getScheduledDeliveryTime();
      }

      for (long i = 0; i < numberOfExpectedMessages; i++) {
         assertTrue(messages.contains(i));
      }
   }

   class FakeMessage implements Message {

      @Override
      public Object getUserContext(Object key) {
         return null;
      }

      @Override
      public void setUserContext(Object key, Object value) {

      }

      @Override
      public void setPaged() {

      }

      @Override
      public boolean isPaged() {
         return false;
      }

      @Override
      public String getProtocolName() {
         // should normally not be visible in GUI
         return getClass().getName();
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
      public void persist(ActiveMQBuffer targetRecord) {

      }

      @Override
      public int getDurableCount() {
         return 0;
      }

      @Override
      public Long getScheduledDeliveryTime() {
         return null;
      }

      @Override
      public void reloadPersistence(ActiveMQBuffer record, CoreMessageObjectPools pools) {

      }

      @Override
      public Persister<Message> getPersister() {
         return null;
      }

      @Override
      public int getPersistSize() {
         return 0;
      }
      final long id;

      @Override
      public CoreMessage toCore() {
         return toCore(null);
      }

      @Override
      public CoreMessage toCore(CoreMessageObjectPools coreMessageObjectPools) {
         return null;
      }

      FakeMessage(final long id) {
         this.id = id;
      }

      @Override
      public FakeMessage setMessageID(long id) {
         return this;
      }

      @Override
      public long getMessageID() {
         return id;
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
      public Message copy(long newID) {
         return null;
      }

      @Override
      public Message copy() {
         return null;
      }

      @Override
      public int getMemoryEstimate() {
         return 0;
      }

      @Override
      public int getRefCount() {
         return 0;
      }

      @Override
      public byte[] getDuplicateIDBytes() {
         return new byte[0];
      }

      @Override
      public Object getDuplicateProperty() {
         return null;
      }

      @Override
      public void messageChanged() {

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
      public FakeMessage setDurable(boolean durable) {
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
      public FakeMessage setExpiration(long expiration) {
         return this;
      }

      @Override
      public long getTimestamp() {
         return 0;
      }

      @Override
      public FakeMessage setTimestamp(long timestamp) {
         return this;
      }

      @Override
      public byte getPriority() {
         return 0;
      }

      @Override
      public FakeMessage setPriority(byte priority) {
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
      public Message setUserID(Object userID) {
         return null;
      }

      @Override
      public void receiveBuffer(ByteBuf buffer) {

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
      public void sendBuffer(ByteBuf buffer, int count) {

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

   }

   public class FakeQueueForScheduleUnitTest extends CriticalComponentImpl implements Queue {

      @Override
      public void setPurgeOnNoConsumers(boolean value) {

      }

      @Override
      public void reloadSequence(MessageReference ref) {
      }

      @Override
      public boolean isEnabled() {
         return false;
      }

      @Override
      public void setEnabled(boolean value) {

      }

      @Override
      public PagingStore getPagingStore() {
         return null;
      }

      @Override
      public int durableUp(Message message) {
         return 1;
      }

      @Override
      public int durableDown(Message message) {
         return 1;
      }

      @Override
      public void refUp(MessageReference messageReference) {

      }

      @Override
      public MessageReference removeWithSuppliedID(String serverID, long id, NodeStoreFactory<MessageReference> nodeStore) {
         return null;
      }

      @Override
      public void expireReferences(Runnable done) {

      }

      @Override
      public void refDown(MessageReference messageReference) {

      }

      @Override
      public void removeAddress() throws Exception {
      }

      @Override
      public long getAcknowledgeAttempts() {
         return 0;
      }

      @Override
      public boolean allowsReferenceCallback() {
         return false;
      }

      @Override
      public int getConsumersBeforeDispatch() {
         return 0;
      }

      @Override
      public void setConsumersBeforeDispatch(int consumersBeforeDispatch) {

      }

      @Override
      public long getDelayBeforeDispatch() {
         return 0;
      }

      @Override
      public void setDelayBeforeDispatch(long delayBeforeDispatch) {

      }

      @Override
      public long getDispatchStartTime() {
         return 0;
      }

      @Override
      public boolean isDispatching() {
         return false;
      }

      @Override
      public void setDispatching(boolean dispatching) {

      }

      @Override
      public void setMaxConsumer(int maxConsumers) {

      }

      @Override
      public int getGroupBuckets() {
         return 0;
      }

      @Override
      public void setGroupBuckets(int groupBuckets) {

      }

      @Override
      public boolean isGroupRebalance() {
         return false;
      }

      @Override
      public void setGroupRebalance(boolean groupRebalance) {

      }

      @Override
      public boolean isGroupRebalancePauseDispatch() {
         return false;
      }

      @Override
      public void setGroupRebalancePauseDispatch(boolean groupRebalancePauseDisptach) {

      }

      @Override
      public SimpleString getGroupFirstKey() {
         return null;
      }

      @Override
      public void setGroupFirstKey(SimpleString groupFirstKey) {

      }

      @Override
      public boolean isConfigurationManaged() {
         return false;
      }

      @Override
      public void setConfigurationManaged(boolean configurationManaged) {

      }

      @Override
      public void recheckRefCount(OperationContext context) {
      }

      @Override
      public void unproposed(SimpleString groupID) {

      }

      public FakeQueueForScheduleUnitTest(final int expectedElements) {
         super(EmptyCriticalAnalyzer.getInstance(), 1);
         this.expectedElements = new CountDownLatch(expectedElements);
      }

      @Override
      public boolean isPersistedPause() {
         return false;
      }

      public boolean waitCompletion(long timeout, TimeUnit timeUnit) throws Exception {
         return expectedElements.await(timeout, timeUnit);
      }

      final CountDownLatch expectedElements;
      LinkedList<MessageReference> messages = new LinkedList<>();

      @Override
      public SimpleString getName() {
         return null;
      }

      @Override
      public Long getID() {
         return 0L;
      }

      @Override
      public void pause(boolean persist) {
      }

      @Override
      public void reloadPause(long recordID) {
      }

      @Override
      public Filter getFilter() {
         return null;
      }

      @Override
      public void setFilter(Filter filter) {
      }

      @Override
      public PageSubscription getPageSubscription() {
         return null;
      }

      @Override
      public RoutingType getRoutingType() {
         return null;
      }

      @Override
      public void setRoutingType(RoutingType routingType) {

      }

      @Override
      public boolean isDurable() {
         return false;
      }

      @Override
      public boolean isDurableMessage() {
         return false;
      }

      @Override
      public boolean isAutoDelete() {
         // no-op
         return false;
      }

      @Override
      public long getAutoDeleteDelay() {
         // no-op
         return -1;
      }

      @Override
      public long getAutoDeleteMessageCount() {
         // no-op
         return -1;
      }

      @Override
      public boolean isTemporary() {
         return false;
      }

      @Override
      public boolean isAutoCreated() {
         return false;
      }

      @Override
      public boolean isPurgeOnNoConsumers() {
         return false;
      }

      @Override
      public int getMaxConsumers() {
         return -1;
      }

      @Override
      public void addConsumer(Consumer consumer) throws Exception {

      }

      @Override
      public void addLingerSession(String sessionId) {

      }

      @Override
      public void removeLingerSession(String sessionId) {

      }

      @Override
      public void removeConsumer(Consumer consumer) {

      }

      @Override
      public int retryMessages(Filter filter) throws Exception {
         return 0;
      }

      @Override
      public int getConsumerCount() {
         return 0;
      }

      @Override
      public long getConsumerRemovedTimestamp() {
         return 0;
      }

      @Override
      public void setRingSize(long ringSize) {

      }

      @Override
      public long getRingSize() {
         return 0;
      }

      @Override
      public ReferenceCounter getConsumersRefCount() {
         return null;
      }

      @Override
      public void addSorted(List<MessageReference> refs, boolean scheduling) {
         addHead(refs, scheduling);
      }

      @Override
      public void reload(MessageReference ref) {

      }

      @Override
      public void addTail(MessageReference ref) {

      }

      @Override
      public void addTail(MessageReference ref, boolean direct) {

      }

      @Override
      public void addHead(MessageReference ref, boolean scheduling) {

      }

      @Override
      public void addSorted(MessageReference ref, boolean scheduling) {

      }

      @Override
      public void addHead(List<MessageReference> refs, boolean scheduling) {
         for (MessageReference ref : refs) {
            addFirst(ref);
         }
      }

      private void addFirst(MessageReference ref) {
         expectedElements.countDown();
         this.messages.addFirst(ref);
      }

      @Override
      public void acknowledge(MessageReference ref) throws Exception {

      }

      @Override
      public void acknowledge(MessageReference ref, ServerConsumer consumer) throws Exception {

      }

      @Override
      public void acknowledge(MessageReference ref, AckReason reason, ServerConsumer consumer) throws Exception {

      }

      @Override
      public void acknowledge(Transaction tx, MessageReference ref) throws Exception {

      }

      @Override
      public void acknowledge(Transaction tx, MessageReference ref, AckReason reason, ServerConsumer consumer, boolean delivering) throws Exception {

      }

      @Override
      public void reacknowledge(Transaction tx, MessageReference ref) throws Exception {

      }

      @Override
      public void cancel(Transaction tx, MessageReference ref) {

      }

      @Override
      public void cancel(Transaction tx, MessageReference ref, boolean ignoreRedeliveryCheck) {

      }

      @Override
      public void cancel(MessageReference reference, long timeBase) throws Exception {

      }

      @Override
      public void deliverAsync() {

      }

      @Override
      public void forceDelivery() {

      }

      @Override
      public void deleteQueue() throws Exception {

      }

      @Override
      public void deleteQueue(boolean removeConsumers) throws Exception {

      }

      @Override
      public void destroyPaging() throws Exception {

      }

      @Override
      public long getMessageCount() {
         return 0;
      }

      @Override
      public long getPersistentSize() {
         return 0;
      }

      @Override
      public long getDurableMessageCount() {
         return 0;
      }

      @Override
      public long getDurablePersistentSize() {
         return 0;
      }

      @Override
      public int getDeliveringCount() {
         return 0;
      }

      @Override
      public long getDeliveringSize() {
         return 0;
      }

      @Override
      public int getDurableDeliveringCount() {
         return 0;
      }

      @Override
      public long getDurableDeliveringSize() {
         return 0;
      }

      @Override
      public int getDurableScheduledCount() {
         return 0;
      }

      @Override
      public long getDurableScheduledSize() {
         return 0;
      }

      @Override
      public void referenceHandled(MessageReference ref) {

      }

      @Override
      public int getScheduledCount() {
         return 0;
      }

      @Override
      public long getScheduledSize() {
         return 0;
      }

      @Override
      public List<MessageReference> getScheduledMessages() {
         return null;
      }

      @Override
      public Map<String, List<MessageReference>> getDeliveringMessages() {
         return null;
      }

      @Override
      public long getMessagesAdded() {
         return 0;
      }

      @Override
      public long getMessagesAcknowledged() {
         return 0;
      }

      @Override
      public long getMessagesExpired() {
         return 0;
      }

      @Override
      public long getMessagesKilled() {
         return 0;
      }

      @Override
      public long getMessagesReplaced() {
         return 0;
      }

      @Override
      public MessageReference removeReferenceWithID(long id) throws Exception {
         return null;
      }

      @Override
      public int deleteAllReferences() throws Exception {
         return 0;
      }

      @Override
      public int deleteAllReferences(int flushLimit) throws Exception {
         return 0;
      }

      @Override
      public boolean deleteReference(long messageID) throws Exception {
         return false;
      }

      @Override
      public int deleteMatchingReferences(Filter filter) throws Exception {
         return 0;
      }

      @Override
      public int deleteMatchingReferences(int flushLImit, Filter filter, AckReason reason) throws Exception {
         return 0;
      }

      @Override
      public boolean expireReference(long messageID) throws Exception {
         return false;
      }

      @Override
      public int expireReferences(Filter filter) throws Exception {
         return 0;
      }

      @Override
      public void expireReferences() {

      }

      @Override
      public void expire(MessageReference ref) throws Exception {

      }

      @Override
      public void expire(MessageReference ref, ServerConsumer consumer, boolean delivering) throws Exception {

      }

      @Override
      public boolean sendToDeadLetterAddress(Transaction tx, MessageReference ref) throws Exception {
         return false;
      }

      @Override
      public boolean sendMessageToDeadLetterAddress(long messageID) throws Exception {
         return false;
      }

      @Override
      public int sendMessagesToDeadLetterAddress(Filter filter) throws Exception {
         return 0;
      }

      @Override
      public boolean changeReferencePriority(long messageID, byte newPriority) throws Exception {
         return false;
      }

      @Override
      public int changeReferencesPriority(Filter filter, byte newPriority) throws Exception {
         return 0;
      }

      @Override
      public boolean moveReference(long messageID, SimpleString toAddress, Binding binding, boolean rejectDuplicates) throws Exception {
         return false;
      }

      @Override
      public int moveReferences(Filter filter, SimpleString toAddress, Binding binding) throws Exception {
         return 0;
      }

      @Override
      public int moveReferences(int flushLimit,
                                Filter filter,
                                SimpleString toAddress,
                                boolean rejectDuplicates,
                                Binding binding) throws Exception {
         return 0;
      }

      @Override
      public int moveReferences(int flushLimit, Filter filter, SimpleString toAddress, boolean rejectDuplicates, int messageCount, Binding binding) throws Exception {
         return 0;
      }

      @Override
      public boolean copyReference(long messageID, SimpleString address, Binding binding) throws Exception {
         return false;
      }

      @Override
      public void addRedistributor(long delay) {

      }

      @Override
      public void cancelRedistributor() {

      }

      @Override
      public boolean hasMatchingConsumer(Message message) {
         return false;
      }

      @Override
      public long getPendingMessageCount() {
         return 0;
      }

      @Override
      public Collection<Consumer> getConsumers() {
         return null;
      }

      @Override
      public Map<SimpleString, Consumer> getGroups() {
         return null;
      }

      @Override
      public void resetGroup(SimpleString groupID) {

      }

      @Override
      public void resetAllGroups() {

      }

      @Override
      public int getGroupCount() {
         return 0;
      }

      @Override
      public Pair<Boolean, Boolean> checkRedelivery(MessageReference ref,
                                                    long timeBase,
                                                    boolean ignoreRedeliveryDelay) throws Exception {
         return new Pair<>(false, false);
      }

      @Override
      public LinkedListIterator<MessageReference> iterator() {
         return null;
      }

      @Override
      public LinkedListIterator<MessageReference> browserIterator() {
         return null;
      }

      @Override
      public SimpleString getExpiryAddress() {
         return null;
      }

      @Override
      public SimpleString getDeadLetterAddress() {
         return null;
      }

      @Override
      public void pause() {

      }

      @Override
      public void resume() {

      }

      @Override
      public boolean isPaused() {
         return false;
      }

      @Override
      public Executor getExecutor() {
         return null;
      }

      @Override
      public void resetAllIterators() {

      }

      @Override
      public boolean flushExecutor() {
         return false;
      }

      @Override
      public void close() throws Exception {

      }

      @Override
      public boolean isDirectDeliver() {
         return false;
      }

      @Override
      public SimpleString getAddress() {
         return null;
      }

      @Override
      public boolean isInternalQueue() {
         return false;
      }

      @Override
      public void setInternalQueue(boolean internalQueue) {

      }

      @Override
      public void resetMessagesAdded() {

      }

      @Override
      public void resetMessagesAcknowledged() {

      }

      @Override
      public void resetMessagesExpired() {

      }

      @Override
      public void resetMessagesKilled() {

      }

      @Override
      public void incrementMesssagesAdded() {

      }

      @Override
      public void deliverScheduledMessages() {

      }

      @Override
      public void deliverScheduledMessages(String filter) throws ActiveMQException {

      }

      @Override
      public void deliverScheduledMessage(long messageId) throws ActiveMQException {

      }

      @Override
      public void route(Message message, RoutingContext context) throws Exception {

      }

      @Override
      public void routeWithAck(Message message, RoutingContext context) {

      }

      @Override
      public void postAcknowledge(MessageReference ref, AckReason reason, boolean delivering) {

      }

      @Override
      public void postAcknowledge(MessageReference ref, AckReason reason) {

      }

      @Override
      public SimpleString getUser() {
         return null;
      }

      @Override
      public void setUser(SimpleString user) {

      }

      @Override
      public boolean isLastValue() {
         return false;
      }

      @Override
      public SimpleString getLastValueKey() {
         return null;
      }

      @Override
      public boolean isNonDestructive() {
         return false;
      }

      @Override
      public void setNonDestructive(boolean nonDestructive) {

      }

      @Override
      public boolean isExclusive() {
         return false;
      }

      @Override
      public void setExclusive(boolean exclusive) {

      }

      @Override
      public int getInitialQueueBufferSize() {
         return 0;
      }
   }
}

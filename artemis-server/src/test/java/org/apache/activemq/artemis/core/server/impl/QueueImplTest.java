/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.server.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.io.IOCallback;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.paging.cursor.PageIterator;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscription;
import org.apache.activemq.artemis.core.paging.cursor.PagedReferenceImpl;
import org.apache.activemq.artemis.core.paging.impl.PagedMessageImpl;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.QueueFactory;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.cluster.RemoteQueueBinding;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.actors.ArtemisExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class QueueImplTest {

   private static final SimpleString SNF_MESSAGE_ADDRESS = SimpleString.of("myAddress");

   private static final SimpleString SNF_QUEUE_ADDRESS = SimpleString.of("sf.myCluster.target");

   private ExecutorService snfExecutor;

   private NullStorageManager snfStorageManager;

   @AfterEach
   public void cleanupSnFResources() throws Exception {
      if (snfExecutor != null) {
         snfExecutor.shutdownNow();
      }
      if (snfStorageManager != null) {
         snfStorageManager.stop();
      }
   }

   /**
    * A store-and-forward reference whose remote queue id resolves to a routing name must be routed directly to the
    * remote queue binding with the same routing name on the scale-down target, keeping the application address, and
    * the source reference acknowledged.
    */
   @Test
   public void moveReferencesBetweenSnFQueuesRoutesDirectlyToTarget() throws Exception {
      PostOffice postOffice = Mockito.mock(PostOffice.class);
      QueueImpl snf = createSnFQueue(postOffice);

      RemoteQueueBinding sourceBinding = createRemoteBinding(snf, 1L, "routingName");
      RemoteQueueBinding targetBinding = createRemoteBinding(createRemoteQueue("sf.myCluster.targetNode"), 7L, "routingName");
      Mockito.when(postOffice.getAllBindings()).thenAnswer(invocation -> Stream.of(sourceBinding, targetBinding));

      snf.addTail(createSnFReference(snf, 1, RoutingType.MULTICAST));

      snf.moveReferencesBetweenSnFQueues("targetNode");

      ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
      Mockito.verify(targetBinding).route(captor.capture(), Mockito.any(RoutingContext.class));
      assertEquals(SNF_MESSAGE_ADDRESS, captor.getValue().getAddressSimpleString(),
                   "the routed copy must carry the application address, not the SnF queue address");
      Mockito.verify(sourceBinding, Mockito.never()).route(Mockito.any(Message.class), Mockito.any(RoutingContext.class));
      Mockito.verify(postOffice).processRoute(Mockito.any(Message.class), Mockito.any(RoutingContext.class), Mockito.eq(false));
      assertEquals(0, snf.getMessageCount());
   }

   /**
    * A store-and-forward reference targeting the local node ID routes directly to the local queue binding.
    */
   @Test
   public void moveReferencesBetweenSnFQueuesRoutesDirectlyToLocalTarget() throws Exception {
      PostOffice postOffice = Mockito.mock(PostOffice.class);
      QueueImpl snf = createSnFQueue(postOffice);

      RemoteQueueBinding sourceBinding = createRemoteBinding(snf, 1L, "routingName");
      LocalQueueBinding targetBinding = createLocalBinding(createRemoteQueue("localQueue"), 7L, "routingName");
      Mockito.when(postOffice.getAllBindings()).thenAnswer(invocation -> Stream.of(sourceBinding, targetBinding));

      snf.addTail(createSnFReference(snf, 1, RoutingType.ANYCAST));

      snf.moveReferencesBetweenSnFQueues("localNode");

      Mockito.verify(targetBinding).route(Mockito.any(Message.class), Mockito.any(RoutingContext.class));
      Mockito.verify(postOffice).processRoute(Mockito.any(Message.class), Mockito.any(RoutingContext.class), Mockito.eq(false));
      assertEquals(0, snf.getMessageCount());
   }

   /**
    * When no binding exists for the requested target node, an alternative enabled binding for the routing name is used
    * as a fallback to avoid leaving messages stranded on the drained SnF queue.
    */
   @Test
   public void moveReferencesBetweenSnFQueuesRoutesToFallbackAlternativeWhenTargetNodeMissing() throws Exception {
      PostOffice postOffice = Mockito.mock(PostOffice.class);
      QueueImpl snf = createSnFQueue(postOffice);

      RemoteQueueBinding sourceBinding = createRemoteBinding(snf, 1L, "routingName");
      RemoteQueueBinding fallbackTarget = createRemoteBinding(createRemoteQueue("sf.myCluster.otherNode"), 7L, "routingName");
      Mockito.when(postOffice.getAllBindings()).thenAnswer(invocation -> Stream.of(sourceBinding, fallbackTarget));

      snf.addTail(createSnFReference(snf, 1, RoutingType.ANYCAST));

      snf.moveReferencesBetweenSnFQueues("targetNode");

      Mockito.verify(fallbackTarget).route(Mockito.any(Message.class), Mockito.any(RoutingContext.class));
      Mockito.verify(postOffice).processRoute(Mockito.any(Message.class), Mockito.any(RoutingContext.class), Mockito.eq(false));
      assertEquals(0, snf.getMessageCount());
   }

   /**
    * When a message on the SnF queue does not have the {@code HDR_ROUTE_TO_IDS} property, it is left on the queue.
    */
   @Test
   public void moveReferencesBetweenSnFQueuesLeavesMessageWhenNoRouteToIDs() throws Exception {
      PostOffice postOffice = Mockito.mock(PostOffice.class);
      QueueImpl snf = createSnFQueue(postOffice);

      CoreMessage message = new CoreMessage(1L, 512);
      message.setAddress(SNF_MESSAGE_ADDRESS);
      message.setRoutingType(RoutingType.ANYCAST);
      MessageReference ref = MessageReference.Factory.createReference(message, snf);
      snf.addTail(ref);

      snf.moveReferencesBetweenSnFQueues("targetNode");

      Mockito.verify(postOffice, Mockito.never()).getAllBindings();
      assertEquals(1, snf.getMessageCount());
   }

   /**
    * When the remote queue id cannot be resolved, an anycast message is routed using its address as routing name.
    */
   @Test
   public void moveReferencesBetweenSnFQueuesRoutesAnycastUsingAddressWhenIdUnresolved() throws Exception {
      PostOffice postOffice = Mockito.mock(PostOffice.class);
      QueueImpl snf = createSnFQueue(postOffice);

      RemoteQueueBinding targetBinding = createRemoteBinding(createRemoteQueue("sf.myCluster.targetNode"), 7L, SNF_MESSAGE_ADDRESS.toString());
      Mockito.when(postOffice.getAllBindings()).thenAnswer(invocation -> Stream.of(targetBinding));

      snf.addTail(createSnFReference(snf, 1, RoutingType.ANYCAST));

      snf.moveReferencesBetweenSnFQueues("targetNode");

      Mockito.verify(targetBinding).route(Mockito.any(Message.class), Mockito.any(RoutingContext.class));
      Mockito.verify(postOffice).processRoute(Mockito.any(Message.class), Mockito.any(RoutingContext.class), Mockito.eq(false));
      assertEquals(0, snf.getMessageCount());
   }

   /**
    * When no binding matches the routing name an anycast message is left on the source SnF instead of being
    * acknowledged: the message is not re-routed and the address-settings for unroutable messages do not apply.
    */
   @Test
   public void moveReferencesBetweenSnFQueuesLeavesAnycastMiss() throws Exception {
      PostOffice postOffice = Mockito.mock(PostOffice.class);
      Mockito.when(postOffice.getAllBindings()).thenAnswer(invocation -> Stream.<Binding>empty());

      QueueImpl snf = createSnFQueue(postOffice);
      snf.addTail(createSnFReference(snf, 1, RoutingType.ANYCAST));

      snf.moveReferencesBetweenSnFQueues("non-existent-target");

      Mockito.verify(postOffice, Mockito.never()).route(Mockito.any(Message.class), Mockito.any(Transaction.class), Mockito.anyBoolean(), Mockito.anyBoolean());
      Mockito.verify(postOffice, Mockito.never()).processRoute(Mockito.any(Message.class), Mockito.any(RoutingContext.class), Mockito.anyBoolean());
      assertEquals(1, snf.getMessageCount());
   }

   /**
    * A disabled remote queue binding must never be selected as a target.
    */
   @Test
   public void moveReferencesBetweenSnFQueuesIgnoresDisabledTarget() throws Exception {
      PostOffice postOffice = Mockito.mock(PostOffice.class);
      QueueImpl snf = createSnFQueue(postOffice);

      RemoteQueueBinding disabledTarget = createRemoteBinding(createRemoteQueue("sf.myCluster.targetNode"), 7L, SNF_MESSAGE_ADDRESS.toString());
      Mockito.when(disabledTarget.isEnabled()).thenReturn(false);
      Mockito.when(postOffice.getAllBindings()).thenAnswer(invocation -> Stream.of(disabledTarget));

      snf.addTail(createSnFReference(snf, 1, RoutingType.ANYCAST));

      snf.moveReferencesBetweenSnFQueues("targetNode");

      Mockito.verify(disabledTarget, Mockito.never()).route(Mockito.any(Message.class), Mockito.any(RoutingContext.class));
      assertEquals(1, snf.getMessageCount());
   }

   /**
    * {@code remoteQueueID} is node-local, so the same id can belong to remote queue bindings toward different nodes.
    * The routing name must only be resolved from the binding that belongs to the SnF queue being drained; otherwise a
    * colliding id from another node's binding would resolve the wrong routing name and misroute the message.
    */
   @Test
   public void moveReferencesBetweenSnFQueuesIgnoresCollidingRemoteQueueIdFromAnotherQueue() throws Exception {
      PostOffice postOffice = Mockito.mock(PostOffice.class);
      QueueImpl snf = createSnFQueue(postOffice);

      // the binding that actually belongs to the SnF queue being drained
      RemoteQueueBinding sourceBinding = createRemoteBinding(snf, 42L, "correct");
      // a different queue's binding that happens to share the same node-local remote queue id
      RemoteQueueBinding collidingBinding = createRemoteBinding(createRemoteQueue("sf.myCluster.other"), 42L, "wrong");
      // a target on the scale-down node, but only for the colliding binding's (wrong) routing name
      RemoteQueueBinding wrongTarget = createRemoteBinding(createRemoteQueue("sf.myCluster.targetNode"), 7L, "wrong");

      Mockito.when(postOffice.getAllBindings()).thenAnswer(invocation -> Stream.of(sourceBinding, collidingBinding, wrongTarget));

      snf.addTail(createSnFReference(snf, 42, RoutingType.ANYCAST));

      snf.moveReferencesBetweenSnFQueues("targetNode");

      // the message's real routing name ("correct") has no target: it is left in place, never routed to the wrong target
      Mockito.verify(wrongTarget, Mockito.never()).route(Mockito.any(Message.class), Mockito.any(RoutingContext.class));
      assertEquals(1, snf.getMessageCount());
   }

   /**
    * A multicast store-and-forward message with an unresolved remote queue id is only the slice already chosen for the
    * departing node's subscriptions, so it must not be routed by address (duplicate risk) and must be left on the
    * source SnF rather than acknowledging a message that was not moved.
    */
   @Test
   public void moveReferencesBetweenSnFQueuesLeavesMulticastMiss() throws Exception {
      PostOffice postOffice = Mockito.mock(PostOffice.class);
      RemoteQueueBinding addressTarget = createRemoteBinding(createRemoteQueue("sf.myCluster.targetNode"), 7L, SNF_MESSAGE_ADDRESS.toString());
      Mockito.when(postOffice.getAllBindings()).thenAnswer(invocation -> Stream.of(addressTarget));

      QueueImpl snf = createSnFQueue(postOffice);
      snf.addTail(createSnFReference(snf, 1, RoutingType.MULTICAST));

      snf.moveReferencesBetweenSnFQueues("targetNode");

      Mockito.verify(addressTarget, Mockito.never()).route(Mockito.any(Message.class), Mockito.any(RoutingContext.class));
      Mockito.verify(postOffice, Mockito.never()).processRoute(Mockito.any(Message.class), Mockito.any(RoutingContext.class), Mockito.anyBoolean());
      assertEquals(1, snf.getMessageCount());
   }

   /**
    * When the message has no routing type (e.g. an AMQP message sent to a multicast address), the routing type must be
    * resolved from the address: a multicast-capable address must still take the multicast path, leaving the message on
    * the source SnF rather than acknowledging it.
    */
   @Test
   public void moveReferencesBetweenSnFQueuesLeavesMulticastMissWhenRoutingTypeUnset() throws Exception {
      PostOffice postOffice = Mockito.mock(PostOffice.class);
      RemoteQueueBinding addressTarget = createRemoteBinding(createRemoteQueue("sf.myCluster.targetNode"), 7L, SNF_MESSAGE_ADDRESS.toString());
      Mockito.when(postOffice.getAllBindings()).thenAnswer(invocation -> Stream.of(addressTarget));
      Mockito.when(postOffice.getAddressInfo(SNF_MESSAGE_ADDRESS)).thenReturn(new AddressInfo(SNF_MESSAGE_ADDRESS, EnumSet.of(RoutingType.MULTICAST)));

      QueueImpl snf = createSnFQueue(postOffice);
      snf.addTail(createSnFReference(snf, 1, null));

      snf.moveReferencesBetweenSnFQueues("targetNode");

      Mockito.verify(addressTarget, Mockito.never()).route(Mockito.any(Message.class), Mockito.any(RoutingContext.class));
      assertEquals(1, snf.getMessageCount());
   }

   /**
    * When the message has no routing type and the address is anycast-only, the anycast path is used: the message is
    * routed by address and the source reference acknowledged.
    */
   @Test
   public void moveReferencesBetweenSnFQueuesRoutesAnycastWhenRoutingTypeUnset() throws Exception {
      PostOffice postOffice = Mockito.mock(PostOffice.class);
      RemoteQueueBinding addressTarget = createRemoteBinding(createRemoteQueue("sf.myCluster.targetNode"), 7L, SNF_MESSAGE_ADDRESS.toString());
      Mockito.when(postOffice.getAllBindings()).thenAnswer(invocation -> Stream.of(addressTarget));
      Mockito.when(postOffice.getAddressInfo(SNF_MESSAGE_ADDRESS)).thenReturn(new AddressInfo(SNF_MESSAGE_ADDRESS, EnumSet.of(RoutingType.ANYCAST)));

      QueueImpl snf = createSnFQueue(postOffice);
      snf.addTail(createSnFReference(snf, 1, null));

      snf.moveReferencesBetweenSnFQueues("targetNode");

      Mockito.verify(addressTarget).route(Mockito.any(Message.class), Mockito.any(RoutingContext.class));
      assertEquals(0, snf.getMessageCount());
   }

   /**
    * When the message has no routing type and the address is unknown (no {@code AddressInfo}), the message must not
    * fall back to anycast: it is left on the source SnF rather than acknowledging a message that was not moved.
    */
   @Test
   public void moveReferencesBetweenSnFQueuesLeavesMissWhenRoutingTypeAndAddressUnset() throws Exception {
      PostOffice postOffice = Mockito.mock(PostOffice.class);
      RemoteQueueBinding addressTarget = createRemoteBinding(createRemoteQueue("sf.myCluster.targetNode"), 7L, SNF_MESSAGE_ADDRESS.toString());
      Mockito.when(postOffice.getAllBindings()).thenAnswer(invocation -> Stream.of(addressTarget));
      // getAddressInfo is left returning null (the mock default): the address is unknown

      QueueImpl snf = createSnFQueue(postOffice);
      snf.addTail(createSnFReference(snf, 1, null));

      snf.moveReferencesBetweenSnFQueues("targetNode");

      Mockito.verify(addressTarget, Mockito.never()).route(Mockito.any(Message.class), Mockito.any(RoutingContext.class));
      assertEquals(1, snf.getMessageCount());
   }

   private Queue createRemoteQueue(String name) {
      Queue queue = Mockito.mock(Queue.class);
      Mockito.when(queue.getName()).thenReturn(SimpleString.of(name));
      return queue;
   }

   private RemoteQueueBinding createRemoteBinding(Queue queue, long remoteQueueID, String routingName) {
      RemoteQueueBinding binding = Mockito.mock(RemoteQueueBinding.class);
      Mockito.when(binding.getQueue()).thenReturn(queue);
      Mockito.when(binding.getRemoteQueueID()).thenReturn(remoteQueueID);
      Mockito.when(binding.getRoutingName()).thenReturn(SimpleString.of(routingName));
      // a Mockito mock does not call the Binding.isEnabled() default method
      Mockito.when(binding.isEnabled()).thenReturn(true);
      return binding;
   }

   private LocalQueueBinding createLocalBinding(Queue queue, long id, String routingName) {
      LocalQueueBinding binding = Mockito.mock(LocalQueueBinding.class);
      Mockito.when(binding.getQueue()).thenReturn(queue);
      Mockito.when(binding.getID()).thenReturn(id);
      Mockito.when(binding.getRoutingName()).thenReturn(SimpleString.of(routingName));
      Mockito.when(binding.isEnabled()).thenReturn(true);
      return binding;
   }

   private QueueImpl createSnFQueue(PostOffice postOffice) throws Exception {
      snfStorageManager = new NullStorageManager();
      snfStorageManager.start();
      snfExecutor = Executors.newSingleThreadExecutor();
      ArtemisExecutor artemisExecutor = ArtemisExecutor.delegate(snfExecutor);

      ExecutorFactory executorFactory = Mockito.mock(ExecutorFactory.class);
      Mockito.when(executorFactory.getExecutor()).thenReturn(artemisExecutor);
      ActiveMQServer server = Mockito.mock(ActiveMQServer.class);
      Mockito.when(server.getExecutorFactory()).thenReturn(executorFactory);
      Mockito.when(server.getNodeID()).thenReturn(SimpleString.of("localNode"));

      return new QueueImpl(QueueConfiguration.of(SNF_QUEUE_ADDRESS).setAddress(SNF_QUEUE_ADDRESS).setRoutingType(RoutingType.MULTICAST).setId(1L).setDurable(false),
                           null, null, null, Mockito.mock(ScheduledExecutorService.class), postOffice, snfStorageManager, null,
                           artemisExecutor, server, Mockito.mock(QueueFactory.class));
   }

   private MessageReference createSnFReference(QueueImpl snf, long id, RoutingType routingType) {
      CoreMessage message = new CoreMessage(id, 512);
      message.setAddress(SNF_MESSAGE_ADDRESS);
      message.setRoutingType(routingType);
      // mirror the cluster bridge: an SnF message carries the remote queue IDs it was routed for
      message.putExtraBytesProperty(Message.HDR_ROUTE_TO_IDS.concat("bridge"), ByteBuffer.allocate(Long.BYTES).putLong(id).array());
      return MessageReference.Factory.createReference(message, snf);
   }

   @Test
   public void deleteAllPagedReferencesTest() throws Exception {
      SimpleString address = SimpleString.of("test");
      AtomicInteger pageIteratorIndex = new AtomicInteger(0);
      PageIterator pageIterator = Mockito.mock(PageIterator.class);
      PageSubscription pageSubscription = Mockito.mock(PageSubscription.class);
      ExecutorService executorService = Executors.newSingleThreadExecutor();
      StorageManager storageManager = Mockito.mock(StorageManager.class);
      ActiveMQServer server = Mockito.mock(ActiveMQServer.class);
      ExecutorFactory executorFactory = Mockito.mock(ExecutorFactory.class);

      final int flushLimit = 100;
      final int pagedReferences = 5 * flushLimit;
      Semaphore availableTransactions = new Semaphore(3);

      //Mock pageIterator.
      Mockito.doAnswer(invocationOnMock -> pageIteratorIndex.get() < pagedReferences).
         when(pageIterator).hasNext();
      Mockito.doAnswer(invocationOnMock -> {
         pageIteratorIndex.incrementAndGet();
         return new PagedReferenceImpl(new PagedMessageImpl(
            Mockito.mock(Message.class), new long[]{0}), pageSubscription);
      }).when(pageIterator).next();
      Mockito.doReturn(pageIterator).when(pageSubscription).iterator();

      //Mock storageManager.
      Mockito.doAnswer(invocationOnMock -> {
         assertTrue(availableTransactions.tryAcquire(3000, TimeUnit.MILLISECONDS),
                           "Too transactions locked on afterCommit.");
         executorService.execute(() -> {
            ((IOCallback) invocationOnMock.getArgument(0)).done();
            availableTransactions.release();
         });
         return null;
      }).when(storageManager).afterCompleteOperations(Mockito.any(IOCallback.class));

      // Mock server
      Mockito.doReturn(executorFactory).when(server).getExecutorFactory();

      QueueImpl queue = new QueueImpl(QueueConfiguration.of(address).setId(0L).setDurable(false), null, null,
                                      pageSubscription, Mockito.mock(ScheduledExecutorService.class), Mockito.mock(PostOffice.class),
                                      storageManager, null, Mockito.mock(ArtemisExecutor.class),
                                      server, Mockito.mock(QueueFactory.class));

      Mockito.doReturn(queue).when(pageSubscription).getQueue();

      assertEquals(pagedReferences, queue.deleteAllReferences(flushLimit));
   }
}
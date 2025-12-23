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

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeSet;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientRequestor;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.paging.cursor.PageSubscription;
import org.apache.activemq.artemis.core.paging.cursor.PagedReference;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.Bindings;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.postoffice.impl.PostOfficeImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.NodeManager;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.cluster.ClusterControl;
import org.apache.activemq.artemis.core.server.cluster.ClusterController;
import org.apache.activemq.artemis.core.transaction.ResourceManager;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.TransactionOperation;
import org.apache.activemq.artemis.core.transaction.impl.TransactionImpl;
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class ScaleDownHandler {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   final PagingManager pagingManager;
   final PostOffice postOffice;
   private NodeManager nodeManager;
   private final ClusterController clusterController;
   private final StorageManager storageManager;
   private final int commitInterval;
   private String targetNodeId;

   public ScaleDownHandler(PagingManager pagingManager,
                           PostOffice postOffice,
                           NodeManager nodeManager,
                           ClusterController clusterController,
                           StorageManager storageManager,
                           int commitInterval) {
      this.pagingManager = pagingManager;
      this.postOffice = postOffice;
      this.nodeManager = nodeManager;
      this.clusterController = clusterController;
      this.storageManager = storageManager;
      this.commitInterval = commitInterval;
   }

   public long scaleDown(ClientSessionFactory sessionFactory,
                         ResourceManager resourceManager,
                         Map<SimpleString, List<Pair<byte[], Long>>> duplicateIDMap,
                         SimpleString managementAddress,
                         SimpleString targetNodeId) throws Exception {
      ClusterControl clusterControl = clusterController.connectToNodeInCluster((ClientSessionFactoryInternal) sessionFactory);
      clusterControl.authorize();
      long num = scaleDownMessages(sessionFactory, targetNodeId, clusterControl.getClusterUser(), clusterControl.getClusterPassword());
      ActiveMQServerLogger.LOGGER.infoScaledDownMessages(num);
      scaleDownTransactions(sessionFactory, resourceManager, clusterControl.getClusterUser(), clusterControl.getClusterPassword());
      scaleDownDuplicateIDs(duplicateIDMap, sessionFactory, managementAddress, clusterControl.getClusterUser(), clusterControl.getClusterPassword());
      clusterControl.announceScaleDown(SimpleString.of(this.targetNodeId), nodeManager.getNodeId());
      return num;
   }

   public long scaleDownMessages(ClientSessionFactory sessionFactory,
                                 SimpleString nodeId,
                                 String user,
                                 String password) throws Exception {
      long messageCount = 0;
      targetNodeId = nodeId != null ? nodeId.toString() : getTargetNodeId(sessionFactory);

      try (ClientSession session = sessionFactory.createSession(user, password, false, true, true, false, 0)) {
         ClientProducer producer = session.createProducer();

         // perform a loop per address
         for (SimpleString address : postOffice.getAddresses()) {
            logger.debug("Scaling down address {}", address);
            Bindings bindings = postOffice.lookupBindingsForAddress(address);

            // It will get a list of queues on this address, ordered by the number of messages
            Set<Queue> queues = new TreeSet<>(new OrderQueueByNumberOfReferencesComparator());
            if (bindings != null) {
               for (Binding binding : bindings.getBindings()) {
                  if (binding instanceof LocalQueueBinding localQueueBinding) {
                     Queue queue = localQueueBinding.getQueue();
                     if (!queue.isTemporary()) {
                        // as part of scale down we will cancel any scheduled message and pass it to theWhile we scan for the queues we will also cancel any scheduled messages and deliver them right away
                        queue.deliverScheduledMessages();
                        queues.add(queue);
                     }
                  }
               }
            }

            String sfPrefix =  ((PostOfficeImpl) postOffice).getServer().getInternalNamingPrefix() + "sf.";
            long messageCountItem = 0;
            if (address.toString().startsWith(sfPrefix)) {
               messageCountItem += scaleDownSNF(address, queues, producer);
            } else {
               messageCountItem += scaleDownRegularMessages(address, queues, session, producer);
            }
            logger.debug("Scaled down {} messages on address {}", messageCountItem, address);
            messageCount += messageCountItem;
         }
      }

      return messageCount;
   }

   public long scaleDownRegularMessages(final SimpleString address,
                                        final Set<Queue> queues,
                                        final ClientSession clientSession,
                                        final ClientProducer producer) throws Exception {
      logger.debug("Scaling down messages on address {}", address);
      long messageCount = 0;

      final HashMap<Queue, QueuesXRefInnerManager> controls = new HashMap<>();

      PagingStore pageStore = pagingManager.getPageStore(address);

      Transaction tx = new TransactionImpl(storageManager);

      if (pageStore != null) {
         pageStore.disableCleanup();
      }

      try {

         for (Queue queue : queues) {
            controls.put(queue, new QueuesXRefInnerManager(clientSession, queue, pageStore));
         }

         // compile a list of all the relevant queues and queue iterators for this address
         for (Queue loopQueue : queues) {
            logger.debug("Scaling down messages on address {} / performing loop on queue {}", address, loopQueue);

            try (LinkedListIterator<MessageReference> messagesIterator = loopQueue.browserIterator()) {

               while (messagesIterator.hasNext()) {
                  MessageReference messageReference = messagesIterator.next();
                  Message message = messageReference.getMessage().copy();

                  logger.debug("Reading message {} from queue {}", message, loopQueue);
                  Set<QueuesXRefInnerManager> queuesFound = new HashSet<>();

                  for (Map.Entry<Queue, QueuesXRefInnerManager> controlEntry : controls.entrySet()) {
                     if (controlEntry.getKey() == loopQueue) {
                        // no need to lookup on itself, we just add it
                        queuesFound.add(controlEntry.getValue());
                     } else if (controlEntry.getValue().lookup(messageReference)) {
                        logger.debug("Message existed on queue {} removeID={}", controlEntry.getKey().getID(), controlEntry.getValue().getQueueID());
                        queuesFound.add(controlEntry.getValue());
                     }
                  }

                  // get the ID for every queue that contains the message
                  ByteBuffer buffer = ByteBuffer.allocate(queuesFound.size() * 8);

                  for (QueuesXRefInnerManager control : queuesFound) {
                     long queueID = control.getQueueID();
                     buffer.putLong(queueID);
                  }

                  message.putBytesProperty(Message.HDR_ROUTE_TO_IDS.toString(), buffer.array());

                  if (logger.isDebugEnabled()) {
                     if (messageReference.isPaged()) {
                        logger.debug("*********************<<<<< Scaling down pdgmessage {}", message);
                     } else {
                        logger.debug("*********************<<<<< Scaling down message {}", message);
                     }
                  }

                  producer.send(address, message);
                  messageCount++;
                  if (commitInterval > 0 && messageCount % commitInterval == 0) {
                     tx.commit();
                     tx = new TransactionImpl(storageManager);
                  }

                  messagesIterator.remove();

                  // We need to perform the ack / removal after sending, otherwise the message could been removed before the send is finished
                  for (QueuesXRefInnerManager queueFound : queuesFound) {
                     ackMessageOnQueue(tx, queueFound.getQueue(), messageReference);
                  }
               }
            } catch (NoSuchElementException ignored) {
               logger.debug(ignored.getMessage(), ignored);
               // this could happen through paging browsing
            }
         }

         tx.commit();

         for (QueuesXRefInnerManager controlRemoved : controls.values()) {
            controlRemoved.close();
         }

         return messageCount;
      } finally {
         if (pageStore != null) {
            pageStore.enableCleanup();
            pageStore.getCursorProvider().scheduleCleanup();
         }
      }
   }

   private long scaleDownSNF(final SimpleString address,
                             final Set<Queue> queues,
                             final ClientProducer producer) throws Exception {
      long messageCount = 0;

      final String propertyEnd;

      // If this SNF is towards our targetNodeId
      boolean queueOnTarget = address.toString().endsWith(targetNodeId);

      if (queueOnTarget) {
         propertyEnd = targetNodeId;
      } else {
         propertyEnd = address.toString().substring(address.toString().lastIndexOf("."));
      }

      Transaction tx = new TransactionImpl(storageManager);

      for (Queue queue : queues) {
         // using auto-closeable
         try (LinkedListIterator<MessageReference> messagesIterator = queue.browserIterator()) {
            // loop through every message of this queue
            while (messagesIterator.hasNext()) {
               MessageReference messageRef = messagesIterator.next();
               Message message = messageRef.getMessage().copy();

               /*
                * Here we are taking messages out of a store-and-forward queue and sending them to the corresponding
                * address on the scale-down target server.  However, we have to take the existing _AMQ_ROUTE_TOsf.*
                * property and put its value into the _AMQ_ROUTE_TO property so the message is routed properly.
                */

               byte[] oldRouteToIDs = null;

               List<SimpleString> propertiesToRemove = new ArrayList<>();
               message.removeProperty(Message.HDR_ROUTE_TO_IDS.toString());
               for (SimpleString propName : message.getPropertyNames()) {
                  if (propName.startsWith(Message.HDR_ROUTE_TO_IDS)) {
                     if (propName.toString().endsWith(propertyEnd)) {
                        oldRouteToIDs = message.getBytesProperty(propName.toString());
                     }
                     propertiesToRemove.add(propName);
                  }
               }

               // TODO: what if oldRouteToIDs == null ??

               for (SimpleString propertyToRemove : propertiesToRemove) {
                  message.removeProperty(propertyToRemove.toString());
               }

               if (queueOnTarget) {
                  message.putBytesProperty(Message.HDR_ROUTE_TO_IDS.toString(), oldRouteToIDs);
               } else {
                  message.putBytesProperty(Message.HDR_SCALEDOWN_TO_IDS.toString(), oldRouteToIDs);
               }

               if (logger.isDebugEnabled()) {
                  logger.debug("Scaling down message {} from {} to {} on node {}", message, address, message.getAddress(), targetNodeId);
               }

               producer.send(message.getAddress(), message);

               messageCount++;
               if (commitInterval > 0 && messageCount % commitInterval == 0) {
                  tx.commit();
                  tx = new TransactionImpl(storageManager);
               }

               messagesIterator.remove();

               ackMessageOnQueue(tx, queue, messageRef);
            }
         } catch (NoSuchElementException ignored) {
            // this could happen through paging browsing
         }
      }

      tx.commit();

      return messageCount;
   }

   private String getTargetNodeId(ClientSessionFactory sessionFactory) {
      return sessionFactory.getServerLocator().getTopology().getMember(sessionFactory.getConnection()).getNodeId();
   }

   public void scaleDownTransactions(ClientSessionFactory sessionFactory,
                                     ResourceManager resourceManager,
                                     String user,
                                     String password) throws Exception {
      ClientSession session = sessionFactory.createSession(user, password, true, false, false, false, 0);
      ClientSession queueCreateSession = sessionFactory.createSession(user, password, false, true, true, false, 0);
      List<Xid> preparedTransactions = resourceManager.getPreparedTransactions();
      Map<String, Long> queueIDs = new HashMap<>();
      for (Xid xid : preparedTransactions) {
         logger.debug("Scaling down transaction: {}", xid);
         Transaction transaction = resourceManager.getTransaction(xid);
         session.start(xid, XAResource.TMNOFLAGS);
         List<TransactionOperation> allOperations = transaction.getAllOperations();

         // Get the information of the Prepared TXs so it could replay the TXs
         Map<Message, Pair<List<Long>, List<Long>>> queuesToSendTo = new HashMap<>();
         for (TransactionOperation operation : allOperations) {
            if (operation instanceof PostOfficeImpl.AddOperation addOperation) {
               List<MessageReference> refs = addOperation.getRelatedMessageReferences();
               for (MessageReference ref : refs) {
                  Message message = ref.getMessage();
                  Queue queue = ref.getQueue();
                  long queueID;
                  String queueName = queue.getName().toString();

                  if (queueIDs.containsKey(queueName)) {
                     queueID = queueIDs.get(queueName);
                  } else {
                     queueID = createQueueWithRoutingTypeIfNecessaryAndGetID(queueCreateSession, queue, message.getAddressSimpleString(), message.getRoutingType());
                     queueIDs.put(queueName, queueID);  // store it so we don't have to look it up every time
                  }
                  Pair<List<Long>, List<Long>> queueIds = queuesToSendTo.get(message);
                  if (queueIds == null) {
                     queueIds = new Pair<>(new ArrayList<>(), new ArrayList<>());
                     queuesToSendTo.put(message, queueIds);
                  }
                  queueIds.getA().add(queueID);
               }
            } else if (operation instanceof RefsOperation refsOperation) {
               List<MessageReference> refs = refsOperation.getReferencesToAcknowledge();
               for (MessageReference ref : refs) {
                  Message message = ref.getMessage();
                  Queue queue = ref.getQueue();
                  long queueID;
                  String queueName = queue.getName().toString();

                  if (queueIDs.containsKey(queueName)) {
                     queueID = queueIDs.get(queueName);
                  } else {
                     queueID = createQueueWithRoutingTypeIfNecessaryAndGetID(queueCreateSession, queue, message.getAddressSimpleString(), message.getRoutingType());
                     queueIDs.put(queueName, queueID);  // store it so we don't have to look it up every time
                  }
                  Pair<List<Long>, List<Long>> queueIds = queuesToSendTo.get(message);
                  if (queueIds == null) {
                     queueIds = new Pair<>(new ArrayList<>(), new ArrayList<>());
                     queuesToSendTo.put(message, queueIds);
                  }
                  queueIds.getA().add(queueID);
                  queueIds.getB().add(queueID);
               }
            }
         }

         ClientProducer producer = session.createProducer();
         for (Map.Entry<Message, Pair<List<Long>, List<Long>>> entry : queuesToSendTo.entrySet()) {
            List<Long> ids = entry.getValue().getA();
            ByteBuffer buffer = ByteBuffer.allocate(ids.size() * 8);
            for (Long id : ids) {
               buffer.putLong(id);
            }
            Message message = entry.getKey();
            message.putBytesProperty(Message.HDR_ROUTE_TO_IDS.toString(), buffer.array());
            ids = entry.getValue().getB();
            if (!ids.isEmpty()) {
               buffer = ByteBuffer.allocate(ids.size() * 8);
               for (Long id : ids) {
                  buffer.putLong(id);
               }
               message.putBytesProperty(Message.HDR_ROUTE_TO_ACK_IDS.toString(), buffer.array());
            }
            producer.send(message.getAddressSimpleString().toString(), message);
         }
         session.end(xid, XAResource.TMSUCCESS);
         session.prepare(xid);
      }
   }

   public void scaleDownDuplicateIDs(Map<SimpleString, List<Pair<byte[], Long>>> duplicateIDMap,
                                     ClientSessionFactory sessionFactory,
                                     SimpleString managementAddress,
                                     String user,
                                     String password) throws Exception {
      try (ClientSession session = sessionFactory.createSession(user, password, true, false, false, false, 0);
           ClientProducer producer = session.createProducer(managementAddress)) {
         for (Map.Entry<SimpleString, List<Pair<byte[], Long>>> entry : duplicateIDMap.entrySet()) {
            ClientMessage message = session.createMessage(false);
            List<Pair<byte[], Long>> list = entry.getValue();
            String[] array = new String[list.size()];
            for (int i = 0; i < list.size(); i++) {
               Pair<byte[], Long> pair = list.get(i);
               array[i] = new String(pair.getA());
            }
            ManagementHelper.putOperationInvocation(message, ResourceNames.BROKER, "updateDuplicateIdCache", entry.getKey().toString(), array);
            producer.send(message);
         }
      }
   }

   /**
    * Get the ID of the queues involved so the message can be routed properly.  This is done because we cannot send
    * directly to a queue, we have to send to an address instead but not all the queues related to the address may need
    * the message
    */
   private long createQueueWithRoutingTypeIfNecessaryAndGetID(ClientSession session,
                                                              Queue queue,
                                                              SimpleString addressName,
                                                              RoutingType routingType) throws Exception {
      long queueID = getQueueID(session, queue.getName());
      if (queueID == -1) {
         session.createQueue(QueueConfiguration.of(queue.getName()).setAddress(addressName).setRoutingType(routingType).setFilterString(queue.getFilter() == null ? null : queue.getFilter().getFilterString()).setDurable(queue.isDurable()));
         if (logger.isDebugEnabled()) {
            logger.debug("Failed to get queue ID, creating queue [addressName={}, queueName={}, routingType={}, filter={}, durable={}]",
                      addressName, queue.getName(), queue.getRoutingType(), (queue.getFilter() == null ? "" : queue.getFilter().getFilterString()), queue.isDurable());
         }
         queueID = getQueueID(session, queue.getName());
      }

      if (logger.isDebugEnabled()) {
         logger.debug("ID for {} is: {}", queue, queueID);
      }

      return queueID;
   }

   private Long getQueueID(ClientSession session, SimpleString queueName) throws Exception {
      Long queueID = -1L;
      Object result;
      try (ClientRequestor requestor = new ClientRequestor(session, "activemq.management")) {
         ClientMessage managementMessage = session.createMessage(false);
         ManagementHelper.putAttribute(managementMessage, ResourceNames.QUEUE + queueName, "ID");
         session.start();
         logger.debug("Requesting ID for: {}", queueName);
         ClientMessage reply = requestor.request(managementMessage);
         result = ManagementHelper.getResult(reply);
      }
      if (result != null && result instanceof Number number) {
         queueID = number.longValue();
      }
      return queueID;
   }

   public static class OrderQueueByNumberOfReferencesComparator implements Comparator<Queue> {

      @Override
      public int compare(Queue queue1, Queue queue2) {
         final int BEFORE = -1;
         final int EQUAL = 0;
         final int AFTER = 1;
         int result = 0;

         if (queue1 == queue2)
            return EQUAL;

         if (queue1.getMessageCount() == queue2.getMessageCount()) {
            // if it's the same count we will use the ID as a tie breaker:

            long tieBreak = queue2.getID() - queue1.getID();

            if (tieBreak > 0)
               return AFTER;
            else if (tieBreak < 0)
               return BEFORE;
            else
               return EQUAL; // EQUAL here shouldn't really happen... but lets do the check anyways

         }
         if (queue1.getMessageCount() > queue2.getMessageCount())
            return BEFORE;
         if (queue1.getMessageCount() < queue2.getMessageCount())
            return AFTER;

         return result;
      }
   }

   private void ackMessageOnQueue(Transaction tx, Queue queue, MessageReference messageRef) throws Exception {
      queue.acknowledge(tx, messageRef);
   }

   /**
    * this class will control iterations while looking over for messages relations
    */
   private class QueuesXRefInnerManager {

      private final Queue queue;
      private LinkedListIterator<MessageReference> memoryIterator;
      private MessageReference lastRef = null;
      private final PagingStore store;

      /**
       * ClientSession used for looking up and creating queues
       */
      private final ClientSession clientSession;

      private long targetQueueID = -1;

      QueuesXRefInnerManager(final ClientSession clientSession, final Queue queue, final PagingStore store) {
         this.queue = queue;
         this.store = store;
         this.clientSession = clientSession;
      }

      public Queue getQueue() {
         return queue;
      }

      public long getQueueID() throws Exception {

         if (targetQueueID < 0) {
            targetQueueID = createQueueWithRoutingTypeIfNecessaryAndGetID(clientSession, queue, queue.getAddress(), queue.getRoutingType());
         }
         return targetQueueID;
      }

      public void close() {
         if (memoryIterator != null) {
            memoryIterator.close();
         }
      }

      public boolean lookup(MessageReference reference) throws Exception {

         if (reference.isPaged()) {
            if (store == null) {
               return false;
            }
            PageSubscription subscription = store.getCursorProvider().getSubscription(queue.getID());
            if (subscription.contains((PagedReference) reference)) {
               return true;
            }
         } else {

            if (lastRef != null && lastRef.getMessage().equals(reference.getMessage())) {
               lastRef = null;
               memoryIterator.remove();
               return true;
            }

            int numberOfScans = 2;

            if (memoryIterator == null) {
               // If we have a brand new iterator, and we can't find something
               numberOfScans = 1;
            }

            MessageReference initialRef = null;
            for (int i = 0; i < numberOfScans; i++) {
               logger.debug("Iterating on queue {} while looking for reference {}", queue, reference);
               memoryIterator = queue.iterator();

               while (memoryIterator.hasNext()) {
                  lastRef = memoryIterator.next();

                  logger.debug("Iterating on message {}", lastRef);

                  if (lastRef.getMessage().equals(reference.getMessage())) {
                     memoryIterator.remove();
                     lastRef = null;
                     return true;
                  }

                  if (initialRef == null) {
                     initialRef = lastRef;
                  } else {
                     if (initialRef.equals(lastRef)) {
                        if (!memoryIterator.hasNext()) {
                           // if by coincidence we are at the end of the iterator, we just reset the iterator
                           lastRef = null;
                           memoryIterator.close();
                           memoryIterator = null;
                        }
                        return false;
                     }
                  }
               }
            }

         }

         // if we reached two iterations without finding anything.. we just go away by cleaning everything up
         lastRef = null;
         if (memoryIterator != null) {
            memoryIterator.close();
            memoryIterator = null;
         }

         return false;
      }

   }

}

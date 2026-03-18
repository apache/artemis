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
package org.apache.activemq.artemis.core.server.group.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.CoreNotificationType;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.postoffice.BindingType;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.management.ManagementService;
import org.apache.activemq.artemis.core.server.management.Notification;
import org.apache.activemq.artemis.utils.ConcurrentUtil;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.apache.activemq.artemis.utils.collections.TypedProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * A Local Grouping handler. All the Remote handlers will talk with us
 */
public final class LocalGroupingHandler extends GroupHandlingAbstract {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final ConcurrentMap<SimpleString, GroupBinding> map = new ConcurrentHashMap<>();

   private final ConcurrentMap<SimpleString, List<GroupBinding>> groupMap = new ConcurrentHashMap<>();

   private final SimpleString name;

   private final StorageManager storageManager;

   private final long timeout;

   private final Lock lock = new ReentrantLock();

   private final Condition awaitCondition = lock.newCondition();

   /**
    * This contains a list of expected bindings to be loaded when the group is waiting for them. During a small window
    * between the server is started and the wait wasn't called yet, this will contain bindings that were already added
    */
   private List<SimpleString> expectedBindings = new LinkedList<>();

   private final long groupTimeout;

   private boolean waitingForBindings = false;

   private final ScheduledExecutorService scheduledExecutor;

   private boolean started;

   private ScheduledFuture reaperFuture;

   private final long reaperPeriod;

   public LocalGroupingHandler(final ExecutorFactory executorFactory,
                               final ScheduledExecutorService scheduledExecutor,
                               final ManagementService managementService,
                               final SimpleString name,
                               final SimpleString address,
                               final StorageManager storageManager,
                               final long timeout,
                               final long groupTimeout,
                               long reaperPeriod) {
      super(executorFactory.getExecutor(), managementService, address);
      this.reaperPeriod = reaperPeriod;
      this.scheduledExecutor = scheduledExecutor;
      this.name = name;
      this.storageManager = storageManager;
      this.timeout = timeout;
      this.groupTimeout = groupTimeout;
   }

   @Override
   public SimpleString getName() {
      return name;
   }

   @Override
   public Response propose(final Proposal proposal) throws Exception {
      OperationContext originalCtx = storageManager.getContext();

      try {
         // the waitCompletion cannot be done inside an ordered executor or we would starve when the thread pool is full
         storageManager.setContext(storageManager.newSingleThreadContext());

         if (proposal.getClusterName() == null) {
            GroupBinding original = map.get(proposal.getGroupId());
            if (original != null) {
               original.use();
               return new Response(proposal.getGroupId(), original.getClusterName());
            } else {
               return null;
            }
         }

         boolean addRecord = false;

         GroupBinding groupBinding = null;
         lock.lock();
         try {
            groupBinding = map.get(proposal.getGroupId());
            if (groupBinding != null) {
               groupBinding.use();
               // Returning with an alternate cluster name, as it's been already grouped
               return new Response(groupBinding.getGroupId(), proposal.getClusterName(), groupBinding.getClusterName());
            } else {
               addRecord = true;
               groupBinding = new GroupBinding(proposal.getGroupId(), proposal.getClusterName());
               groupBinding.setId(storageManager.generateID());
               List<GroupBinding> newList = new ArrayList<>();
               List<GroupBinding> oldList = groupMap.putIfAbsent(groupBinding.getClusterName(), newList);
               if (oldList != null) {
                  newList = oldList;
               }
               newList.add(groupBinding);
               map.put(groupBinding.getGroupId(), groupBinding);
            }
         } finally {
            lock.unlock();
         }
         // Storing the record outside of any locks
         if (addRecord) {
            storageManager.addGrouping(groupBinding);
         }
         return new Response(groupBinding.getGroupId(), groupBinding.getClusterName());
      } finally {
         storageManager.setContext(originalCtx);
      }
   }

   @Override
   public void resendPending() throws Exception {
      // this only make sense on RemoteGroupingHandler.
      // this is a no-op on the local one
   }

   @Override
   public void proposed(final Response response) throws Exception {
   }

   @Override
   public void remove(SimpleString groupid, SimpleString clusterName, int distance) throws Exception {
      remove(groupid, clusterName);
   }

   @Override
   public void sendProposalResponse(final Response response, final int distance) throws Exception {
      TypedProperties props = new TypedProperties();
      props.putSimpleStringProperty(ManagementHelper.HDR_PROPOSAL_GROUP_ID, response.getGroupId());
      props.putSimpleStringProperty(ManagementHelper.HDR_PROPOSAL_VALUE, response.getClusterName());
      props.putSimpleStringProperty(ManagementHelper.HDR_PROPOSAL_ALT_VALUE, response.getAlternativeClusterName());
      props.putIntProperty(ManagementHelper.HDR_BINDING_TYPE, BindingType.LOCAL_QUEUE_INDEX);
      props.putSimpleStringProperty(ManagementHelper.HDR_ADDRESS, address);
      props.putIntProperty(ManagementHelper.HDR_DISTANCE, distance);
      Notification notification = new Notification(null, CoreNotificationType.PROPOSAL_RESPONSE, props);
      managementService.sendNotification(notification);
   }

   @Override
   public Response receive(final Proposal proposal, final int distance) throws Exception {
      logger.trace("received proposal {}", proposal);
      return propose(proposal);
   }

   @Override
   public void addGroupBinding(final GroupBinding groupBinding) {
      map.put(groupBinding.getGroupId(), groupBinding);
      List<GroupBinding> newList = new ArrayList<>();
      List<GroupBinding> oldList = groupMap.putIfAbsent(groupBinding.getClusterName(), newList);
      if (oldList != null) {
         newList = oldList;
      }
      newList.add(groupBinding);
   }

   @Override
   public Response getProposal(final SimpleString fullID, final boolean touchTime) {
      GroupBinding original = map.get(fullID);

      if (original != null) {
         if (touchTime) {
            original.use();
         }
         return new Response(fullID, original.getClusterName());
      } else {
         return null;
      }
   }

   @Override
   public void remove(SimpleString groupid, SimpleString clusterName) {
      GroupBinding groupBinding = map.remove(groupid);
      List<GroupBinding> groupBindings = groupMap.get(clusterName);
      if (groupBindings != null && groupBinding != null) {
         groupBindings.remove(groupBinding);
         try {
            long tx = storageManager.generateID();
            storageManager.deleteGrouping(tx, groupBinding);
            storageManager.commitBindings(tx);
         } catch (Exception e) {
            // nothing we can do being log
            logger.warn(e.getMessage(), e);
         }
      }
   }

   @Override
   public void awaitBindings() throws Exception {
      lock.lock();
      try {
         if (!groupMap.isEmpty()) {
            waitingForBindings = true;

            //make a copy of the bindings added so far from the cluster via onNotification()
            List<SimpleString> bindingsAlreadyAdded;
            if (expectedBindings == null) {
               bindingsAlreadyAdded = Collections.emptyList();
               expectedBindings = new LinkedList<>();
            } else {
               bindingsAlreadyAdded = new ArrayList<>(expectedBindings);
               //clear the bindings
               expectedBindings.clear();
            }
            //now add all the group bindings that were loaded by the journal
            expectedBindings.addAll(groupMap.keySet());
            //and if we remove persisted bindings from whats been added so far we have left any bindings we havent yet
            //received via onNotification
            expectedBindings.removeAll(bindingsAlreadyAdded);

            if (!expectedBindings.isEmpty()) {
               logger.debug("Waiting remote group bindings to arrive before starting the server. timeout={} milliseconds", timeout);
               //now we wait here for the rest to be received in onNotification, it will signal once all have been received.
               //if we aren't signaled then bindingsAdded still has some groupids we need to remove.
               if (!ConcurrentUtil.await(awaitCondition, timeout)) {
                  ActiveMQServerLogger.LOGGER.remoteGroupCoordinatorsNotStarted();
               }
            }
         }
      } finally {
         expectedBindings = null;
         waitingForBindings = false;
         lock.unlock();
      }
   }

   @Override
   public void onNotification(final Notification notification) {
      if (!(notification.getType() instanceof CoreNotificationType)) {
         return;
      }

      if (notification.getType() == CoreNotificationType.BINDING_REMOVED) {
         SimpleString clusterName = notification.getProperties().getSimpleStringProperty(ManagementHelper.HDR_CLUSTER_NAME);
         removeGrouping(clusterName);
      } else if (notification.getType() == CoreNotificationType.BINDING_ADDED) {
         SimpleString clusterName = notification.getProperties().getSimpleStringProperty(ManagementHelper.HDR_CLUSTER_NAME);
         try {
            lock.lock();

            if (expectedBindings != null) {
               if (waitingForBindings) {
                  if (expectedBindings.remove(clusterName)) {
                     logger.debug("OnNotification for waitForbindings::Removed clusterName={} from list succesffully", clusterName);
                  } else {
                     logger.debug("OnNotification for waitForbindings::Couldn't remove clusterName={} as it wasn't on the original list", clusterName);
                  }
               } else {
                  expectedBindings.add(clusterName);
                  logger.debug("Notification for waitForbindings::Adding previously known item clusterName={}", clusterName);
               }

               if (logger.isDebugEnabled()) {
                  for (SimpleString stillWaiting : expectedBindings) {
                     logger.debug("Notification for waitForbindings::Still waiting for clusterName={}", stillWaiting);
                  }
               }

               if (expectedBindings.isEmpty()) {
                  awaitCondition.signal();
               }
            }
         } finally {
            lock.unlock();
         }
      }
   }

   @Override
   public synchronized void start() throws Exception {
      if (started) {
         return;
      }

      lock.lock();

      try {
         if (expectedBindings == null) {
            // just in case the component is restarted
            expectedBindings = new LinkedList<>();
         }
      } finally {
         lock.unlock();
      }

      if (reaperPeriod > 0 && groupTimeout > 0) {
         if (reaperFuture != null) {
            reaperFuture.cancel(true);
            reaperFuture = null;
         }

         reaperFuture = scheduledExecutor.scheduleAtFixedRate(new GroupReaperScheduler(), reaperPeriod, reaperPeriod, TimeUnit.MILLISECONDS);
      }
      started = true;
   }

   @Override
   public synchronized void stop() throws Exception {
      started = false;
      if (reaperFuture != null) {
         reaperFuture.cancel(true);
         reaperFuture = null;
      }
   }

   @Override
   public boolean isStarted() {
      return started;
   }

   private void removeGrouping(final SimpleString clusterName) {
      final List<GroupBinding> list = groupMap.remove(clusterName);
      if (list != null) {
         executor.execute(() -> {
            long txID = -1;

            for (GroupBinding val : list) {
               if (val != null) {
                  fireUnproposed(val.getGroupId());
                  map.remove(val.getGroupId());

                  sendUnproposal(val.getGroupId(), clusterName, 0);

                  try {
                     if (txID < 0) {
                        txID = storageManager.generateID();
                     }
                     storageManager.deleteGrouping(txID, val);
                  } catch (Exception e) {
                     ActiveMQServerLogger.LOGGER.unableToDeleteGroupBindings(val.getGroupId(), e);
                  }
               }
            }

            if (txID >= 0) {
               try {
                  storageManager.commitBindings(txID);
               } catch (Exception e) {
                  ActiveMQServerLogger.LOGGER.unableToDeleteGroupBindings(SimpleString.of("TX:" + txID), e);
               }
            }
         });

      }
   }

   private final class GroupReaperScheduler implements Runnable {

      final GroupIdReaper reaper = new GroupIdReaper();

      @Override
      public void run() {
         executor.execute(reaper);
      }

   }

   private final class GroupIdReaper implements Runnable {

      @Override
      public void run() {
         // The reaper thread should be finished case the PostOffice is gone
         // This is to avoid leaks on PostOffice between stops and starts
         if (isStarted()) {
            long txID = -1;

            int expiredGroups = 0;

            for (GroupBinding groupBinding : map.values()) {
               if ((groupBinding.getTimeUsed() + groupTimeout) < System.currentTimeMillis()) {
                  map.remove(groupBinding.getGroupId());
                  List<GroupBinding> groupBindings = groupMap.get(groupBinding.getClusterName());

                  groupBindings.remove(groupBinding);

                  fireUnproposed(groupBinding.getGroupId());

                  sendUnproposal(groupBinding.getGroupId(), groupBinding.getClusterName(), 0);

                  expiredGroups++;
                  try {
                     if (txID < 0) {
                        txID = storageManager.generateID();
                     }
                     storageManager.deleteGrouping(txID, groupBinding);

                     if (expiredGroups >= 1000 && txID >= 0) {
                        storageManager.commitBindings(txID);
                        expiredGroups = 0;
                        txID = -1;
                     }
                  } catch (Exception e) {
                     ActiveMQServerLogger.LOGGER.unableToDeleteGroupBindings(groupBinding.getGroupId(), e);
                  }
               }
            }

            if (txID >= 0) {
               try {
                  storageManager.commitBindings(txID);
               } catch (Exception e) {
                  ActiveMQServerLogger.LOGGER.unableToDeleteGroupBindings(SimpleString.of("TX:" + txID), e);
               }
            }
         }
      }
   }

}

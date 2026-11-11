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

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.management.CoreNotificationType;
import org.apache.activemq.artemis.api.core.management.ManagementHelper;
import org.apache.activemq.artemis.core.postoffice.BindingType;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.group.GroupingHandler;
import org.apache.activemq.artemis.core.server.group.UnproposalListener;
import org.apache.activemq.artemis.core.server.management.ManagementService;
import org.apache.activemq.artemis.core.server.management.Notification;
import org.apache.activemq.artemis.utils.collections.TypedProperties;

public abstract class GroupHandlingAbstract implements GroupingHandler {

   protected final Executor executor;

   protected final ManagementService managementService;

   protected final SimpleString address;

   // no need to synchronize listeners as we use a single threaded executor on all its accesses
   final Set<UnproposalListener> listeners = Collections.newSetFromMap(new WeakHashMap<>());

   public GroupHandlingAbstract(final Executor executor,
                                final ManagementService managementService,
                                final SimpleString address) {
      this.executor = executor;
      this.managementService = managementService;
      this.address = address;
   }

   @Override
   public void addListener(final UnproposalListener listener) {
      if (executor == null) {
         listeners.add(listener);
      } else {
         executor.execute(() -> listeners.add(listener));
      }
   }

   protected void fireUnproposed(final SimpleString groupID) {

      Runnable runnable = () -> {
         for (UnproposalListener listener : listeners) {
            listener.unproposed(groupID);
         }
      };
      if (executor != null) {
         executor.execute(runnable);
      } else {
         // for tests only, where we don't need an executor
         runnable.run();
      }
   }

   @Override
   public void forceRemove(SimpleString groupid, SimpleString clusterName) throws Exception {
      remove(groupid, clusterName);
      sendUnproposal(groupid, clusterName, 0);
   }

   protected void sendUnproposal(SimpleString groupid, SimpleString clusterName, int distance) {
      TypedProperties props = new TypedProperties();
      props.putSimpleStringProperty(ManagementHelper.HDR_PROPOSAL_GROUP_ID, groupid);
      props.putSimpleStringProperty(ManagementHelper.HDR_PROPOSAL_VALUE, clusterName);
      props.putIntProperty(ManagementHelper.HDR_BINDING_TYPE, BindingType.LOCAL_QUEUE_INDEX);
      props.putSimpleStringProperty(ManagementHelper.HDR_ADDRESS, address);
      props.putIntProperty(ManagementHelper.HDR_DISTANCE, distance);
      Notification notification = new Notification(null, CoreNotificationType.UNPROPOSAL, props);
      try {
         managementService.sendNotification(notification);
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.errorHandlingMessage(e);
      }
   }

}

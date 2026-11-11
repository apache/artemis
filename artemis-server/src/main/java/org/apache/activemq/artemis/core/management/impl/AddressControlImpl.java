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
package org.apache.activemq.artemis.core.management.impl;

import javax.management.MBeanAttributeInfo;
import javax.management.MBeanOperationInfo;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.management.AddressControl;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.core.paging.PagingManager;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.Bindings;
import org.apache.activemq.artemis.core.postoffice.DuplicateIDCache;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.postoffice.impl.PostOfficeImpl;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.security.SecurityStore;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.cluster.RemoteQueueBinding;
import org.apache.activemq.artemis.core.server.impl.AckReason;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.QueueImpl;
import org.apache.activemq.artemis.core.server.management.ManagementService;
import org.apache.activemq.artemis.core.server.replay.ReplayManager;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.json.JsonArrayBuilder;
import org.apache.activemq.artemis.logs.AuditLogger;
import org.apache.activemq.artemis.utils.JsonLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class AddressControlImpl extends AbstractControl implements AddressControl {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private AddressInfo addressInfo;

   private final ActiveMQServer server;

   private final PagingManager pagingManager;

   private final HierarchicalRepository<Set<Role>> securityRepository;

   private final SecurityStore securityStore;

   private final ManagementService managementService;


   public AddressControlImpl(AddressInfo addressInfo,
                             final ActiveMQServer server,
                             final PagingManager pagingManager,
                             final StorageManager storageManager,
                             final HierarchicalRepository<Set<Role>> securityRepository,
                             final SecurityStore securityStore,
                             final ManagementService managementService)throws Exception {
      super(AddressControl.class, storageManager);
      this.server = server;
      this.addressInfo = addressInfo;
      this.pagingManager = pagingManager;
      this.securityRepository = securityRepository;
      this.securityStore = securityStore;
      this.managementService = managementService;
   }


   // AddressControlMBean implementation ----------------------------

   @Override
   public long getId() {
      return addressInfo.getId();
   }

   @Override
   public String getAddress() {
      return addressInfo.getName().toString();
   }

   @Override
   public String[] getRoutingTypes() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getRoutingTypes(this.addressInfo);
      }
      EnumSet<RoutingType> routingTypes = addressInfo.getRoutingTypes();
      String[] result = new String[routingTypes.size()];
      int i = 0;
      for (RoutingType routingType : routingTypes) {
         result[i++] = routingType.toString();
      }
      return result;
   }

   @Override
   public String getRoutingTypesAsJSON() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getRoutingTypesAsJSON(this.addressInfo);
      }

      clearIO();
      try {
         JsonArrayBuilder json = JsonLoader.createArrayBuilder();
         String[] routingTypes = getRoutingTypes();

         for (String routingType : routingTypes) {
            json.add(routingType);
         }
         return json.build().toString();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String[] getRemoteQueueNames() {
      return getQueueNames(SearchType.REMOTE);
   }

   @Override
   public String[] getQueueNames() {
      return getQueueNames(SearchType.LOCAL);
   }

   @Override
   public String[] getAllQueueNames() {
      return getQueueNames(SearchType.ALL);
   }

   enum SearchType {
      LOCAL, REMOTE, ALL
   }

   private String[] getQueueNames(SearchType searchType) {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getQueueNames(this.addressInfo, searchType);
      }
      clearIO();
      try {
         Bindings bindings = server.getPostOffice().lookupBindingsForAddress(addressInfo.getName());
         if (bindings != null) {
            List<String> queueNames = new ArrayList<>();
            for (Binding binding : bindings.getBindings()) {
               if (binding instanceof QueueBinding && ((searchType == SearchType.ALL) || (searchType == SearchType.LOCAL && binding.isLocal()) || (searchType == SearchType.REMOTE && binding instanceof RemoteQueueBinding))) {
                  queueNames.add(binding.getUniqueName().toString());
               }
            }
            return queueNames.toArray(new String[queueNames.size()]);
         } else {
            return new String[0];
         }
      } catch (Throwable t) {
         throw new IllegalStateException(t.getMessage());
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String[] getBindingNames() throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getBindingNames(this.addressInfo);
      }
      try {
         clearIO();

         Bindings bindings = server.getPostOffice().lookupBindingsForAddress(addressInfo.getName());
         if (bindings != null) {
            String[] bindingNames = new String[bindings.getBindings().size()];
            int i = 0;
            for (Binding binding : bindings.getBindings()) {
               bindingNames[i++] = binding.getUniqueName().toString();
            }
            return bindingNames;
         } else {
            return new String[0];
         }
      } catch (Throwable t) {
         throw new IllegalStateException(t.getMessage());
      } finally {
         blockOnIO();
      }
   }

   @Override
   public Object[] getRoles() throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getRoles(this.addressInfo);
      }
      clearIO();
      try {
         Set<Role> roles = securityRepository.getMatch(addressInfo.getName().toString());

         Object[] objRoles = new Object[roles.size()];

         int i = 0;
         for (Role role : roles) {
            objRoles[i++] = CheckType.asObjectArray(role);
         }
         return objRoles;
      } finally {
         blockOnIO();
      }
   }

   @Override
   public String getRolesAsJSON() throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getRolesAsJSON(this.addressInfo);
      }
      clearIO();
      try {
         JsonArrayBuilder json = JsonLoader.createArrayBuilder();
         Set<Role> roles = securityRepository.getMatch(addressInfo.getName().toString());

         for (Role role : roles) {
            json.add(role.toJson());
         }
         return json.build().toString();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public long getNumberOfBytesPerPage() throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getNumberOfBytesPerPage(this.addressInfo);
      }
      clearIO();
      try {
         final PagingStore pagingStore = getPagingStore();
         if (pagingStore == null) {
            return 0;
         }
         return pagingStore.getPageSizeBytes();
      } finally {
         blockOnIO();
      }
   }

   private PagingStore getPagingStore() throws Exception {
      return pagingManager.getPageStore(addressInfo.getName());
   }

   @Override
   public long getAddressSize() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getAddressSize(this.addressInfo);
      }
      clearIO();
      try {
         final PagingStore pagingStore = getPagingStore();
         if (pagingStore == null) {
            return 0;
         }
         return pagingStore.getAddressSize();
      } catch (Exception e) {
         logger.debug("Failed to get address size", e);
         return -1;
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getMaxPageReadBytes() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getMaxPageReadBytes(this.addressInfo);
      }
      clearIO();
      try {
         final PagingStore pagingStore = getPagingStore();
         if (pagingStore == null) {
            return 0;
         }
         return pagingStore.getMaxPageReadBytes();
      } catch (Exception e) {
         logger.debug("Failed to get attribute value", e);
         return -1;
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getMaxPageReadMessages() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getMaxPageReadMessages(this.addressInfo);
      }
      clearIO();
      try {
         final PagingStore pagingStore = getPagingStore();
         if (pagingStore == null) {
            return 0;
         }
         return pagingStore.getMaxPageReadMessages();
      } catch (Exception e) {
         logger.debug("Failed to get attribute value", e);
         return -1;
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getPrefetchPageBytes() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getPrefetchPageBytes(this.addressInfo);
      }
      clearIO();
      try {
         final PagingStore pagingStore = getPagingStore();
         if (pagingStore == null) {
            return 0;
         }
         return pagingStore.getPrefetchPageBytes();
      } catch (Exception e) {
         logger.debug("Failed to get attribute value", e);
         return -1;
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getPrefetchPageMessages() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getPrefetchPageMessages(this.addressInfo);
      }
      clearIO();
      try {
         final PagingStore pagingStore = getPagingStore();
         if (pagingStore == null) {
            return 0;
         }
         return pagingStore.getPrefetchPageMessages();
      } catch (Exception e) {
         logger.debug("Failed to get attribute value", e);
         return -1;
      } finally {
         blockOnIO();
      }
   }


   @Override
   public void schedulePageCleanup() throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.schedulePageCleanup(this.addressInfo);
      }
      clearIO();
      try {
         final PagingStore pagingStore = getPagingStore();
         if (pagingStore == null) {
            return;
         }
         pagingStore.getCursorProvider().scheduleCleanup();
      } catch (Exception e) {
         logger.debug("Failed to schedule pageCleanup", e);
         throw e;
      } finally {
         blockOnIO();
      }
   }

   @Override
   @Deprecated
   public long getNumberOfMessages() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getNumberOfMessages(this.addressInfo);
      }
      clearIO();
      try {
         return getMessageCount();
      } catch (Throwable t) {
         throw new IllegalStateException(t.getMessage());
      } finally {
         blockOnIO();
      }
   }

   @Override
   public boolean isPaging() throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.isPaging(this.addressInfo);
      }
      clearIO();
      try {
         final PagingStore pagingStore = getPagingStore();
         if (pagingStore == null) {
            return false;
         }
         return pagingStore.isPaging();
      } finally {
         blockOnIO();
      }
   }

   @Override
   public int getAddressLimitPercent() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getAddressLimitPercent(this.addressInfo);
      }
      clearIO();
      try {
         final PagingStore pagingStore = getPagingStore();
         if (pagingStore == null) {
            return 0;
         }
         return pagingStore.getAddressLimitPercent();
      } catch (Exception e) {
         logger.debug("Failed to get address limit %", e);
         return -1;
      } finally {
         blockOnIO();
      }
   }

   @Override
   public boolean block() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.block(this.addressInfo);
      }
      clearIO();
      boolean result = false;
      try {
         final PagingStore pagingStore = getPagingStore();
         if (pagingStore != null) {
            pagingStore.block();
            result = true;
         }
      } catch (Exception e) {
         logger.debug("Failed to block", e);

      } finally {
         blockOnIO();
      }
      return result;
   }

   @Override
   public void unblock() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.unBlock(this.addressInfo);
      }
      clearIO();
      try {
         final PagingStore pagingStore = getPagingStore();
         if (pagingStore != null) {
            pagingStore.unblock();
         }
      } catch (Exception e) {
         logger.debug("Failed to unblock", e);
      } finally {
         blockOnIO();
      }
   }

   @Override
   public boolean isBlockedViaManagement() throws Exception {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.isBlockedViaManagement(this.addressInfo);
      }
      clearIO();
      try {
         final PagingStore pagingStore = getPagingStore();
         if (pagingStore != null) {
            return pagingStore.isBlockedViaManagement();
         } else {
            return false;
         }
      } catch (Exception e) {
         return false;
      } finally {
         blockOnIO();
      }
   }

   @Override
   public long getNumberOfPages() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getNumberOfPages(this.addressInfo);
      }
      clearIO();
      try {
         final PagingStore pageStore = getPagingStore();

         if (pageStore == null || !pageStore.isStorePaging()) {
            return 0;
         } else {
            return pageStore.getNumberOfPages();
         }
      } catch (Exception e) {
         logger.debug("Failed to get number of pages", e);
         return -1;
      } finally {
         blockOnIO();
      }
   }

   @Override
   public long getMessageCount() {
      // prevent parallel tasks running
      try (AutoCloseable lock = server.managementLock()) {
         if (AuditLogger.isBaseLoggingEnabled()) {
            AuditLogger.getMessageCount(this.addressInfo);
         }
         return getMessageCount(DurabilityType.ALL);
      } catch (Exception e) {
         throw new RuntimeException(e.getMessage(), e);
      }
   }

   @Override
   public long getQueueCount() {
      // prevent parallel tasks running
      try (AutoCloseable lock = server.managementLock()) {
         if (AuditLogger.isBaseLoggingEnabled()) {
            AuditLogger.getQueueCount(this.addressInfo);
         }
         return getQueueNames(SearchType.LOCAL).length;
      } catch (Exception e) {
         throw new RuntimeException(e.getMessage(), e);
      }
   }

   @Override
   public long getRoutedMessageCount() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getRoutedMessageCount(this.addressInfo);
      }
      return addressInfo.getRoutedMessageCount();
   }

   @Override
   public long getUnRoutedMessageCount() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getUnRoutedMessageCount(this.addressInfo);
      }
      return addressInfo.getUnRoutedMessageCount();
   }


   @Override
   public String sendMessage(final Map<String, String> headers,
                             final int type,
                             final String body,
                             boolean durable,
                             final String user,
                             final String password) throws Exception {
      return sendMessage(headers, type, body, durable, user, password, false);
   }

   @Override
   public String sendMessage(final Map<String, String> headers,
                             final int type,
                             final String body,
                             boolean durable,
                             final String user,
                             final String password,
                             boolean createMessageId) throws Exception {
      // prevent parallel tasks running
      try (AutoCloseable lock = server.managementLock()) {
         if (AuditLogger.isBaseLoggingEnabled()) {
            AuditLogger.sendMessageThroughManagement(this, headers, type, body, durable, user, "****");
         }
         try {
            return sendMessage(addressInfo.getName(), server, headers, type, body, durable, user, password, createMessageId);
         } catch (Exception e) {
            logger.debug("Failed to sendMessage", e);
            throw new IllegalStateException(e.getMessage());
         }
      }
   }


   @Override
   protected MBeanOperationInfo[] fillMBeanOperationInfo() {
      return MBeanInfoHelper.getMBeanOperationsInfo(AddressControl.class);
   }

   @Override
   protected MBeanAttributeInfo[] fillMBeanAttributeInfo() {
      return MBeanInfoHelper.getMBeanAttributesInfo(AddressControl.class);
   }

   @Override
   public void pause() {
      pause(false);
   }

   @Override
   public void pause(boolean persist) {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.pause(addressInfo);
      }
      checkStarted();

      clearIO();
      try {
         addressInfo.setPostOffice(server.getPostOffice());
         addressInfo.setStorageManager(server.getStorageManager());
         addressInfo.pause(persist);
         if (AuditLogger.isResourceLoggingEnabled()) {
            AuditLogger.pauseAddressSuccess(addressInfo.getName().toString());
         }
      } catch (Exception e) {
         if (AuditLogger.isResourceLoggingEnabled()) {
            AuditLogger.pauseAddressFailure(addressInfo.getName().toString());
         }
         throw e;
      } finally {
         blockOnIO();
      }
   }


   @Override
   public void resume() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.resume(addressInfo);
      }
      checkStarted();

      clearIO();
      try {
         addressInfo.setPostOffice(server.getPostOffice());
         addressInfo.setStorageManager(server.getStorageManager());
         addressInfo.resume();
         if (AuditLogger.isResourceLoggingEnabled()) {
            AuditLogger.resumeAddressSuccess(addressInfo.getName().toString());
         }
      } catch (Exception e) {
         if (AuditLogger.isResourceLoggingEnabled()) {
            AuditLogger.resumeAddressFailure(addressInfo.getName().toString());
         }
         throw e;
      } finally {
         blockOnIO();
      }
   }

   @Override
   public boolean isPaused() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.isPaused(this.addressInfo);
      }
      return addressInfo.isPaused();
   }

   @Override
   public boolean isRetroactiveResource() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.isRetroactiveResource(this.addressInfo);
      }
      return ResourceNames.isRetroactiveResource(server.getInternalNamingPrefix(), addressInfo.getName());
   }

   @Override
   public long getCurrentDuplicateIdCacheSize() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.getCurrentDuplicateIdCacheSize(this.addressInfo);
      }
      DuplicateIDCache cache = ((PostOfficeImpl)server.getPostOffice()).getDuplicateIDCaches().get(addressInfo.getName());
      try {
         if (cache != null) {
            return cache.getMap().size();
         }
      } catch (Exception e) {
         logger.debug("Failed to get duplicate ID cache size", e);
      }

      return 0;
   }

   @Override
   public boolean clearDuplicateIdCache() {
      // prevent parallel tasks running
      try (AutoCloseable lock = server.managementLock()) {
         if (AuditLogger.isBaseLoggingEnabled()) {
            AuditLogger.clearDuplicateIdCache(this.addressInfo);
         }
         DuplicateIDCache cache = ((PostOfficeImpl) server.getPostOffice()).getDuplicateIDCaches().get(addressInfo.getName());
         try {
            if (cache != null) {
               cache.clear();
               return true;
            }
         } catch (Exception e) {
            logger.debug("Failed to clear duplicate ID cache", e);
         }

         return false;
      } catch (Exception e) {
         throw new RuntimeException(e.getMessage(), e);
      }
   }

   @Override
   public boolean isAutoCreated() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.isAutoCreated(this.addressInfo);
      }
      return addressInfo.isAutoCreated();
   }

   @Override
   public boolean isInternal() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.isInternal(this.addressInfo);
      }
      return addressInfo.isInternal();
   }

   @Override
   public boolean isTemporary() {
      if (AuditLogger.isBaseLoggingEnabled()) {
         AuditLogger.isTemporary(this.addressInfo);
      }
      return addressInfo.isTemporary();
   }

   @Override
   public long purge() throws Exception {
      // prevent parallel tasks running
      try (AutoCloseable lock = server.managementLock()) {
         if (AuditLogger.isBaseLoggingEnabled()) {
            AuditLogger.purge(this.addressInfo);
         }
         clearIO();
         long totalMsgs = 0;
         try {
            Bindings bindings = server.getPostOffice().lookupBindingsForAddress(addressInfo.getName());
            if (bindings != null) {
               for (Binding binding : bindings.getBindings()) {
                  if (binding instanceof QueueBinding queueBinding) {
                     totalMsgs += queueBinding.getQueue().deleteMatchingReferences(QueueImpl.DEFAULT_FLUSH_LIMIT, null, AckReason.KILLED);
                  }
               }
            }
            if (AuditLogger.isResourceLoggingEnabled()) {
               AuditLogger.purgeAddressSuccess(addressInfo.getName().toString());
            }
         } catch (Throwable t) {
            if (AuditLogger.isResourceLoggingEnabled()) {
               AuditLogger.purgeAddressFailure(addressInfo.getName().toString());
            }
            throw new IllegalStateException(t.getMessage());
         } finally {
            blockOnIO();
         }

         return totalMsgs;
      }
   }

   @Override
   public void replay(String target, String filter) throws Exception {
      // server.replay is already calling the managementLock, no need to do it here
      server.replay(null, null, this.getAddress(), target, filter);
   }

   @Override
   public void replay(String startScan, String endScan, String target, String filter) throws Exception {

      SimpleDateFormat format = ReplayManager.newRetentionSimpleDateFormat();

      Date startScanDate = format.parse(startScan);
      Date endScanDate = format.parse(endScan);

      // server.replay is already calling the managementLock, no need to do it here
      server.replay(startScanDate, endScanDate, this.getAddress(), target, filter);
   }


   private long getMessageCount(final DurabilityType durability) {
      // prevent parallel tasks running
      try (AutoCloseable lock = server.managementLock()) {
         long count = 0;
         for (String queueName : getQueueNames()) {
            Queue queue = server.locateQueue(queueName);
            if (queue != null && (durability == DurabilityType.ALL || (durability == DurabilityType.DURABLE && queue.isDurable()) || (durability == DurabilityType.NON_DURABLE && !queue.isDurable()))) {
               count += queue.getMessageCount();
            }
         }
         return count;
      } catch (Exception e) {
         throw new RuntimeException(e.getMessage(), e);
      }
   }

   private void checkStarted() {
      if (!server.getPostOffice().isStarted()) {
         throw new IllegalStateException("Broker is not started. Queues can not be managed yet");
      }
   }

   private enum DurabilityType {
      ALL, DURABLE, NON_DURABLE
   }
}

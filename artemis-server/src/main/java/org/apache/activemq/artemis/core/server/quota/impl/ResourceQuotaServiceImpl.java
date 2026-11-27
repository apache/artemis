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
package org.apache.activemq.artemis.core.server.quota.impl;

import org.apache.activemq.artemis.api.core.ActiveMQResourceQuotaExceededException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.quota.ResourceQuotaManager;
import org.apache.activemq.artemis.core.server.quota.ResourceQuotaService;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.ResourceQuota;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

/**
 * Implementation of ResourceQuotaService with lazy initialization.
 */
public class ResourceQuotaServiceImpl implements ResourceQuotaService {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final HierarchicalRepository<AddressSettings> addressSettingsRepository;
   private final org.apache.activemq.artemis.core.config.Configuration configuration;
   private final java.util.concurrent.ConcurrentHashMap<SimpleString, String> addressQuotaMapping = new java.util.concurrent.ConcurrentHashMap<>();
   private volatile ResourceQuotaManager resourceQuotaManager;
   private org.apache.activemq.artemis.core.postoffice.PostOffice postOffice;
   private org.apache.activemq.artemis.core.paging.PagingManager pagingManager;
   private org.apache.activemq.artemis.core.server.management.ManagementService managementService;

   public ResourceQuotaServiceImpl(HierarchicalRepository<AddressSettings> addressSettingsRepository,
                                   org.apache.activemq.artemis.core.config.Configuration configuration) {
      this.addressSettingsRepository = addressSettingsRepository;
      this.configuration = configuration;
   }

   @Override
   public void setPostOffice(org.apache.activemq.artemis.core.postoffice.PostOffice postOffice) {
      this.postOffice = postOffice;
   }

   @Override
   public void setPagingManager(org.apache.activemq.artemis.core.paging.PagingManager pagingManager) {
      this.pagingManager = pagingManager;
   }

   @Override
   public void setManagementService(org.apache.activemq.artemis.core.server.management.ManagementService managementService) {
      this.managementService = managementService;
   }

   /**
    * Initialize ResourceQuotaManager lazily with runtime quota instances from configuration.
    * Creates ResourceQuota instances from ResourceQuotaConfig definitions.
    * Counters start at zero and are rebuilt by scanning existing addresses/queues during broker startup.
    * This is called on first access to ensure the manager is ready.
    */
   private void ensureInitialized() {
      if (resourceQuotaManager != null) {
         return;
      }

      synchronized (this) {
         if (resourceQuotaManager != null) {
            return;
         }
         createAndInitializeQuotaManager();
      }
   }

   /**
    * Create ResourceQuotaManager and initialize it with runtime quota instances from configuration.
    * Must be called within synchronized block.
    */
   private void createAndInitializeQuotaManager() {
      if (configuration == null) {
         return;
      }

      java.util.Map<String, org.apache.activemq.artemis.core.settings.impl.ResourceQuotaConfig> quotaConfigs =
         configuration.getResourceQuotas();
      if (quotaConfigs == null || quotaConfigs.isEmpty()) {
         logger.debug("No quota configurations found, skipping ResourceQuotaManager creation");
         return;
      }

      // Create ResourceQuotaManager
      org.apache.activemq.artemis.core.settings.impl.HierarchicalObjectRepository<ResourceQuota> quotaRepo =
         new org.apache.activemq.artemis.core.settings.impl.HierarchicalObjectRepository<>(
            configuration.getWildcardConfiguration());

      resourceQuotaManager = new ResourceQuotaManager(quotaRepo, configuration.getWildcardConfiguration());

      // Create runtime ResourceQuota instances from configuration
      for (java.util.Map.Entry<String, org.apache.activemq.artemis.core.settings.impl.ResourceQuotaConfig> entry : quotaConfigs.entrySet()) {
         ResourceQuota runtimeQuota = entry.getValue().createRuntimeQuota();
         resourceQuotaManager.addQuota(entry.getKey(), runtimeQuota);
      }

      // Establish parent relationships between runtime quota instances
      resourceQuotaManager.establishParentRelationships();

      // Register quotas in JMX
      if (managementService != null) {
         for (ResourceQuota quota : resourceQuotaManager.getAllQuotas()) {
            try {
               managementService.registerResourceQuota(quota);
            } catch (Exception e) {
               logger.warn("Failed to register resource quota '{}' in JMX: {}", quota.getName(), e.getMessage(), e);
            }
         }
      }
   }

   @Override
   public ResourceQuotaManager getResourceQuotaManager() {
      ensureInitialized();
      return resourceQuotaManager;
   }

   @Override
   public void checkAddressQuota(SimpleString address) throws Exception {
      ResourceQuota quota = lookupQuota(address);
      if (quota == null) {
         return;
      }

      if (!quota.canAddAddress()) {
         throw new ActiveMQResourceQuotaExceededException(
            "Address quota exceeded for quota '" + quota.getName() +
            "': max addresses is " + quota.getMaxAddresses());
      }
   }

   @Override
   public void incrementAddressCount(SimpleString address) {
      ResourceQuota quota = lookupQuota(address);
      if (quota != null) {
         quota.incrementAddressCount();
         addressQuotaMapping.put(address, quota.getName());
      }
   }

   @Override
   public void decrementAddressCount(SimpleString address) {
      ResourceQuota quota = lookupQuota(address);
      if (quota != null) {
         quota.decrementAddressCount();
         addressQuotaMapping.remove(address);
      }
   }

   @Override
   public void checkQueueQuota(SimpleString address) throws Exception {
      ResourceQuota quota = lookupQuota(address);
      if (quota == null) {
         return;
      }

      if (!quota.canAddQueue()) {
         throw new ActiveMQResourceQuotaExceededException(
            "Queue quota exceeded for quota '" + quota.getName() +
            "': max queues is " + quota.getMaxQueues());
      }
   }

   @Override
   public void incrementQueueCount(SimpleString address) {
      ResourceQuota quota = lookupQuota(address);
      if (quota != null) {
         quota.incrementQueueCount();
      }
   }

   @Override
   public void decrementQueueCount(SimpleString address) {
      ResourceQuota quota = lookupQuota(address);
      if (quota != null) {
         quota.decrementQueueCount();
      }
   }

   @Override
   public ResourceQuota lookupQuota(SimpleString address) {
      if (resourceQuotaManager == null) {
         return null;
      }

      AddressSettings settings = addressSettingsRepository.getMatch(address.toString());
      return resourceQuotaManager.getQuotaForAddress(address, settings);
   }

   @Override
   public void reloadQuotas() {
      logger.debug("Reloading resource quotas from configuration");

      java.util.Map<String, org.apache.activemq.artemis.core.settings.impl.ResourceQuotaConfig> newConfigs =
         configuration.getResourceQuotas();

      // Create manager if it doesn't exist and we have quotas to add
      if (resourceQuotaManager == null && (newConfigs != null && !newConfigs.isEmpty())) {
         org.apache.activemq.artemis.core.settings.impl.HierarchicalObjectRepository<ResourceQuota> quotaRepo =
            new org.apache.activemq.artemis.core.settings.impl.HierarchicalObjectRepository<>(
               configuration.getWildcardConfiguration());
         resourceQuotaManager = new ResourceQuotaManager(quotaRepo, configuration.getWildcardConfiguration());
      }

      // If manager is null and no new quotas, nothing to do
      if (resourceQuotaManager == null) {
         logger.debug("No quotas configured and no existing quotas");
         return;
      }

      // Get current quotas from manager
      java.util.List<ResourceQuota> currentQuotasList = resourceQuotaManager.getAllQuotas();

      logger.debug("Found {} current quotas, new config has {} quotas",
                  currentQuotasList.size(), newConfigs == null ? 0 : newConfigs.size());

      // Process removals - remove quotas that are no longer in config
      java.util.Set<String> removedQuotaNames = new java.util.HashSet<>();
      for (ResourceQuota quota : currentQuotasList) {
         String name = quota.getName();
         if (newConfigs == null || !newConfigs.containsKey(name)) {
            logger.debug("Removing quota: {}", name);
            resourceQuotaManager.removeQuota(name);
            removedQuotaNames.add(name);
         }
      }

      // Unregister removed quotas from JMX
      if (managementService != null && !removedQuotaNames.isEmpty()) {
         for (String quotaName : removedQuotaNames) {
            try {
               managementService.unregisterResourceQuota(quotaName);
            } catch (Exception e) {
               logger.warn("Failed to unregister resource quota '{}' from JMX: {}", quotaName, e.getMessage(), e);
            }
         }
      }

      // Clear orphaned parent references - when a parent quota is removed,
      // child quotas that referenced it should have their parent field cleared
      if (!removedQuotaNames.isEmpty()) {
         clearOrphanedParentReferences(removedQuotaNames);
      }

      // Process additions and modifications
      java.util.Map<String, ResourceQuota> newQuotas = new java.util.HashMap<>();
      java.util.Set<String> hierarchyChangedQuotas = new java.util.HashSet<>();

      if (newConfigs != null) {
         for (java.util.Map.Entry<String, org.apache.activemq.artemis.core.settings.impl.ResourceQuotaConfig> entry : newConfigs.entrySet()) {
            String name = entry.getKey();
            org.apache.activemq.artemis.core.settings.impl.ResourceQuotaConfig newConfig = entry.getValue();
            ResourceQuota currentQuota = resourceQuotaManager.getQuota(name);

            if (currentQuota == null) {
               // New quota - add it, counters will be populated by scanning later
               logger.debug("Adding new quota: {}", name);
               ResourceQuota newQuota = newConfig.createRuntimeQuota();
               resourceQuotaManager.addQuota(name, newQuota);
               newQuotas.put(name, newQuota);

               // Register new quota in JMX
               if (managementService != null) {
                  try {
                     managementService.registerResourceQuota(newQuota);
                  } catch (Exception e) {
                     logger.warn("Failed to register resource quota '{}' in JMX: {}", name, e.getMessage(), e);
                  }
               }
            } else if (!configEquals(currentQuota, newConfig)) {
               // Quota exists but config changed - update in place
               logger.debug("Modifying quota: {}", name);

               // Check if hierarchy changed before updating
               boolean hierarchyChanged = !java.util.Objects.equals(
                  currentQuota.getPartOf(),
                  newConfig.getPartOf()
               );

               // Update limits in place (preserves counters)
               updateQuotaLimits(currentQuota, newConfig);

               if (hierarchyChanged) {
                  // Decrement this quota's contribution from the old parent chain
                  // BEFORE clearing the parent pointer. This preserves the child's
                  // own counters (no enforcement window) while correctly adjusting
                  // the old parent's aggregate counts.
                  ResourceQuota oldParent = currentQuota.getParent();
                  if (oldParent != null) {
                     adjustParentChain(oldParent,
                        -currentQuota.getCurrentAddressCount(),
                        -currentQuota.getCurrentQueueCount(),
                        -currentQuota.getCurrentMessageBytes());
                  }
                  currentQuota.setParent(null);
                  hierarchyChangedQuotas.add(name);
                  logger.debug("Quota {} hierarchy changed - parent chain adjusted in place", name);
               } else {
                  logger.debug("Quota {} limits updated without counter rebuild", name);
               }
            }
         }

         // Re-establish parent relationships for new/modified quotas
         if (!newQuotas.isEmpty() || !hierarchyChangedQuotas.isEmpty()) {
            resourceQuotaManager.establishParentRelationships();
         }

         // For hierarchy-changed quotas: increment the new parent chain
         // using the child's current counter values (which were never zeroed)
         for (String name : hierarchyChangedQuotas) {
            ResourceQuota quota = resourceQuotaManager.getQuota(name);
            if (quota != null) {
               ResourceQuota newParent = quota.getParent();
               if (newParent != null) {
                  adjustParentChain(newParent,
                     quota.getCurrentAddressCount(),
                     quota.getCurrentQueueCount(),
                     quota.getCurrentMessageBytes());
               }
            }
         }

         // For wildcard templates whose hierarchy changed, also reparent
         // their instantiated children. Instances live in a separate map
         // and are not covered by establishParentRelationships().
         reparentWildcardInstances(hierarchyChangedQuotas, newConfigs);

         // For new quotas: scan existing addresses to populate initial counters
         if (!newQuotas.isEmpty()) {
            for (java.util.Map.Entry<String, ResourceQuota> entry : newQuotas.entrySet()) {
               populateCountersForNewQuota(entry.getKey(), entry.getValue());
            }
         }
      }

      // Rebalance address-to-quota mappings. Address-settings may have changed
      // (swap happens before reloadQuotas), so existing addresses may now map
      // to different quotas than what currently tracks them.
      rebalanceAddressMappings();

      logger.debug("Resource quota reload complete");
   }

   /**
    * Update quota limits in place from configuration.
    * This preserves existing counters while updating the limit values.
    *
    * @param quota the quota to update
    * @param config the new configuration
    */
   private void updateQuotaLimits(ResourceQuota quota, org.apache.activemq.artemis.core.settings.impl.ResourceQuotaConfig config) {
      quota.setMaxAddresses(config.getMaxAddresses() >= 0 ? config.getMaxAddresses() : null);
      quota.setMaxQueues(config.getMaxQueues() >= 0 ? config.getMaxQueues() : null);
      quota.setMaxMessageBytes(config.getMaxMessageBytes() >= 0 ? config.getMaxMessageBytes() : null);
      quota.setPartOf(config.getPartOf());
   }

   /**
    * Adjust counters on a parent chain without propagation.
    * Used during hierarchy changes to move a child quota's contribution
    * from the old parent chain to the new one, avoiding any counter reset.
    *
    * @param parent the starting parent quota in the chain
    * @param addressDelta change in address count (negative to remove, positive to add)
    * @param queueDelta change in queue count
    * @param bytesDelta change in byte count
    */
   private void adjustParentChain(ResourceQuota parent, int addressDelta, int queueDelta, long bytesDelta) {
      while (parent != null) {
         parent.adjustCountersDirect(addressDelta, queueDelta, bytesDelta);
         parent = parent.getParent();
      }
   }

   /**
    * Reparent wildcard instances when their template's partOf changes.
    * Instances live in a separate map from the quota repository and are not
    * covered by establishParentRelationships(). When a wildcard template like
    * "region.*" changes its partOf, all instances (e.g., "region.us", "region.eu")
    * must also be moved to the new parent chain using delta adjustments.
    */
   private void reparentWildcardInstances(java.util.Set<String> hierarchyChangedQuotas,
                                           java.util.Map<String, org.apache.activemq.artemis.core.settings.impl.ResourceQuotaConfig> newConfigs) {
      if (resourceQuotaManager == null) {
         return;
      }

      java.util.Map<String, ResourceQuota> instances = resourceQuotaManager.getInstantiatedQuotas();
      if (instances.isEmpty()) {
         return;
      }

      for (String templateName : hierarchyChangedQuotas) {
         if (!templateName.contains("*")) {
            continue;
         }

         org.apache.activemq.artemis.core.settings.impl.ResourceQuotaConfig newConfig = newConfigs.get(templateName);
         if (newConfig == null) {
            continue;
         }

         String prefix = templateName.substring(0, templateName.indexOf('*'));
         String newPartOf = newConfig.getPartOf();

         // Resolve the new parent quota object
         ResourceQuota newParentQuota = newPartOf != null ? resourceQuotaManager.getQuota(newPartOf) : null;

         for (java.util.Map.Entry<String, ResourceQuota> instanceEntry : instances.entrySet()) {
            if (!instanceEntry.getKey().startsWith(prefix)) {
               continue;
            }

            ResourceQuota instance = instanceEntry.getValue();

            // Decrement from old parent chain
            ResourceQuota oldParent = instance.getParent();
            if (oldParent != null) {
               adjustParentChain(oldParent,
                  -instance.getCurrentAddressCount(),
                  -instance.getCurrentQueueCount(),
                  -instance.getCurrentMessageBytes());
            }

            // Update instance's partOf and parent
            instance.setPartOf(newPartOf);
            instance.setParent(newParentQuota);

            // Increment new parent chain
            if (newParentQuota != null) {
               adjustParentChain(newParentQuota,
                  instance.getCurrentAddressCount(),
                  instance.getCurrentQueueCount(),
                  instance.getCurrentMessageBytes());
            }

            logger.debug("Reparented wildcard instance '{}' from old parent to '{}'",
                        instanceEntry.getKey(), newPartOf);
         }
      }
   }

   /**
    * Clear orphaned parent references when parent quotas are removed.
    * When a parent quota is removed, child quotas that referenced it should have
    * their parent field cleared to avoid holding references to removed objects.
    *
    * @param removedQuotaNames names of quotas that were removed
    */
   private void clearOrphanedParentReferences(java.util.Set<String> removedQuotaNames) {
      if (resourceQuotaManager == null) {
         return;
      }

      java.util.List<ResourceQuota> allQuotas = resourceQuotaManager.getAllQuotas();
      for (ResourceQuota quota : allQuotas) {
         ResourceQuota parent = quota.getParent();
         if (parent != null && removedQuotaNames.contains(parent.getName())) {
            quota.setParent(null);
            logger.warn("Cleared orphaned parent reference from quota '{}' to removed quota '{}'. " +
                       "The quota configuration still references part-of='{}', but the parent quota no longer exists.",
                       quota.getName(), parent.getName(), quota.getPartOf());
         }
      }
   }

   /**
    * Check if a runtime quota matches the configuration.
    * Used to detect modifications during reload.
    */
   private boolean configEquals(ResourceQuota quota, org.apache.activemq.artemis.core.settings.impl.ResourceQuotaConfig config) {
      if (quota.getMaxAddresses() != config.getMaxAddresses()) {
         return false;
      }
      if (quota.getMaxQueues() != config.getMaxQueues()) {
         return false;
      }
      if (quota.getMaxMessageBytes() != config.getMaxMessageBytes()) {
         return false;
      }
      return java.util.Objects.equals(quota.getPartOf(), config.getPartOf());
   }

   /**
    * Populate counters for a newly added quota by scanning existing addresses, queues, and message bytes.
    * Called only for new quotas that start with zero counters.
    * Uses propagating increment methods so parent chain counts are updated automatically.
    *
    * @param quotaName the name of the quota being populated
    * @param quota the quota instance (must have zero counters)
    */
   private void populateCountersForNewQuota(String quotaName, ResourceQuota quota) {
      if (postOffice == null) {
         logger.warn("PostOffice not available, cannot populate counters for quota '{}'", quotaName);
         return;
      }

      int addressCount = 0;
      int queueCount = 0;
      long totalBytes = 0;

      java.util.Set<org.apache.activemq.artemis.api.core.SimpleString> addresses = postOffice.getAddresses();
      for (org.apache.activemq.artemis.api.core.SimpleString address : addresses) {
         ResourceQuota addressQuota = lookupQuota(address);
         if (addressQuota != null && addressQuota.getName().equals(quotaName)) {
            quota.incrementAddressCount();
            addressQuotaMapping.put(address, quotaName);
            addressCount++;

            if (pagingManager != null) {
               try {
                  org.apache.activemq.artemis.core.paging.PagingStore pagingStore =
                     pagingManager.getPageStore(address);
                  if (pagingStore != null) {
                     long addressSize = pagingStore.getAddressSize();
                     quota.addSize(addressSize);
                     totalBytes += addressSize;
                  }
               } catch (Exception e) {
                  logger.warn("Error getting paging store size for address {}: {}", address, e.getMessage());
               }
            }

            try {
               java.util.List<org.apache.activemq.artemis.core.server.Queue> queuesForAddress =
                  postOffice.listQueuesForAddress(address);
               for (org.apache.activemq.artemis.core.server.Queue queue : queuesForAddress) {
                  ResourceQuota queueQuota = lookupQuota(queue.getAddress());
                  if (queueQuota != null && queueQuota.getName().equals(quotaName)) {
                     quota.incrementQueueCount();
                     queueCount++;
                  }
               }
            } catch (Exception e) {
               logger.warn("Error listing queues for address {} when populating quota counters: {}", address, e.getMessage());
            }
         }
      }

      logger.debug("Populated counters for new quota '{}': {} addresses, {} queues, {} bytes",
                  quotaName, addressCount, queueCount, totalBytes);
   }

   /**
    * Rebalance address-to-quota mappings after address-settings may have changed.
    * For each tracked address, checks whether lookupQuota() (using updated address-settings)
    * returns a different quota. If so, decrements the old quota's counters and increments the
    * new quota's counters using propagating methods so parent chains are adjusted automatically.
    */
   private void rebalanceAddressMappings() {
      if (postOffice == null || resourceQuotaManager == null || addressQuotaMapping.isEmpty()) {
         return;
      }

      int rebalanced = 0;
      for (java.util.Map.Entry<SimpleString, String> entry : addressQuotaMapping.entrySet()) {
         SimpleString address = entry.getKey();
         String oldQuotaName = entry.getValue();

         ResourceQuota newQuota = lookupQuota(address);
         String newQuotaName = newQuota != null ? newQuota.getName() : null;

         if (java.util.Objects.equals(oldQuotaName, newQuotaName)) {
            continue;
         }

         // Find the old quota object
         ResourceQuota oldQuota = resourceQuotaManager.getQuota(oldQuotaName);

         // Count queues for this address
         int queueCount = 0;
         try {
            java.util.List<org.apache.activemq.artemis.core.server.Queue> queues =
               postOffice.listQueuesForAddress(address);
            queueCount = queues.size();
         } catch (Exception e) {
            logger.warn("Error listing queues for address {} during rebalance: {}", address, e.getMessage());
         }

         // Get bytes for this address
         long bytes = 0;
         if (pagingManager != null) {
            try {
               org.apache.activemq.artemis.core.paging.PagingStore pagingStore =
                  pagingManager.getPageStore(address);
               if (pagingStore != null) {
                  bytes = pagingStore.getAddressSize();
               }
            } catch (Exception e) {
               logger.warn("Error getting paging store for address {} during rebalance: {}", address, e.getMessage());
            }
         }

         // Decrement old quota (propagating to parent chain)
         if (oldQuota != null) {
            oldQuota.decrementAddressCount();
            for (int i = 0; i < queueCount; i++) {
               oldQuota.decrementQueueCount();
            }
            if (bytes > 0) {
               oldQuota.addSize(-bytes);
            }
         }

         // Increment new quota (propagating to parent chain)
         if (newQuota != null) {
            newQuota.incrementAddressCount();
            for (int i = 0; i < queueCount; i++) {
               newQuota.incrementQueueCount();
            }
            if (bytes > 0) {
               newQuota.addSize(bytes);
            }
         }

         // Update tracking
         if (newQuotaName != null) {
            entry.setValue(newQuotaName);
         } else {
            addressQuotaMapping.remove(address);
         }

         rebalanced++;
         logger.debug("Rebalanced address '{}' from quota '{}' to '{}'", address, oldQuotaName, newQuotaName);
      }

      if (rebalanced > 0) {
         logger.debug("Rebalanced {} address-to-quota mappings", rebalanced);
      }
   }

}

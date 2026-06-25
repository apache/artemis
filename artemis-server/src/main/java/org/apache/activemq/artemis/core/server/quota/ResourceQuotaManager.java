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
package org.apache.activemq.artemis.core.server.quota;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.HierarchicalObjectRepository;
import org.apache.activemq.artemis.core.settings.impl.ResourceQuota;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

/**
 * Manages resource quotas including template instantiation and parent hierarchy resolution.
 * <p>
 * This manager handles:
 * <ul>
 *   <li>Storage and retrieval of quota definitions</li>
 *   <li>Wildcard template expansion (e.g., "EU.*" template creates "EU.fr" instance)</li>
 *   <li>Parent-child quota hierarchy establishment</li>
 * </ul>
 */
public class ResourceQuotaManager {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final HierarchicalObjectRepository<ResourceQuota> quotaRepository;
   private final ConcurrentHashMap<String, ResourceQuota> instantiatedQuotas;
   private final WildcardConfiguration wildcardConfiguration;

   public ResourceQuotaManager(HierarchicalObjectRepository<ResourceQuota> quotaRepository,
                               WildcardConfiguration wildcardConfiguration) {
      this.quotaRepository = quotaRepository;
      this.instantiatedQuotas = new ConcurrentHashMap<>();
      this.wildcardConfiguration = wildcardConfiguration;
   }

   /**
    * Get the resource quota for a given address based on its settings.
    *
    * @param address  the address to get quota for
    * @param settings the address settings containing quota reference
    * @return the resource quota, or null if none configured
    */
   public ResourceQuota getQuotaForAddress(SimpleString address, AddressSettings settings) {
      if (settings == null || settings.getResourceQuota() == null) {
         return null;
      }

      String quotaName = settings.getResourceQuota();

      // Check if quota name contains wildcard - if so, need to resolve instance
      if (quotaName.contains("*")) {
         return resolveWildcardQuota(quotaName, address);
      }

      // Simple case: direct quota lookup
      ResourceQuota quota = quotaRepository.getMatch(quotaName);
      if (quota == null) {
         logger.warn("Quota {} referenced but not found for address {}", quotaName, address);
      }
      return quota;
   }

   /**
    * Resolve a wildcard quota template to a concrete instance.
    * For example, quota "EU.*" with address "eu.fr.orders" becomes instance "EU.fr"
    *
    * @param quotaTemplate the quota template name (e.g., "EU.*")
    * @param address       the address to match
    * @return resolved quota instance, or null if template not found
    */
   private ResourceQuota resolveWildcardQuota(String quotaTemplate, SimpleString address) {
      // First check if template exists
      ResourceQuota template = quotaRepository.getMatch(quotaTemplate);
      if (template == null) {
         logger.warn("Quota template {} not found", quotaTemplate);
         return null;
      }

      // Extract wildcard value from address
      // For quota "EU.*" and address "eu.fr.orders", extract "fr"
      String wildcardValue = extractWildcardValue(address.toString(), quotaTemplate);
      if (wildcardValue == null) {
         logger.debug("Could not extract wildcard value for quota {} from address {}", quotaTemplate, address);
         return template; // Fall back to template itself
      }

      // Build instance name by substituting wildcard
      String instanceName = quotaTemplate.replace("*", wildcardValue);

      // Get or create the instance
      return instantiatedQuotas.computeIfAbsent(instanceName, name -> {
         logger.debug("Creating quota instance {} from template {}", name, quotaTemplate);
         return createQuotaInstance(name, template);
      });
   }

   /**
    * Extract the wildcard value from an address.
    * For quota "EU.*" we expect addresses like "eu.XX.*" where XX is the wildcard value.
    *
    * @param addressStr    the address string
    * @param quotaTemplate the quota template (e.g., "EU.*")
    * @return the extracted wildcard value, or null if not found
    */
   private String extractWildcardValue(String addressStr, String quotaTemplate) {
      // Convert quota template to lowercase prefix for matching
      // "EU.*" becomes "eu."
      String templatePrefix = quotaTemplate.substring(0, quotaTemplate.indexOf('*')).toLowerCase();

      // Check if address starts with this prefix pattern
      String delimiterRegex = java.util.regex.Pattern.quote(String.valueOf(wildcardConfiguration.getDelimiter()));
      String[] addressParts = addressStr.split(delimiterRegex);
      String[] templateParts = templatePrefix.split(delimiterRegex);

      // Find the position of the wildcard in the template
      int wildcardIndex = templateParts.length;

      // Extract the value at that position from the address
      if (addressParts.length > wildcardIndex) {
         return addressParts[wildcardIndex];
      }

      return null;
   }

   /**
    * Create a new quota instance from a template.
    *
    * @param instanceName the name for the new instance (e.g., "EU.fr")
    * @param template     the template to copy from
    * @return the new quota instance
    */
   private ResourceQuota createQuotaInstance(String instanceName, ResourceQuota template) {
      ResourceQuota instance = template.copy(instanceName);

      // Establish parent relationship if template has one
      if (instance.getPartOf() != null) {
         String parentName = instance.getPartOf();

         // Wildcard in partOf is not supported - it doesn't make semantic sense
         // because each instance would get a separate parent instance
         if (parentName.contains("*")) {
            logger.warn("Quota template {} has wildcard in part-of '{}' - wildcards are not supported in part-of. Parent relationship will not be established.",
                        template.getName(), parentName);
            return instance;
         }

         // Look up non-wildcard parent
         ResourceQuota parent = quotaRepository.getMatch(parentName);
         if (parent != null) {
            instance.setParent(parent);
         } else {
            logger.warn("Parent quota {} not found for instance {}", parentName, instanceName);
         }
      }

      return instance;
   }

   /**
    * Establish parent-child relationships for all quotas in the repository.
    * This should be called after all quotas are loaded from configuration.
    */
   public void establishParentRelationships() {
      List<ResourceQuota> quotasList = getAllQuotas();
      Map<String, ResourceQuota> allQuotas = new HashMap<>();
      for (ResourceQuota quota : quotasList) {
         allQuotas.put(quota.getName(), quota);
      }
      establishParentRelationships(allQuotas);
   }

   private void establishParentRelationships(Map<String, ResourceQuota> allQuotas) {
      Set<String> visited = new HashSet<>();

      for (ResourceQuota quota : allQuotas.values()) {
         establishParentChain(quota, allQuotas, visited);
      }

      logger.debug("Established parent relationships for {} quotas", allQuotas.size());
   }

   /**
    * Recursively establish parent chain for a quota, detecting circular references.
    *
    * @param quota      the quota to process
    * @param allQuotas  all available quotas
    * @param visited    set of quota names already visited (for cycle detection)
    * @return true if parent relationship was successfully established, false if circular reference detected
    */
   private boolean establishParentChain(ResourceQuota quota, Map<String, ResourceQuota> allQuotas, Set<String> visited) {
      if (quota == null || quota.getPartOf() == null) {
         return true;  // No parent needed, success
      }

      // Already processed
      if (quota.getParent() != null) {
         return true;  // Parent already set, success
      }

      // Detect circular reference
      if (visited.contains(quota.getName())) {
         logger.error("Circular parent reference detected for quota: {}", quota.getName());
         return false;  // Circular reference, failure
      }

      visited.add(quota.getName());

      String parentName = quota.getPartOf();

      // Wildcard in partOf is not supported
      if (parentName.contains("*")) {
         logger.warn("Quota {} has wildcard in part-of '{}' - wildcards are not supported in part-of. Parent relationship will not be established.",
                     quota.getName(), parentName);
         visited.remove(quota.getName());
         return false;  // Invalid configuration, failure
      }

      ResourceQuota parent = allQuotas.get(parentName);

      if (parent == null) {
         logger.warn("Parent quota {} not found for quota {}", parentName, quota.getName());
         visited.remove(quota.getName());
         return false;  // Parent not found, failure
      }

      // Recursively establish parent's chain first
      boolean parentSuccess = establishParentChain(parent, allQuotas, visited);

      // Only set parent if parent chain was successfully established (no circular reference)
      if (parentSuccess) {
         quota.setParent(parent);
         logger.debug("Established parent relationship: {} -> {}", quota.getName(), parent.getName());
      }

      visited.remove(quota.getName());
      return parentSuccess;  // Return same result as parent's processing
   }

   /**
    * Add a quota to the repository.
    *
    * @param name  the quota name
    * @param quota the quota object
    */
   public void addQuota(String name, ResourceQuota quota) {
      quotaRepository.addMatch(name, quota);
      logger.debug("Added quota: {}", name);
   }

   /**
    * Get a quota by exact name.
    *
    * @param name the quota name
    * @return the quota, or null if not found
    */
   public ResourceQuota getQuota(String name) {
      return quotaRepository.getMatch(name);
   }

   /**
    * Get all configured quotas from the repository.
    * This returns all quotas including templates and exact matches.
    *
    * @return list of all quota objects from the repository
    */
   public List<ResourceQuota> getAllQuotas() {
      return quotaRepository.values();
   }

   /**
    * Get all instantiated quotas created from templates.
    *
    * @return map of instance name to quota object
    */
   public Map<String, ResourceQuota> getInstantiatedQuotas() {
      return new ConcurrentHashMap<>(instantiatedQuotas);
   }

   /**
    * Remove a quota from the repository.
    * Handles bulk decrement from parent if this quota is part of a hierarchy.
    *
    * @param name the quota name to remove
    */
   public void removeQuota(String name) {
      ResourceQuota quota = quotaRepository.getMatch(name);
      if (quota == null) {
         logger.warn("Attempted to remove non-existent quota: {}", name);
         return;
      }

      // If this is a wildcard template, remove all runtime instances created from it
      // and decrement their parent contributions before discarding them.
      if (name.contains("*")) {
         int wildcardIndex = name.indexOf('*');
         String prefix = name.substring(0, wildcardIndex);
         instantiatedQuotas.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(prefix)) {
               decrementParentCounters(entry.getValue());
               return true;
            }
            return false;
         });
      }

      // Decrement parent counters for the quota itself (non-wildcard, or the template)
      decrementParentCounters(quota);

      // Remove from repository
      quotaRepository.removeMatch(name);

      logger.debug("Removed quota: {}", name);
   }

   private void decrementParentCounters(ResourceQuota quota) {
      ResourceQuota parent = quota.getParent();
      if (parent == null) {
         return;
      }
      int addressCount = quota.getCurrentAddressCount();
      int queueCount = quota.getCurrentQueueCount();
      long sizeBytes = quota.getCurrentMessageBytes();

      for (int i = 0; i < addressCount; i++) {
         parent.decrementAddressCount();
      }
      for (int i = 0; i < queueCount; i++) {
         parent.decrementQueueCount();
      }
      if (sizeBytes > 0) {
         parent.addSize(-sizeBytes);
      }

      logger.debug("Decremented parent quota '{}' by {} addresses, {} queues, {} bytes for child '{}'",
                  parent.getName(), addressCount, queueCount, sizeBytes, quota.getName());
   }

   @Override
   public String toString() {
      return "ResourceQuotaManager{" +
             "instantiatedQuotas=" + instantiatedQuotas.size() +
             '}';
   }
}

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

import org.apache.activemq.artemis.api.core.SimpleString;

/**
 * Service for managing resource quota enforcement.
 */
public interface ResourceQuotaService {

   /**
    * Check if creating an address would exceed quota limits.
    *
    * @param address the address name for quota lookup
    * @throws Exception if quota would be exceeded
    */
   void checkAddressQuota(SimpleString address) throws Exception;

   /**
    * Increment address count for the quota associated with the address.
    * Should be called after successful address creation and JMX registration.
    *
    * @param address the address name for quota lookup
    */
   void incrementAddressCount(SimpleString address);

   /**
    * Decrement address count for the quota associated with the address.
    * Should be called after successful address removal.
    *
    * @param address the address name for quota lookup
    */
   void decrementAddressCount(SimpleString address);

   /**
    * Check if creating a queue would exceed quota limits.
    *
    * @param address the address name for quota lookup
    * @throws Exception if quota would be exceeded
    */
   void checkQueueQuota(SimpleString address) throws Exception;

   /**
    * Increment queue count for the quota associated with the address.
    * Should be called after successful queue creation and JMX registration.
    *
    * @param address the address name for quota lookup
    */
   void incrementQueueCount(SimpleString address);

   /**
    * Decrement queue count for the quota associated with the address.
    * Should be called after successful queue removal.
    *
    * @param address the address name for quota lookup
    */
   void decrementQueueCount(SimpleString address);

   /**
    * Lookup the resource quota for a given address.
    * This looks up which quota applies to the address via AddressSettings.
    *
    * @param address the address name
    * @return the ResourceQuota instance, or null if no quota is configured
    */
   org.apache.activemq.artemis.core.settings.impl.ResourceQuota lookupQuota(SimpleString address);

   /**
    * Get a resource quota by its configured name.
    * This provides direct access to a quota by the name it was configured with.
    *
    * @param quotaName the quota name
    * @return the ResourceQuota instance, or null if no quota with that name exists
    */
   default org.apache.activemq.artemis.core.settings.impl.ResourceQuota getQuotaByName(String quotaName) {
      ResourceQuotaManager manager = getResourceQuotaManager();
      return manager != null ? manager.getQuota(quotaName) : null;
   }

   /**
    * Get the ResourceQuotaManager managed by this service.
    * This provides access to the manager for operations that need direct access
    * to the hierarchy and wildcard template functionality.
    *
    * @return the ResourceQuotaManager instance, or null if quotas are not configured
    */
   default ResourceQuotaManager getResourceQuotaManager() {
      return null;
   }

   /**
    * Reload resource quotas from configuration.
    * Creates fresh quota instances, clears wildcard instances, and rebuilds counters.
    *
    */
   void reloadQuotas();

   default void setPostOffice(org.apache.activemq.artemis.core.postoffice.PostOffice postOffice) {
   }

   default void setPagingManager(org.apache.activemq.artemis.core.paging.PagingManager pagingManager) {
   }

   default void setManagementService(org.apache.activemq.artemis.core.server.management.ManagementService managementService) {
   }

}

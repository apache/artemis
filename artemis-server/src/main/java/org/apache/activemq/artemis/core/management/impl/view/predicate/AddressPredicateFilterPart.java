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
package org.apache.activemq.artemis.core.management.impl.view.predicate;

import org.apache.activemq.artemis.api.core.management.AddressControl;
import org.apache.activemq.artemis.core.management.impl.view.AddressField;

import java.util.Arrays;

public class AddressPredicateFilterPart extends PredicateFilterPart<AddressControl> {
   private final AddressFilterPredicate addressFilterPredicate;
   private AddressField f;

   public AddressPredicateFilterPart(AddressFilterPredicate addressFilterPredicate, String field, String operation, String value) {
      super(operation, value);
      this.addressFilterPredicate = addressFilterPredicate;
      if (field != null && !field.isEmpty()) {
         f = AddressField.valueOfName(field);

         //for backward compatibility
         if (f == null) {
            f = AddressField.valueOf(field);
         }

      }
   }

   @Override
   public boolean filterPart(AddressControl address) throws Exception {
      return switch (f) {
         case ID -> matchesLong(address.getId());
         case NAME -> matches(address.getAddress());
         case ROUTING_TYPES -> matchAny(Arrays.asList(address.getRoutingTypes()));
         case QUEUE_COUNT -> matchesLong(address.getQueueCount());
         case INTERNAL -> matches(address.isInternal());
         case TEMPORARY -> matches(address.isTemporary());
         case AUTO_CREATED -> matches(address.isAutoCreated());
         case PAUSED -> matches(address.isPaused());
         case CURRENT_DUPLICATE_ID_CACHE_SIZE -> matchesLong(address.getCurrentDuplicateIdCacheSize());
         case RETROACTIVE_RESOURCE -> matches(address.isRetroactiveResource());
         case UNROUTED_MESSAGE_COUNT -> matchesLong(address.getUnRoutedMessageCount());
         case ROUTED_MESSAGE_COUNT -> matchesLong(address.getRoutedMessageCount());
         case MESSAGE_COUNT -> matchesLong(address.getMessageCount());
         case NUMBER_OF_BYTES_PER_PAGE -> matchesLong(address.getNumberOfBytesPerPage());
         case ADDRESS_LIMIT_PERCENT -> matchesLong(address.getAddressLimitPercent());
         case PAGING -> matches(address.isPaging());
         case NUMBER_OF_PAGES -> matchesLong(address.getNumberOfPages());
         case ADDRESS_SIZE -> matchesLong(address.getAddressSize());
         case MAX_PAGE_READ_BYTES -> matchesLong(address.getMaxPageReadBytes());
         case MAX_PAGE_READ_MESSAGES -> matchesLong(address.getMaxPageReadMessages());
         case PREFETCH_PAGE_BYTES -> matchesLong(address.getPrefetchPageBytes());
         case PREFETCH_PAGE_MESSAGES -> matchesLong(address.getPrefetchPageBytes());
      };
   }
}

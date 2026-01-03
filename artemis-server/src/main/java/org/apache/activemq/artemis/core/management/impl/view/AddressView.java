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
package org.apache.activemq.artemis.core.management.impl.view;

import org.apache.activemq.artemis.api.core.management.AddressControl;
import org.apache.activemq.artemis.core.management.impl.view.predicate.AddressPredicateFilterPart;
import org.apache.activemq.artemis.json.JsonObjectBuilder;
import org.apache.activemq.artemis.core.management.impl.view.predicate.AddressFilterPredicate;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.utils.JsonLoader;

public class AddressView extends ActiveMQAbstractView<AddressControl, AddressPredicateFilterPart> {

   private static final String defaultSortField = AddressField.ID.getName();

   private final ActiveMQServer server;

   public AddressView(ActiveMQServer server) {
      super();
      this.server = server;
      this.predicate = new AddressFilterPredicate();
   }

   @Override
   public Class getClassT() {
      return AddressControl.class;
   }

   @Override
   public JsonObjectBuilder toJson(AddressControl address) {
      if (address == null) {
         return null;
      }

      JsonObjectBuilder obj = JsonLoader.createObjectBuilder()
         .add(AddressField.ID.getName(), toString(address.getId()))
         .add(AddressField.NAME.getName(), toString(address.getAddress()))
         .add(AddressField.ROUTING_TYPES.getName(), toString(address.getRoutingTypesAsJSON()))
         .add(AddressField.QUEUE_COUNT.getName(), toString(address.getQueueCount()))
         .add(AddressField.INTERNAL.getName(), toString(address.isInternal()))
         .add(AddressField.TEMPORARY.getName(), toString(address.isTemporary()))
         .add(AddressField.AUTO_CREATED.getName(), toString(address.isAutoCreated()))
         .add(AddressField.PAUSED.getName(), toString(address.isPaused()))
         .add(AddressField.CURRENT_DUPLICATE_ID_CACHE_SIZE.getName(), toString(address.getCurrentDuplicateIdCacheSize()))
         .add(AddressField.RETROACTIVE_RESOURCE.getName(), toString(address.isRetroactiveResource()))
         .add(AddressField.UNROUTED_MESSAGE_COUNT.getName(), toString(address.getUnRoutedMessageCount()))
         .add(AddressField.ROUTED_MESSAGE_COUNT.getName(), toString(address.getRoutedMessageCount()))
         .add(AddressField.MESSAGE_COUNT.getName(), toString(address.getMessageCount()))
         .add(AddressField.ADDRESS_LIMIT_PERCENT.getName(), toString(address.getAddressLimitPercent()))
         .add(AddressField.NUMBER_OF_PAGES.getName(), toString(address.getNumberOfPages()))
         .add(AddressField.ADDRESS_SIZE.getName(), toString(address.getAddressSize()))
         .add(AddressField.MAX_PAGE_READ_BYTES.getName(), toString(address.getMaxPageReadBytes()))
         .add(AddressField.MAX_PAGE_READ_MESSAGES.getName(), toString(address.getMaxPageReadMessages()))
         .add(AddressField.PREFETCH_PAGE_BYTES.getName(), toString(address.getPrefetchPageBytes()))
         .add(AddressField.PREFETCH_PAGE_MESSAGES.getName(), toString(address.getPrefetchPageBytes()));

      try {
         obj.add(AddressField.NUMBER_OF_BYTES_PER_PAGE.getName(), toString(address.getNumberOfBytesPerPage()));
      } catch (Exception e) {
         obj.add(AddressField.NUMBER_OF_BYTES_PER_PAGE.getName(), N_A);
      }

      try {
         obj.add(AddressField.PAGING.getName(), toString(address.isPaging()));
      } catch (Exception e) {
         obj.add(AddressField.PAGING.getName(), N_A);
      }
      return obj;
   }

   @Override
   public Object getField(AddressControl address, String fieldName) {
      if (address == null) {
         return null;
      }

      return switch (AddressField.valueOfName(fieldName)) {
         case ID -> address.getId();
         case NAME -> address.getAddress();
         case ROUTING_TYPES -> address.getRoutingTypes();
         case QUEUE_COUNT -> address.getQueueCount();
         case INTERNAL -> address.isInternal();
         case TEMPORARY -> address.isTemporary();
         case AUTO_CREATED -> address.isAutoCreated();
         case PAUSED -> address.isPaused();
         case CURRENT_DUPLICATE_ID_CACHE_SIZE -> address.getCurrentDuplicateIdCacheSize();
         case RETROACTIVE_RESOURCE -> address.isRetroactiveResource();
         case UNROUTED_MESSAGE_COUNT -> address.getUnRoutedMessageCount();
         case ROUTED_MESSAGE_COUNT -> address.getRoutedMessageCount();
         case MESSAGE_COUNT -> address.getMessageCount();
         case NUMBER_OF_BYTES_PER_PAGE -> {
            try {
               yield address.getNumberOfBytesPerPage();
            } catch (Exception e) {
               yield N_A;
            }
         }
         case ADDRESS_LIMIT_PERCENT -> address.getAddressLimitPercent();
         case PAGING -> {
            try {
               yield address.isPaging();
            } catch (Exception e) {
               yield N_A;
            }
         }
         case NUMBER_OF_PAGES -> address.getNumberOfPages();
         case ADDRESS_SIZE -> address.getAddressSize();
         case MAX_PAGE_READ_BYTES -> address.getMaxPageReadBytes();
         case MAX_PAGE_READ_MESSAGES -> address.getMaxPageReadMessages();
         case PREFETCH_PAGE_BYTES -> address.getPrefetchPageBytes();
         case PREFETCH_PAGE_MESSAGES -> address.getPrefetchPageBytes();
         default -> throw new IllegalArgumentException("Unsupported field, " + fieldName);
      };
   }

   @Override
   public String getDefaultOrderColumn() {
      return defaultSortField;
   }
}

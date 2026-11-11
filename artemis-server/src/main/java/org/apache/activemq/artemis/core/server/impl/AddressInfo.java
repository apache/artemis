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

import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonArrayBuilder;
import org.apache.activemq.artemis.json.JsonNumber;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.json.JsonObjectBuilder;
import org.apache.activemq.artemis.json.JsonString;
import org.apache.activemq.artemis.json.JsonValue;
import java.io.StringReader;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.persistence.AddressQueueStatus;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.Bindings;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.settings.HierarchicalRepositoryChangeListener;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.apache.activemq.artemis.utils.JsonLoader;
import org.apache.activemq.artemis.utils.PrefixUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class AddressInfo {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private long id;
   private long pauseStatusRecord = -1;
   private boolean swept;

   private SimpleString name;

   private boolean autoCreated = false;

   private volatile boolean temporary = false;

   // Do not be tempted to cache this on an static variable.
   // if the empty set gets damaged in any race, the results and unpredictable
   private static EnumSet<RoutingType> createEmptySet() {
      return EnumSet.noneOf(RoutingType.class);
   }

   EnumSet<RoutingType> routingTypes;
   private RoutingType firstSeen;

   private boolean internal = false;

   private volatile long routedMessageCount = 0;

   private static final AtomicLongFieldUpdater<AddressInfo> routedMessageCountUpdater = AtomicLongFieldUpdater.newUpdater(AddressInfo.class, "routedMessageCount");

   private volatile long unRoutedMessageCount = 0;

   private static final AtomicLongFieldUpdater<AddressInfo> unRoutedMessageCountUpdater = AtomicLongFieldUpdater.newUpdater(AddressInfo.class, "unRoutedMessageCount");

   private volatile long bindingRemovedTimestamp = -1;

   private volatile boolean paused = false;

   private PostOffice postOffice;
   private StorageManager storageManager;
   private HierarchicalRepositoryChangeListener repositoryChangeListener;
   private long createdTimestamp = -1;

   public boolean isSwept() {
      return swept;
   }

   public void setSwept(boolean swept) {
      this.swept = swept;
   }

   /**
    * Private constructor used on JSON decoding.
    */
   private AddressInfo() {
   }

   public AddressInfo(String name) {
      this(SimpleString.of(name), createEmptySet());
   }

   public AddressInfo(SimpleString name) {
      this(name, createEmptySet());
   }

   public AddressInfo(SimpleString name, EnumSet<RoutingType> routingTypes) {
      this.name = CompositeAddress.extractAddressName(name);
      this.createdTimestamp = System.currentTimeMillis();
      setRoutingTypes(routingTypes);
   }

   public AddressInfo(SimpleString name, RoutingType routingType) {
      this.name = CompositeAddress.extractAddressName(name);
      this.createdTimestamp = System.currentTimeMillis();
      addRoutingType(routingType);
   }

   public boolean isAutoCreated() {
      return autoCreated;
   }

   public AddressInfo setAutoCreated(boolean autoCreated) {
      this.autoCreated = autoCreated;
      return this;
   }

   public boolean isTemporary() {
      return temporary;
   }

   public AddressInfo setTemporary(boolean temporary) {
      this.temporary = temporary;
      return this;
   }

   public SimpleString getName() {
      return name;
   }

   public void setId(long id) {
      this.id = id;
   }

   public long getId() {
      return id;
   }

   public EnumSet<RoutingType> getRoutingTypes() {
      return Objects.requireNonNullElseGet(routingTypes, () -> createEmptySet());
   }

   public AddressInfo setRoutingTypes(final EnumSet<RoutingType> routingTypes) {
      this.routingTypes = routingTypes;
      if (routingTypes != null && !routingTypes.isEmpty()) {
         this.firstSeen = routingTypes.iterator().next();
      } else {
         this.firstSeen = null;
      }
      return this;
   }

   public AddressInfo addRoutingType(final RoutingType routingType) {
      if (routingType != null) {
         if (routingTypes == null || routingTypes.isEmpty()) {
            routingTypes = EnumSet.of(routingType);
            firstSeen = routingType;
         } else {
            routingTypes.add(routingType);
         }
      }
      return this;
   }

   public RoutingType getRoutingType() {
      return firstSeen;
   }

   public long getBindingRemovedTimestamp() {
      return bindingRemovedTimestamp;
   }

   public void setBindingRemovedTimestamp(long bindingRemovedTimestamp) {
      this.bindingRemovedTimestamp = bindingRemovedTimestamp;
   }


   public synchronized void reloadPause(long recordID) {

      if (pauseStatusRecord >= 0) {
         try {
            storageManager.deleteAddressStatus(pauseStatusRecord);
         } catch (Exception e) {
            ActiveMQServerLogger.LOGGER.unableToDeleteQueueStatus(e);
         }
      }

      this.pauseStatusRecord = recordID;

      try {
         Bindings bindings = postOffice.lookupBindingsForAddress(this.getName());
         if (bindings != null) {
            for (Binding binding : bindings.getBindings()) {
               if (binding instanceof LocalQueueBinding localQueueBinding) {
                  localQueueBinding.getQueue().pause(false);
               }
            }
         }
      } catch (Throwable e) {
         logger.warn(e.getMessage(), e);
      }

      this.paused = true;
   }

   public synchronized void pause(boolean persist) {
      if (postOffice == null) {
         throw new IllegalStateException("");
      }
      if (storageManager == null && persist) {
         throw new IllegalStateException("");
      }
      try {
         if (persist) {
            if (pauseStatusRecord >= 0) {
               try {
                  storageManager.deleteAddressStatus(pauseStatusRecord);
               } catch (Exception e) {
                  ActiveMQServerLogger.LOGGER.unableToDeleteQueueStatus(e);
               }
            }

            this.pauseStatusRecord = storageManager.storeAddressStatus(this.getId(), AddressQueueStatus.PAUSED);
         }
         Bindings bindings = postOffice.lookupBindingsForAddress(this.getName());
         if (bindings != null) {
            for (Binding binding : bindings.getBindings()) {
               if (binding instanceof LocalQueueBinding localQueueBinding) {
                  localQueueBinding.getQueue().pause(false);
               }
            }
         }
         this.paused = true;
      } catch (Exception ex) {
         throw new RuntimeException(ex);
      }
   }

   public synchronized void resume() {
      if (postOffice == null) {
         throw new IllegalStateException("");
      }
      if (storageManager == null && this.pauseStatusRecord > 0) {
         throw new IllegalStateException("");
      }
      if (!this.paused) {
         return;
      }
      try {
         if (this.pauseStatusRecord > 0) {
            storageManager.deleteAddressStatus(this.pauseStatusRecord);
         }
         Bindings bindings = postOffice.lookupBindingsForAddress(this.getName());
         if (bindings != null) {
            for (Binding binding : bindings.getBindings()) {
               if (binding instanceof LocalQueueBinding localQueueBinding) {
                  localQueueBinding.getQueue().resume();
               }
            }
         }
         this.paused = false;
      } catch (Exception ex) {
         throw new RuntimeException(ex);
      }
   }

   boolean isPersisted() {
      return this.paused && this.pauseStatusRecord > 0;
   }

   public boolean isPaused() {
      return this.paused;
   }

   public void setPostOffice(PostOffice postOffice) {
      this.postOffice = postOffice;
   }

   public void setStorageManager(StorageManager storageManager) {
      this.storageManager = storageManager;
   }

   @Override
   public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("Address [name=").append(name);
      builder.append(", id=").append(id);
      builder.append(", routingTypes={");
      for (RoutingType routingType : getRoutingTypes()) {
         builder.append(routingType.toString()).append(",");
      }
      // delete hanging comma
      if (builder.charAt(builder.length() - 1) == ',') {
         builder.deleteCharAt(builder.length() - 1);
      }
      builder.append("}");
      builder.append(", autoCreated=").append(autoCreated);
      builder.append(", paused=").append(paused);
      builder.append(", bindingRemovedTimestamp=").append(bindingRemovedTimestamp);
      builder.append(", swept=").append(swept);
      builder.append(", createdTimestamp=").append(createdTimestamp);
      builder.append(", temporary=").append(temporary);
      builder.append("]");
      return builder.toString();
   }

   public boolean isInternal() {
      return this.internal;
   }

   public AddressInfo setInternal(boolean internal) {
      this.internal = internal;
      return this;
   }

   public AddressInfo create(SimpleString name, RoutingType routingType) {
      AddressInfo info = new AddressInfo(name, routingType);
      info.setInternal(this.internal);
      if (paused) {
         info.pause(this.pauseStatusRecord > 0);
      }
      return info;
   }

   public AddressInfo getAddressAndRoutingType(Map<SimpleString, RoutingType> prefixes) {
      for (Map.Entry<SimpleString, RoutingType> entry : prefixes.entrySet()) {
         if (this.getName().startsWith(entry.getKey())) {
            AddressInfo newAddressInfo = this.create(PrefixUtil.removePrefix(this.getName(), entry.getKey()), entry.getValue());
            return newAddressInfo;
         }
      }
      return this;
   }

   public long incrementRoutedMessageCount() {
      return routedMessageCountUpdater.incrementAndGet(this);
   }

   public long incrementUnRoutedMessageCount() {
      return unRoutedMessageCountUpdater.incrementAndGet(this);
   }

   public long getRoutedMessageCount() {
      return routedMessageCountUpdater.get(this);
   }

   public long getUnRoutedMessageCount() {
      return unRoutedMessageCountUpdater.get(this);
   }

   public HierarchicalRepositoryChangeListener getRepositoryChangeListener() {
      return repositoryChangeListener;
   }

   public AddressInfo setRepositoryChangeListener(HierarchicalRepositoryChangeListener repositoryChangeListener) {
      this.repositoryChangeListener = repositoryChangeListener;
      return this;
   }

   public String toJSON() {
      JsonObjectBuilder builder = JsonLoader.createObjectBuilder();

      builder.add("id", id);
      builder.add("name", "" + name);
      builder.add("auto-created", autoCreated);
      builder.add("temporary", temporary);
      builder.add("internal", internal);
      if (firstSeen != null) {
         builder.add("firstSeen", firstSeen.getType());
      }
      if (routingTypes != null) {
         JsonArrayBuilder arrayBuilder = JsonLoader.createArrayBuilder();
         for (RoutingType rt : routingTypes) {
            arrayBuilder.add(rt.getType());
         }
         builder.add("routingTypes", arrayBuilder);
      }
      builder.add("created-timestamp", createdTimestamp);

      return builder.build().toString();
   }

   protected void setJson(String key, Object value) {
      if (key.equals("id")) {
         JsonNumber jsonLong = (JsonNumber) value;
         this.id = jsonLong.longValue();
      } else if (key.equals("name")) {
         JsonString jasonString = (JsonString) value;
         this.name = SimpleString.of(jasonString.getString());
      } else if (key.equals("auto-created")) {
         this.autoCreated = Boolean.valueOf(value.toString());
      } else if (key.equals("temporary")) {
         this.temporary = Boolean.valueOf(value.toString());
      } else if (key.equals("firstSeen")) {
         JsonNumber jsonNumber = (JsonNumber)value;
         this.firstSeen = RoutingType.getType((byte)jsonNumber.intValue());
      } else if (key.equals("routingTypes")) {
         JsonArray routingTypes = (JsonArray) value;
         for (JsonValue rtValue : routingTypes) {
            JsonNumber jsonNumber = (JsonNumber)rtValue;
            this.addRoutingType(RoutingType.getType((byte)jsonNumber.intValue()));
         }
      } else if (key.equals("created-timestamp")) {
         JsonNumber jsonLong = (JsonNumber) value;
         this.createdTimestamp = jsonLong.longValue();
      } else if (key.equals("internal")) {
         this.internal = Boolean.valueOf(value.toString());
      }
   }

   public static AddressInfo fromJSON(String jsonString) {
      JsonObject json = JsonLoader.readObject(new StringReader(jsonString));

      AddressInfo result = new AddressInfo();

      for (Map.Entry<String, JsonValue> entry : json.entrySet()) {
         result.setJson(entry.getKey(), entry.getValue());
      }

      return result;
   }

   public long getCreatedTimestamp() {
      return createdTimestamp;
   }
}

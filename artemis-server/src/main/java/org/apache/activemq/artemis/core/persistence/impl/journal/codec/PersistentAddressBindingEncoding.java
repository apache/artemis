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
package org.apache.activemq.artemis.core.persistence.impl.journal.codec;

import java.util.EnumSet;
import java.util.Objects;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.core.persistence.AddressBindingInfo;
import org.apache.activemq.artemis.utils.DataConstants;

public class PersistentAddressBindingEncoding implements EncodingSupport, AddressBindingInfo {

   private long id;

   private SimpleString name;

   private boolean autoCreated;

   private AddressStatusEncoding addressStatusEncoding;

   private EnumSet<RoutingType> routingTypes;

   private boolean internal;

   public PersistentAddressBindingEncoding() {
      routingTypes = EnumSet.noneOf(RoutingType.class);
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder("PersistentAddressBindingEncoding [id=" + id);
      sb.append(", name=" + name);
      sb.append(", routingTypes={");
      for (RoutingType routingType : routingTypes) {
         sb.append(routingType.toString() + ",");
      }
      if (sb.charAt(sb.length() - 1) == ',') {
         sb.deleteCharAt(sb.length() - 1);
      }
      sb.append("}");
      sb.append(", autoCreated=" + autoCreated);
      sb.append(", internal=" + internal + "]");
      return sb.toString();
   }

   public PersistentAddressBindingEncoding(final SimpleString name,
                                           final EnumSet<RoutingType> routingTypes,
                                           final boolean autoCreated,
                                           final boolean internal) {
      this.name = Objects.requireNonNull(name);
      this.routingTypes = Objects.requireNonNull(routingTypes);
      this.autoCreated = autoCreated;
      this.internal = internal;
   }

   @Override
   public long getId() {
      return id;
   }

   public void setId(final long id) {
      this.id = id;
   }

   @Override
   public SimpleString getName() {
      return name;
   }

   @Override
   public boolean isAutoCreated() {
      return autoCreated;
   }

   @Override
   public EnumSet<RoutingType> getRoutingTypes() {
      return routingTypes;
   }

   @Override
   public AddressStatusEncoding getAddressStatusEncoding() {
      return addressStatusEncoding;
   }

   public void setAddressStatusEncoding(AddressStatusEncoding addressStatusEncoding) {
      this.addressStatusEncoding = addressStatusEncoding;
   }

   @Override
   public boolean isInternal() {
      return internal;
   }

   @Override
   public void decode(final ActiveMQBuffer buffer) {
      name = buffer.readSimpleString();
      int size = buffer.readInt();
      for (int i = 0; i < size; i++) {
         routingTypes.add(RoutingType.getType(buffer.readByte()));
      }
      autoCreated = buffer.readBoolean();

      if (buffer.readableBytes() > 0) {
         internal = buffer.readBoolean();
      } else {
         internal = ActiveMQDefaultConfiguration.getDefaultInternal();
      }
   }

   @Override
   public void encode(final ActiveMQBuffer buffer) {
      buffer.writeSimpleString(name);
      buffer.writeInt(routingTypes.size());
      for (RoutingType d : routingTypes) {
         buffer.writeByte(d.getType());
      }
      buffer.writeBoolean(autoCreated);
      buffer.writeBoolean(internal);
   }

   @Override
   public int getEncodeSize() {
      return SimpleString.sizeofString(name) +
         DataConstants.SIZE_INT +
         (DataConstants.SIZE_BYTE * routingTypes.size()) +
         DataConstants.SIZE_BOOLEAN +
         DataConstants.SIZE_BOOLEAN;
   }
}

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
package org.apache.activemq.artemis.jms.persistence.config;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.utils.BufferHelper;
import org.apache.activemq.artemis.utils.DataConstants;

public class PersistedDestination implements EncodingSupport {


   private long id;

   private PersistedType type;

   private String name;

   private String selector;

   private boolean durable;

   public PersistedDestination() {
   }

   public PersistedDestination(final PersistedType type, final String name) {
      this(type, name, null, true);
   }

   public PersistedDestination(final PersistedType type,
                               final String name,
                               final String selector,
                               final boolean durable) {
      this.type = type;
      this.name = name;
      this.selector = selector;
      this.durable = durable;
   }

   public long getId() {
      return id;
   }

   public void setId(final long id) {
      this.id = id;
   }

   public String getName() {
      return name;
   }

   public PersistedType getType() {
      return type;
   }

   public String getSelector() {
      return selector;
   }

   public boolean isDurable() {
      return durable;
   }

   @Override
   public int getEncodeSize() {
      return DataConstants.SIZE_BYTE +
         BufferHelper.sizeOfSimpleString(name) +
         BufferHelper.sizeOfNullableSimpleString(selector) +
         DataConstants.SIZE_BOOLEAN;
   }

   @Override
   public void encode(final ActiveMQBuffer buffer) {
      buffer.writeByte(type.getType());
      buffer.writeSimpleString(SimpleString.of(name));
      buffer.writeNullableSimpleString(SimpleString.of(selector));
      buffer.writeBoolean(durable);
   }

   @Override
   public void decode(final ActiveMQBuffer buffer) {
      type = PersistedType.getType(buffer.readByte());
      name = buffer.readSimpleString().toString();
      SimpleString selectorStr = buffer.readNullableSimpleString();
      selector = (selectorStr == null) ? null : selectorStr.toString();
      durable = buffer.readBoolean();
   }
}

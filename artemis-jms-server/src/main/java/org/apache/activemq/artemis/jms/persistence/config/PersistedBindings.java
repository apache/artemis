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

import java.util.ArrayList;
import java.util.List;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.core.journal.EncodingSupport;
import org.apache.activemq.artemis.utils.BufferHelper;
import org.apache.activemq.artemis.utils.DataConstants;

public class PersistedBindings implements EncodingSupport {


   private long id;

   private PersistedType type;

   private String name;

   private ArrayList<String> bindings = new ArrayList<>();


   public PersistedBindings() {
   }

   /**
    * @param type
    * @param name
    */
   public PersistedBindings(PersistedType type, String name) {
      super();
      this.type = type;
      this.name = name;
   }

   @Override
   public void decode(ActiveMQBuffer buffer) {
      type = PersistedType.getType(buffer.readByte());
      name = buffer.readSimpleString().toString();
      int bindingArraySize = buffer.readInt();
      bindings = new ArrayList<>(bindingArraySize);

      for (int i = 0; i < bindingArraySize; i++) {
         bindings.add(buffer.readSimpleString().toString());
      }
   }

   @Override
   public void encode(ActiveMQBuffer buffer) {
      buffer.writeByte(type.getType());
      BufferHelper.writeAsSimpleString(buffer, name);
      buffer.writeInt(bindings.size());
      for (String bindingsEl : bindings) {
         BufferHelper.writeAsSimpleString(buffer, bindingsEl);
      }
   }

   @Override
   public int getEncodeSize() {
      return DataConstants.SIZE_BYTE +
         BufferHelper.sizeOfSimpleString(name) +
         sizeOfBindings();
   }

   private int sizeOfBindings() {
      int size = DataConstants.SIZE_INT; // for the number of elements written

      for (String str : bindings) {
         size += BufferHelper.sizeOfSimpleString(str);
      }

      return size;
   }

   /**
    * @return the id
    */
   public long getId() {
      return id;
   }

   /**
    * @param id the id to set
    */
   public void setId(long id) {
      this.id = id;
   }

   /**
    * @return the type
    */
   public PersistedType getType() {
      return type;
   }

   /**
    * @return the name
    */
   public String getName() {
      return name;
   }

   /**
    * @return the bindings
    */
   public List<String> getBindings() {
      return bindings;
   }

   public void addBinding(String address) {
      bindings.add(address);
   }

   public void deleteBinding(String address) {
      bindings.remove(address);
   }

}

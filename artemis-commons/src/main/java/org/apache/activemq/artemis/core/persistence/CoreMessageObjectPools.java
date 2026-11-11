/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.persistence;


import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.utils.Suppliers;
import org.apache.activemq.artemis.utils.collections.TypedProperties;

import java.util.function.Supplier;

public class CoreMessageObjectPools {

   private final Supplier<SimpleString.ByteBufSimpleStringPool> addressDecoderPool;
   private final Supplier<TypedProperties.TypedPropertiesDecoderPools> propertiesDecoderPools;

   private final Supplier<SimpleString.StringSimpleStringPool> groupIdStringSimpleStringPool;
   private final Supplier<SimpleString.StringSimpleStringPool> addressStringSimpleStringPool;
   private final Supplier<TypedProperties.TypedPropertiesStringSimpleStringPools> propertiesStringSimpleStringPools;

   public CoreMessageObjectPools(int addressPoolCapacity,
                                 int groupIdCapacity,
                                 int propertyKeyCapacity,
                                 int propertyValueCapacity) {
      addressDecoderPool = Suppliers.memoize(() -> new SimpleString.ByteBufSimpleStringPool(addressPoolCapacity));
      propertiesDecoderPools = Suppliers.memoize(() -> new TypedProperties.TypedPropertiesDecoderPools(propertyKeyCapacity, propertyValueCapacity));
      groupIdStringSimpleStringPool = Suppliers.memoize(() -> new SimpleString.StringSimpleStringPool(groupIdCapacity));
      addressStringSimpleStringPool = Suppliers.memoize(() -> new SimpleString.StringSimpleStringPool(addressPoolCapacity));
      propertiesStringSimpleStringPools = Suppliers.memoize(() -> new TypedProperties.TypedPropertiesStringSimpleStringPools(propertyKeyCapacity, propertyValueCapacity));
   }

   public CoreMessageObjectPools() {
      addressDecoderPool = Suppliers.memoize(SimpleString.ByteBufSimpleStringPool::new);
      propertiesDecoderPools = Suppliers.memoize(TypedProperties.TypedPropertiesDecoderPools::new);
      groupIdStringSimpleStringPool = Suppliers.memoize(SimpleString.StringSimpleStringPool::new);
      addressStringSimpleStringPool = Suppliers.memoize(SimpleString.StringSimpleStringPool::new);
      propertiesStringSimpleStringPools = Suppliers.memoize(TypedProperties.TypedPropertiesStringSimpleStringPools::new);
   }

   public SimpleString.ByteBufSimpleStringPool getAddressDecoderPool() {
      return addressDecoderPool.get();
   }

   public SimpleString.StringSimpleStringPool getAddressStringSimpleStringPool() {
      return addressStringSimpleStringPool.get();
   }

   public SimpleString.StringSimpleStringPool getGroupIdStringSimpleStringPool() {
      return groupIdStringSimpleStringPool.get();
   }

   public TypedProperties.TypedPropertiesDecoderPools getPropertiesDecoderPools() {
      return propertiesDecoderPools.get();
   }

   public TypedProperties.TypedPropertiesStringSimpleStringPools getPropertiesStringSimpleStringPools() {
      return propertiesStringSimpleStringPools.get();
   }

   public static SimpleString cachedAddressSimpleString(String address, CoreMessageObjectPools coreMessageObjectPools) {
      return SimpleString.of(address, coreMessageObjectPools == null ? null : coreMessageObjectPools.getAddressStringSimpleStringPool());
   }
}

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

import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.utils.collections.LinkedListImpl;

/**
 * I need to store protocol specific data on the references. The same need exists in both PagedReference and
 * MessageReferenceImpl. This class will serve the purpose to keep the specific protocol data for either reference.
 */
public abstract class AbstractProtocolReference extends LinkedListImpl.Node<MessageReferenceImpl> implements MessageReference {

   private Map<Class, Object> protocolDataMap;
   protected volatile long sequence = 0;

   @Override
   public <T> T getProtocolData(Class<T> classType) {
      if (protocolDataMap == null) {
         return null;
      } else {
         return (T)protocolDataMap.get(classType);
      }
   }

   @Override
   public <T> void setProtocolData(Class<T> classType, T protocolData) {
      if (protocolDataMap == null) {
         protocolDataMap = new HashMap<>();
      }
      protocolDataMap.put(classType, protocolData);
   }

   @Override
   public long getSequence() {
      return sequence;
   }

   @Override
   public void setSequence(long nextSequence) {
      this.sequence = nextSequence;
   }


}

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
package org.apache.activemq.artemis.core.protocol.core.impl.wireformat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;

public abstract class QueueAbstractPacket extends PacketImpl {

   protected SimpleString queueName;

   protected SimpleString address;

   public SimpleString getQueueName() {
      return queueName;
   }

   public SimpleString getAddress() {
      return address;
   }

   /**
    * It converts the given {@code queueNames} using the JMS prefix found on {@link #address} when
    * {@code clientVersion < }{@link #ADDRESSING_CHANGE_VERSION}. If no conversion has occurred, it returns
    * {@code queueNames}.
    *
    * @param clientVersion version of the client
    * @param queueNames    names of the queues to be converted
    * @return the converted queues names or {@code queueNames} when no conversion has occurred
    */
   public final List<SimpleString> convertQueueNames(int clientVersion, List<SimpleString> queueNames) {
      if (clientVersion < ADDRESSING_CHANGE_VERSION) {
         final int names = queueNames.size();
         if (names == 0) {
            return Collections.emptyList();
         } else {
            final SimpleString prefix = jmsPrefixOf(this.address);
            if (prefix != null) {
               final List<SimpleString> prefixedQueueNames = new ArrayList<>(names);
               for (int i = 0; i < names; i++) {
                  final SimpleString oldQueueName = queueNames.get(i);
                  if (oldQueueName.startsWith(prefix)) {
                     prefixedQueueNames.add(oldQueueName);
                  } else {
                     final SimpleString prefixedQueueName = prefix.concat(oldQueueName);
                     prefixedQueueNames.add(prefixedQueueName);
                  }
               }
               return prefixedQueueNames;
            } else {
               return queueNames;
            }
         }
      } else {
         return queueNames;
      }
   }

   private static SimpleString jmsPrefixOf(SimpleString address) {
      if (address.startsWith(OLD_QUEUE_PREFIX)) {
         return OLD_QUEUE_PREFIX;
      } else if (address.startsWith(OLD_TOPIC_PREFIX)) {
         return OLD_TOPIC_PREFIX;
      } else {
         return null;
      }
   }

   public QueueAbstractPacket(byte type) {
      super(type);
   }

   public static SimpleString getOldPrefixedAddress(SimpleString address, RoutingType routingType) {
      if (routingType == RoutingType.MULTICAST && !address.startsWith(OLD_TOPIC_PREFIX)) {
         return OLD_TOPIC_PREFIX.concat(address);
      } else if (routingType == RoutingType.ANYCAST && !address.startsWith(OLD_QUEUE_PREFIX)) {
         return OLD_QUEUE_PREFIX.concat(address);
      }

      return address;
   }
}

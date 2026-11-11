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
package org.apache.activemq.artemis.core.postoffice;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;

public class QueueInfo implements Serializable {

   private static final long serialVersionUID = 3451892849198803182L;

   private final SimpleString routingName;

   private final SimpleString clusterName;

   private final SimpleString address;

   private final SimpleString filterString;

   private final long id;

   private List<SimpleString> filterStrings;

   private volatile int consumersCount = 0;

   private static final AtomicIntegerFieldUpdater<QueueInfo> consumerUpdater = AtomicIntegerFieldUpdater.newUpdater(QueueInfo.class, "consumersCount");

   private final int distance;

   public QueueInfo(final SimpleString routingName,
                    final SimpleString clusterName,
                    final SimpleString address,
                    final SimpleString filterString,
                    final long id,
                    final int distance) {
      if (routingName == null) {
         throw ActiveMQMessageBundle.BUNDLE.routeNameIsNull();
      }
      if (clusterName == null) {
         throw ActiveMQMessageBundle.BUNDLE.clusterNameIsNull();
      }
      if (address == null) {
         throw ActiveMQMessageBundle.BUNDLE.addressIsNull();
      }

      this.routingName = routingName;
      this.clusterName = clusterName;
      this.address = address;
      this.filterString = filterString;
      this.id = id;
      this.distance = distance;
   }

   public SimpleString getRoutingName() {
      return routingName;
   }

   public SimpleString getClusterName() {
      return clusterName;
   }

   public SimpleString getAddress() {
      return address;
   }

   public SimpleString getFilterString() {
      return filterString;
   }

   public int getDistance() {
      return distance;
   }

   public long getID() {
      return id;
   }

   public List<SimpleString> getFilterStrings() {
      return filterStrings;
   }

   public void setFilterStrings(final List<SimpleString> filterStrings) {
      this.filterStrings = filterStrings;
   }

   public int getNumberOfConsumers() {
      return consumerUpdater.get(this);
   }

   public void incrementConsumers() {
      consumerUpdater.incrementAndGet(this);
   }

   public void decrementConsumers() {

      consumerUpdater.getAndUpdate(this, value -> {
         if (value > 0) {
            return --value;
         } else {
            ActiveMQServerLogger.LOGGER.consumerCountError("Tried to decrement consumer count below 0: " + this);
            return value;
         }
      });
   }

   public boolean matchesAddress(SimpleString address) {
      boolean containsAddress = false;

      if (address != null) {
         SimpleString[] split = address.split(',');
         for (SimpleString addressPart : split) {
            containsAddress = this.address.startsWith(addressPart);

            if (containsAddress) {
               break;
            }
         }
      }

      return containsAddress;
   }

   @Override
   public String toString() {
      return "QueueInfo [routingName=" + routingName +
         ", clusterName=" +
         clusterName +
         ", address=" +
         address +
         ", filterString=" +
         filterString +
         ", id=" +
         id +
         ", filterStrings=" +
         filterStrings +
         ", numberOfConsumers=" +
         consumersCount +
         ", distance=" +
         distance +
         "]";
   }
}

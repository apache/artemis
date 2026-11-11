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
package org.apache.activemq.artemis.core.postoffice.impl;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.BindingType;
import org.apache.activemq.artemis.core.server.Bindable;
import org.apache.activemq.artemis.core.server.Divert;
import org.apache.activemq.artemis.core.server.RoutingContext;

public class DivertBinding implements Binding {

   private final SimpleString address;

   private final Divert divert;

   private final SimpleString uniqueName;

   private final SimpleString routingName;

   private final boolean exclusive;

   private final Long id;

   public DivertBinding(final long id, final SimpleString address, final Divert divert) {
      this.id = id;

      this.address = address;

      this.divert = divert;

      uniqueName = divert.getUniqueName();

      routingName = divert.getRoutingName();

      exclusive = divert.isExclusive();
   }

   @Override
   public Long getID() {
      return id;
   }

   @Override
   public Filter getFilter() {
      return divert.getFilter();
   }

   @Override
   public SimpleString getAddress() {
      return address;
   }

   @Override
   public Bindable getBindable() {
      return divert;
   }

   @Override
   public SimpleString getRoutingName() {
      return routingName;
   }

   @Override
   public SimpleString getUniqueName() {
      return uniqueName;
   }

   @Override
   public SimpleString getClusterName() {
      return uniqueName;
   }

   @Override
   public boolean isExclusive() {
      return exclusive;
   }

   @Override
   public boolean isHighAcceptPriority(final Message message) {
      return true;
   }

   @Override
   public void route(final Message message, final RoutingContext context) throws Exception {
      divert.route(message, context);
   }

   @Override
   public int getDistance() {
      return 0;
   }

   @Override
   public BindingType getType() {
      return BindingType.DIVERT;
   }

   @Override
   public void unproposed(SimpleString groupID) {
   }

   @Override
   public String toString() {
      return "DivertBinding [id=" + id +
         ", address=" +
         address +
         ", divert=" +
         divert +
         ", filter=" +
         divert.getFilter() +
         ", uniqueName=" +
         uniqueName +
         ", routingName=" +
         routingName +
         ", exclusive=" +
         exclusive +
         "]";
   }

   @Override
   public String toManagementString() {
      return this.getClass().getSimpleName() + " [id=" + id + "]";
   }

   @Override
   public boolean isConnected() {
      return true;
   }

   @Override
   public void routeWithAck(Message message, RoutingContext context) {
      //noop
   }

   @Override
   public void close() throws Exception {
   }

   public Divert getDivert() {
      return divert;
   }

}

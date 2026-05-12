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
package org.apache.activemq.artemis.tests.unit.core.postoffice.impl;

import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.BindingType;
import org.apache.activemq.artemis.core.postoffice.Bindings;
import org.apache.activemq.artemis.core.postoffice.BindingsFactory;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.server.Bindable;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.cluster.impl.MessageLoadBalancingType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BindingFactoryFake implements BindingsFactory {

   @Override
   public boolean isAddressBound(SimpleString address) throws Exception {
      return false;
   }

   @Override
   public Bindings createBindings(SimpleString address) {
      return new BindingsFake(address);
   }

   public static class BindingFake implements Binding {

      final SimpleString address;
      final SimpleString id;
      final Long idl;

      public BindingFake(String addressParameter, String id) {
         this(SimpleString.of(addressParameter), SimpleString.of(id));
      }

      public BindingFake(SimpleString addressParameter, SimpleString id) {
         this(addressParameter, id, 0L);
      }

      public BindingFake(SimpleString addressParameter, SimpleString id, long idl) {
         this.address = addressParameter;
         this.id = id;
         this.idl = idl;
      }

      @Override
      public void unproposed(SimpleString groupID) {

      }

      @Override
      public SimpleString getAddress() {
         return address;
      }

      @Override
      public Bindable getBindable() {
         return null;
      }

      @Override
      public BindingType getType() {
         return BindingType.LOCAL_QUEUE;
      }

      @Override
      public SimpleString getUniqueName() {
         return id;
      }

      @Override
      public SimpleString getRoutingName() {
         return id;
      }

      @Override
      public SimpleString getClusterName() {
         return null;
      }

      @Override
      public Filter getFilter() {
         return null;
      }

      @Override
      public boolean isHighAcceptPriority(Message message) {
         return false;
      }

      @Override
      public boolean isExclusive() {
         return false;
      }

      @Override
      public Long getID() {
         return idl;
      }

      @Override
      public int getDistance() {
         return 0;
      }

      @Override
      public void route(Message message, RoutingContext context) throws Exception {
      }

      @Override
      public void close() throws Exception {
      }

      @Override
      public String toManagementString() {
         return "FakeBiding Address=" + this.address;
      }

      @Override
      public boolean isConnected() {
         return true;
      }

      @Override
      public void routeWithAck(Message message, RoutingContext context) {

      }
   }

   public static class BindingsFake implements Bindings {

      private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

      SimpleString name;
      ConcurrentMap<String, Binding> bindings = new ConcurrentHashMap<>();

      public BindingsFake(SimpleString address) {
         this.name = address;
      }

      @Override
      public boolean hasLocalBinding() {
         return false;
      }

      @Override
      public Collection<Binding> getBindings() {
         return bindings.values();
      }

      @Override
      public void addBinding(Binding binding) {
         bindings.put(String.valueOf(binding.getUniqueName()), binding);
      }

      @Override
      public Binding removeBindingByUniqueName(SimpleString uniqueName) {
         return bindings.remove(String.valueOf(uniqueName));
      }

      @Override
      public SimpleString getName() {
         return name;
      }

      @Override
      public void setMessageLoadBalancingType(MessageLoadBalancingType messageLoadBalancingType) {

      }

      @Override
      public Binding getBinding(String name) {
         return bindings.get(name);
      }

      @Override
      public void forEach(BiConsumer<String, Binding> bindingConsumer) {
         bindings.forEach(bindingConsumer);
      }

      @Override
      public int size() {
         return bindings.size();
      }

      @Override
      public MessageLoadBalancingType getMessageLoadBalancingType() {
         return null;
      }

      @Override
      public void unproposed(SimpleString groupID) {
      }

      @Override
      public void updated(QueueBinding binding) {
      }

      @Override
      public Message redistribute(Message message,
                                  Queue originatingQueue,
                                  RoutingContext context) throws Exception {
         return null;
      }

      @Override
      public void route(Message message, RoutingContext context) throws Exception {
         logger.debug("routing message: {}", message);
      }

      @Override
      public boolean allowRedistribute() {
         return false;
      }
   }

}

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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.persistence.impl.nullpm.NullStorageManager;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.BindingType;
import org.apache.activemq.artemis.core.postoffice.Bindings;
import org.apache.activemq.artemis.core.postoffice.BindingsFactory;
import org.apache.activemq.artemis.core.postoffice.impl.BindingsImpl;
import org.apache.activemq.artemis.core.postoffice.impl.WildcardAddressManager;
import org.apache.activemq.artemis.core.server.Bindable;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class WildcardAddressManagerPerfTest {

   @Test
   @Disabled
   public void testConcurrencyAndEfficiency() throws Exception {

      System.out.println("Type so we can go on..");
      //TimeUnit.SECONDS.sleep(20);
      System.out.println("we can go on..");

      final WildcardConfiguration configuration = new WildcardConfiguration();
      configuration.setAnyWords('>');
      final WildcardAddressManager ad = new WildcardAddressManager(new BindingFactoryFake(), configuration, null, null);


      int numSubs = 5000;
      int numThreads = 4;
      final int partitions = 2;
      ExecutorService executorService = Executors.newFixedThreadPool(numThreads);

      for (int i = 0; i < numSubs; i++) {
         final int id = i;

         executorService.submit(() -> {
            try {

               if (id % 1000 == 0) {
                  // give gc a chance
                  Thread.yield();
               }

               // subscribe as wildcard
               ad.addBinding(new BindingFake(SimpleString.of("Topic1." + id % partitions + ".>"), SimpleString.of("" + id), id));

               SimpleString pubAddr = SimpleString.of("Topic1." + id % partitions + "." + id);


               if (id != 0 && id % 1000 == 0) {
                  System.err.println("0. pub for: " + id);
               }

               // publish
               Bindings binding = ad.getBindingsForRoutingAddress(pubAddr);

               if (binding != null) {
                  if (id != 0 && id % 1000 == 0) {
                     System.err.println("1. Bindings for: " + id + ", " + binding.getBindings().size());
                  }

                  // publish again
                  binding = ad.getBindingsForRoutingAddress(pubAddr);

                  if (id % 500 == 0) {
                     System.err.println("2. Bindings for: " + id + ", " + binding.getBindings().size());
                  }
               }

            } catch (Exception e) {
               e.printStackTrace();
            }
         });
      }

      executorService.shutdown();
      assertTrue(executorService.awaitTermination(10, TimeUnit.MINUTES), "finished on time");


      final AtomicLong addresses = new AtomicLong();
      final AtomicLong bindings = new AtomicLong();
      ad.getAddressMap().visitMatchingWildcards(SimpleString.of(">"), value -> {
         addresses.incrementAndGet();
         bindings.addAndGet(value.getBindings().size());
      });
      System.err.println("Total: Addresses: " + addresses.get() + ", bindings: " + bindings.get());

      System.out.println("Type so we can go on..");
      //System.in.read();
      System.out.println("we can go on..");

   }

   class BindingFactoryFake implements BindingsFactory {

      @Override
      public boolean isAddressBound(SimpleString address) throws Exception {
         return false;
      }

      @Override
      public Bindings createBindings(SimpleString address) throws Exception {
         return new BindingsImpl(address, null, new NullStorageManager(1000));
      }
   }

   class BindingFake implements Binding {

      final SimpleString address;
      final SimpleString id;
      final Long idl;

      BindingFake(SimpleString addressParameter, SimpleString id, long idl) {
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

}

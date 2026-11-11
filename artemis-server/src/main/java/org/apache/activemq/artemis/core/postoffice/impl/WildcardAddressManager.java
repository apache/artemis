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

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.WildcardConfiguration;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.Bindings;
import org.apache.activemq.artemis.core.postoffice.BindingsFactory;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.metrics.MetricsManager;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.utils.CompositeAddress;

/**
 * extends the simple manager to allow wildcard addresses to be used.
 */
public class WildcardAddressManager extends SimpleAddressManager {

   private final AddressMap<Bindings> addressMap = new AddressMap<>(wildcardConfiguration.getAnyWordsString(), wildcardConfiguration.getSingleWordString(), wildcardConfiguration.getDelimiter());

   /**
    * Guards consistency between {@link #addressMap} and {@link #mappings} when adding or removing bindings for wildcard addresses
    */
   private final Object addressMapLock = new Object();

   public WildcardAddressManager(final BindingsFactory bindingsFactory,
                                 final WildcardConfiguration wildcardConfiguration,
                                 final StorageManager storageManager,
                                 final MetricsManager metricsManager) {
      super(bindingsFactory, wildcardConfiguration, storageManager, metricsManager);
   }

   // publish, may be a new address that needs wildcard bindings added
   // won't contain a wildcard because we don't ever route to a wildcards at this time
   @Override
   public Bindings getBindingsForRoutingAddress(final SimpleString address) throws Exception {
      if (wildcardConfiguration.isWild(address)) {
         throw ActiveMQMessageBundle.BUNDLE.wildcardOnProducerNotSupported(String.valueOf(address));
      }

      // initial, unsynchronized check for bindings; optimized for the normal case (i.e., existing Bindings)
      Bindings bindings = super.getBindingsForRoutingAddress(address);

      if (bindings == null) {
         synchronized (addressMapLock) {
            // subsequent, synchronized check for bindings; only used when creating a new Bindings
            bindings = super.getBindingsForRoutingAddress(address);
            if (bindings == null) {
               final Bindings[] lazyCreateResult = new Bindings[1];

               addressMap.visitMatchingWildcards(address, new AddressMapVisitor<>() {
                  Bindings newBindings = null;
                  @Override
                  public void visit(Bindings matchingBindings) throws Exception {
                     if (newBindings == null) {
                        newBindings = addMappingsInternal(address, matchingBindings.getBindings());
                        lazyCreateResult[0] = newBindings;
                     } else {
                        for (Binding binding : matchingBindings.getBindings()) {
                           newBindings.addBinding(binding);
                        }
                     }
                  }
               });

               bindings = lazyCreateResult[0];
               if (bindings != null) {
                  addressMap.put(address, bindings);
               }
            }
         }
      }
      return bindings;
   }

   /**
    * If the address to add the binding to contains a wildcard then a copy of the binding (with the same underlying
    * queue) will be added to matching addresses. If the address is non wildcard, then we need to add any existing
    * matching wildcard bindings to this address the first time we see it.
    *
    * @param binding the binding to add
    * @return true if the address was a new mapping
    */
   @Override
   public boolean addBinding(final Binding binding) throws Exception {
      synchronized (addressMapLock) {
         final boolean bindingsForANewAddress = super.addBinding(binding);
         final SimpleString address = binding.getAddress();
         final Bindings bindingsForRoutingAddress = mappings.get(binding.getAddress());

         if (wildcardConfiguration.isWild(address)) {

            addressMap.visitMatching(address, bindings -> {
               // this wildcard binding needs to be added to matching addresses
               bindings.addBinding(binding);
            });

         } else if (bindingsForANewAddress) {
            // existing wildcards may match this new simple address
            addressMap.visitMatchingWildcards(address, bindings -> {
               // apply existing bindings from matching wildcards
               for (Binding toAdd : bindings.getBindings()) {
                  bindingsForRoutingAddress.addBinding(toAdd);
               }
            });
         }

         if (bindingsForANewAddress) {
            addressMap.put(address, bindingsForRoutingAddress);
         }
         return bindingsForANewAddress;
      }
   }

   @Override
   public Binding removeBinding(final SimpleString uniqueName, Transaction tx) throws Exception {
      synchronized (addressMapLock) {
         Binding binding = super.removeBinding(uniqueName, tx);
         if (binding != null) {
            SimpleString address = binding.getAddress();
            if (wildcardConfiguration.isWild(address)) {
               addressMap.visitMatching(address, bindings -> {
                  try {
                     removeBindingInternal(bindings.getName(), uniqueName);
                  } catch (IllegalStateException e) {
                     // The addressMapLock should prevent this IllegalStateException, but we explicitly catch it here
                     // for additional safety since it would abort the visitor traversal and skip cleanup of remaining
                     // addresses, leaking their entries in mappings and addressMap.
                     ActiveMQServerLogger.LOGGER.failedToRemoveBindingDuringWildcardCleanup(uniqueName.toString(), bindings.getName().toString(), e);
                  }
               });
            }
         }
         return binding;
      }
   }

   @Override
   protected void bindingsEmpty(SimpleString realAddress, Bindings bindings) {
      addressMap.remove(realAddress, bindings);
   }

   @Override
   public void clear() {
      synchronized (addressMapLock) {
         super.clear();
         addressMap.reset();
      }
   }

   public AddressMap<Bindings> getAddressMap() {
      return addressMap;
   }

   @Override
   public AddressInfo removeAddressInfo(SimpleString address) throws Exception {
      synchronized (addressMapLock) {
         SimpleString realAddress = CompositeAddress.extractAddressName(address);
         addressMap.remove(realAddress, super.getBindingsForRoutingAddress(realAddress));
         return super.removeAddressInfo(address);
      }
   }
}

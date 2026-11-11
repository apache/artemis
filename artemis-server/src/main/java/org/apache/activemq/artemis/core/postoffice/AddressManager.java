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

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.mirror.MirrorController;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.transaction.Transaction;

/**
 * Used to maintain addresses and BindingsImpl.
 */
public interface AddressManager {

   boolean addBinding(Binding binding) throws Exception;

   /**
    * This will use a Transaction as we need to confirm the queue was removed
    */
   Binding removeBinding(SimpleString uniqueName, Transaction tx) throws Exception;

   Bindings getExistingBindingsForRoutingAddress(SimpleString address) throws Exception;

   Bindings getBindingsForRoutingAddress(SimpleString address) throws Exception;

   Collection<Binding> getMatchingBindings(SimpleString address) throws Exception;

   Collection<Binding> getDirectBindings(SimpleString address) throws Exception;

   SimpleString getMatchingQueue(SimpleString address, RoutingType routingType) throws Exception;

   LocalQueueBinding findLocalBinding(long id);

   void clear();

   Binding getBinding(SimpleString queueName);

   Stream<Binding> getBindings();

   Set<SimpleString> getAddresses();

   /**
    * {@return {@code true} if the address was added, false if it wasn't added}
    */
   boolean addAddressInfo(AddressInfo addressInfo) throws Exception;

   boolean reloadAddressInfo(AddressInfo addressInfo) throws Exception;

   /**
    * Update the specified {@code AddressInfo}
    *
    * @return the updated {@code AddressInfo} or {@code null} if there are no updates
    * @throws Exception if the address doesn't exist
    */
   AddressInfo updateAddressInfo(SimpleString addressName, EnumSet<RoutingType> routingTypes) throws Exception;

   AddressInfo removeAddressInfo(SimpleString address) throws Exception;

   AddressInfo getAddressInfo(SimpleString address);

   void scanAddresses(MirrorController mirrorController) throws Exception;

   boolean checkAutoRemoveAddress(AddressInfo addressInfo,
                                  AddressSettings settings,
                                  boolean ignoreDelay) throws Exception;

}

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
package org.apache.activemq.artemis.core.server.plugin;

import java.util.EnumSet;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;

public interface ActiveMQServerAddressPlugin extends ActiveMQServerBasePlugin {

   /**
    * Before an address is added tot he broker
    *
    * @param addressInfo The addressInfo that will be added
    * @param reload If the address is being reloaded
    */
   default void beforeAddAddress(AddressInfo addressInfo, boolean reload) throws ActiveMQException {

   }

   /**
    * After an address has been added tot he broker
    *
    * @param addressInfo The newly added address
    * @param reload If the address is being reloaded
    */
   default void afterAddAddress(AddressInfo addressInfo, boolean reload) throws ActiveMQException {

   }


   /**
    * Before an address is updated
    *
    * @param address The existing address info that is about to be updated
    * @param routingTypes The new routing types that the address will be updated with
    */
   default void beforeUpdateAddress(SimpleString address, EnumSet<RoutingType> routingTypes) throws ActiveMQException {

   }

   /**
    * After an address has been updated
    *
    * @param addressInfo The newly updated address info
    */
   default void afterUpdateAddress(AddressInfo addressInfo) throws ActiveMQException {

   }

   /**
    * Before an address is removed
    *
    * @param address The address that will be removed
    */
   default void beforeRemoveAddress(SimpleString address) throws ActiveMQException {

   }

   /**
    * After an address has been removed
    *
    * @param address The address that has been removed
    * @param addressInfo The address info that has been removed or null if not removed
    */
   default void afterRemoveAddress(SimpleString address, AddressInfo addressInfo) throws ActiveMQException {

   }
}

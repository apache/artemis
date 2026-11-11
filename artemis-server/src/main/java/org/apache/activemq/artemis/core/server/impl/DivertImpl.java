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

import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.filter.Filter;
import org.apache.activemq.artemis.core.persistence.StorageManager;
import org.apache.activemq.artemis.core.persistence.impl.journal.LargeServerMessageImpl;
import org.apache.activemq.artemis.core.postoffice.PostOffice;
import org.apache.activemq.artemis.core.server.ComponentConfigurationRoutingType;
import org.apache.activemq.artemis.core.server.Divert;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.transformer.Transformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A DivertImpl simply diverts a message to a different forwardAddress
 */
public class DivertImpl implements Divert {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final PostOffice postOffice;

   private final SimpleString address;

   private volatile SimpleString forwardAddress;

   private volatile SimpleString[] forwardAddresses;

   private final SimpleString uniqueName;

   private final SimpleString routingName;

   private final boolean exclusive;

   private volatile Filter filter;

   private volatile Transformer transformer;

   private final StorageManager storageManager;

   private volatile ComponentConfigurationRoutingType routingType;

   public DivertImpl(final SimpleString uniqueName,
                     final SimpleString address,
                     final SimpleString forwardAddress,
                     final SimpleString routingName,
                     final boolean exclusive,
                     final Filter filter,
                     final Transformer transformer,
                     final PostOffice postOffice,
                     final StorageManager storageManager,
                     final ComponentConfigurationRoutingType routingType) {
      this.address = address;

      this.setForwardAddress(forwardAddress);

      this.uniqueName = uniqueName;

      this.routingName = routingName;

      this.exclusive = exclusive;

      this.filter = filter;

      this.transformer = transformer;

      this.postOffice = postOffice;

      this.storageManager = storageManager;

      this.routingType = routingType;
   }

   @Override
   public void route(final Message message, final RoutingContext context) throws Exception {

      if (logger.isTraceEnabled()) {
         logger.trace("Routing message {} through context {}", message, context);
      }

      for (SimpleString forwardAddress : forwardAddresses) {
         if (logger.isTraceEnabled()) {
            logger.trace("Diverting message {} into {}", message, this);
         }

         context.setReusable(false);

         Message copy = null;

         // Shouldn't copy if it's not routed anywhere else
         if (!forwardAddress.equals(context.getAddress(message))) {
            long id = storageManager.generateID();

            // We must make a copy of the message, otherwise things like returning credits to the page won't work
            // properly on ack, since the original address will be overwritten
            copy = message.copy(id);

            logger.trace("Divert {} copied message {}", uniqueName, copy);

            // This will set the original MessageId, and the original address
            copy.referenceOriginalMessage(message, this.getUniqueName());

            copy.setAddress(forwardAddress);

            copy.setExpiration(message.getExpiration());

            //This header could be set if the message is redistributed from a clustered broker.
            //It needs to be removed as it will interfere with upcoming routing
            copy.removeExtraBytesProperty(Message.HDR_ROUTE_TO_IDS);

            switch (routingType) {
               case ANYCAST:
                  copy.setRoutingType(RoutingType.ANYCAST);
                  break;
               case MULTICAST:
                  copy.setRoutingType(RoutingType.MULTICAST);
                  break;
               case STRIP:
                  copy.setRoutingType(null);
                  break;
               case PASS:
                  break;
            }

            if (transformer != null) {
               copy = LargeServerMessageImpl.checkLargeMessage(transformer.transform(copy), this.storageManager);
            }

            // We call reencode at the end only, in a single call.
            copy.reencode();
         } else {
            copy = message;
         }

         postOffice.route(copy, new RoutingContextImpl(context.getTransaction()).setReusable(false).setMirrorOption(context.getMirrorOption()).setRoutingType(copy.getRoutingType()).setServerSession(context.getServerSession()), false);
      }
   }

   @Override
   public void routeWithAck(Message message, RoutingContext context) throws Exception {
      route(message, context);
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
   public boolean isExclusive() {
      return exclusive;
   }

   @Override
   public SimpleString getAddress() {
      return address;
   }

   @Override
   public Filter getFilter() {
      return filter;
   }

   @Override
   public Transformer getTransformer() {
      return transformer;
   }

   @Override
   public SimpleString getForwardAddress() {
      return forwardAddress;
   }

   @Override
   public ComponentConfigurationRoutingType getRoutingType() {
      return routingType;
   }

   @Override
   public void setFilter(Filter filter) {
      this.filter = filter;
   }

   @Override
   public void setTransformer(Transformer transformer) {
      this.transformer = transformer;
   }

   @Override
   public void setForwardAddress(SimpleString forwardAddress) {
      this.forwardAddress = forwardAddress;

      String[] split = forwardAddress.toString().split(",");
      SimpleString[] forwardAddresses = new SimpleString[split.length];
      for (int i = 0; i < split.length; i++) {
         forwardAddresses[i] = SimpleString.of(split[i].trim());
      }

      this.forwardAddresses = forwardAddresses;
   }

   @Override
   public void setRoutingType(ComponentConfigurationRoutingType routingType) {
      this.routingType = routingType;
   }

   @Override
   public String toString() {
      return "DivertImpl [routingName=" + routingName +
         ", uniqueName=" +
         uniqueName +
         ", forwardAddress=" +
         forwardAddress +
         ", exclusive=" +
         exclusive +
         ", filter=" +
         filter +
         ", transformer=" +
         transformer +
         "]";
   }

}

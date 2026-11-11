/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.activemq.artemis.spi.core.protocol;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.BaseInterceptor;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.server.routing.RoutingHandler;

public abstract class AbstractProtocolManager<P, I extends BaseInterceptor<P>, C extends RemotingConnection, R extends RoutingHandler> implements ProtocolManager<I, R> {

   private final Map<SimpleString, RoutingType> prefixes = new HashMap<>();

   private String securityDomain;

   protected String invokeInterceptors(final List<I> interceptors, final P message, final C connection) {
      if (interceptors != null && !interceptors.isEmpty()) {
         for (I interceptor : interceptors) {
            try {
               if (!interceptor.intercept(message, connection)) {
                  return interceptor.getClass().getName();
               }
            } catch (Exception e) {
               ActiveMQServerLogger.LOGGER.failedToInvokeAnInterceptor(e);
            }
         }
      }

      return null;
   }

   @Override
   public void setAnycastPrefix(String anycastPrefix) {
      for (String prefix : anycastPrefix.split(",")) {
         prefixes.put(SimpleString.of(prefix), RoutingType.ANYCAST);
      }
   }

   @Override
   public void setMulticastPrefix(String multicastPrefix) {
      for (String prefix : multicastPrefix.split(",")) {
         prefixes.put(SimpleString.of(prefix), RoutingType.MULTICAST);
      }
   }

   @Override
   public Map<SimpleString, RoutingType> getPrefixes() {
      return prefixes;
   }

   @Override
   public String getSecurityDomain() {
      return securityDomain;
   }

   @Override
   public void setSecurityDomain(String securityDomain) {
      this.securityDomain = securityDomain;
   }
}

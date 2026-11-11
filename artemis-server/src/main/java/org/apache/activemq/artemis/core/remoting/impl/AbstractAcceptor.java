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
package org.apache.activemq.artemis.core.remoting.impl;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.activemq.artemis.api.core.BaseInterceptor;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManager;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;

public abstract class AbstractAcceptor implements Acceptor {

   protected final Map<String, ProtocolManager> protocolMap;

   public AbstractAcceptor(Map<String, ProtocolManager> protocolMap) {
      this.protocolMap = protocolMap;
   }

   /**
    * This will update the list of interceptors for each ProtocolManager inside the acceptor.
    */
   @Override
   public void updateInterceptors(List<BaseInterceptor> incomingInterceptors,
                                  List<BaseInterceptor> outgoingInterceptors) {
      for (ProtocolManager manager : protocolMap.values()) {
         manager.updateInterceptors(incomingInterceptors, outgoingInterceptors);
      }
   }

   public Map<String, ProtocolManager> getProtocolMap() {
      return Collections.unmodifiableMap(protocolMap);
   }

}

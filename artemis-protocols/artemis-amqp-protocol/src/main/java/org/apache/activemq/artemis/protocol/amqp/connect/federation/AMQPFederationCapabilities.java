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
package org.apache.activemq.artemis.protocol.amqp.connect.federation;

import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_V1;
import static org.apache.activemq.artemis.protocol.amqp.connect.federation.AMQPFederationConstants.FEDERATION_VERSION;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

import org.apache.qpid.proton.amqp.Symbol;
import org.apache.qpid.proton.engine.Link;

/**
 * Capabilities class that provides a reconciliation between what the remote offered as compared to what
 * this federation instance desired in order to determine what features can and cannot be used.
 */
public final class AMQPFederationCapabilities {

   private boolean initialized;
   private int localVersion = FEDERATION_V1;
   private int remoteVersion = FEDERATION_V1;
   private boolean fqqnAddressSubscriptions;

   /**
    * Initialize the federation versions all federation capabilities using the state of the opened control
    * link to match on locally set desired capabilities sent to the remote and remotely offered capabilities.
    * <p>
    * We cannot use any feature that was not indicated as locally desired when offered by the remote.
    *
    * @param controlLink The federation control link on the source or target side of the connection.
    *
    * @return this federation capabilities instance fully initialized.
    */
   public AMQPFederationCapabilities initialize(Link controlLink) {
      if (!initialized) {
         initialized = true;

         final Map<Symbol, Object> localProperties = Objects.requireNonNullElseGet(controlLink.getProperties(), () -> Collections.emptyMap());
         final Map<Symbol, Object> remoteProperties = Objects.requireNonNullElseGet(controlLink.getRemoteProperties(), () -> Collections.emptyMap());

         final Object local = localProperties.getOrDefault(FEDERATION_VERSION, FEDERATION_V1);
         final Object remote = remoteProperties.getOrDefault(FEDERATION_VERSION, FEDERATION_V1);

         if (!(local instanceof Integer localVersionNo)) {
            throw new IllegalArgumentException("Invalid value set on federation local version number: " + local);
         } else {
            this.localVersion = localVersionNo.intValue();
         }

         if (!(remote instanceof Integer remoteVersionNo)) {
            throw new IllegalArgumentException("Invalid value sent in federation remote version number: " + local);
         } else {
            this.remoteVersion = remoteVersionNo.intValue();
         }

         // Remote must be using V2 otherwise it cannot handle FQQN subscriptions and we need to fall back.
         if (remoteVersion >= 2) {
            fqqnAddressSubscriptions = true;
         }
      }

      return this;
   }

   /**
    * {@return the federation version in use on the local side of the connection.}
    */
   public int getLocalVersion() {
      return localVersion;
   }

   /**
    * {@return the federation version in use on the remote side of the connection.}
    */
   public int getRemoteVersion() {
      return remoteVersion;
   }

   /**
    * {@return <code>true</code> if federation address receivers can use FQQN source addresses or only legacy style.}
    */
   public boolean isUseFQQNAddressSubscriptions() {
      checkIsInitialized();

      return fqqnAddressSubscriptions;
   }

   private void checkIsInitialized() {
      if (!initialized) {
         throw new IllegalStateException("Cannot check capabilities until this instance is initialized");
      }
   }
}

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
package org.apache.activemq.artemis.protocol.amqp.sasl;

import org.apache.activemq.artemis.core.security.SecurityStore;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;

public class PlainSASL extends ServerSASLPlain {

   private final SecurityStore securityStore;
   private final String securityDomain;
   private RemotingConnection remotingConnection;

   public PlainSASL(SecurityStore securityStore, String securityDomain, RemotingConnection remotingConnection) {
      this.securityStore = securityStore;
      this.securityDomain = securityDomain;
      this.remotingConnection = remotingConnection;
   }

   @Override
   protected boolean authenticate(String user, String password) {
      if (securityStore.isSecurityEnabled()) {
         try {
            securityStore.authenticate(user, password, remotingConnection, securityDomain);
            return true;
         } catch (Exception e) {
            return false;
         }
      } else {
         return true;
      }
   }
}

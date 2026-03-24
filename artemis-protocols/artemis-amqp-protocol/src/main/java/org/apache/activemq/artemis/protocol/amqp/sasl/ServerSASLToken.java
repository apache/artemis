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

public abstract class ServerSASLToken implements ServerSASL {

   protected final SecurityStore securityStore;
   protected final String securityDomain;
   protected RemotingConnection remotingConnection;

   protected SASLResult result = null;

   public ServerSASLToken(SecurityStore securityStore, String securityDomain, RemotingConnection remotingConnection) {
      this.securityStore = securityStore;
      this.securityDomain = securityDomain;
      this.remotingConnection = remotingConnection;
   }

   @Override
   public SASLResult result() {
      return result;
   }

   @Override
   public void done() {
   }

   protected boolean authenticate(String user, String token) {
      if (securityStore != null && securityStore.isSecurityEnabled()) {
         try {
            securityStore.authenticate(user, token, remotingConnection, securityDomain);
            return true;
         } catch (Exception e) {
            return false;
         }
      }
      return true;
   }

}

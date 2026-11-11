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

package org.apache.activemq.artemis.core.server.plugin.impl;

import javax.security.auth.Subject;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.security.SecurityStore;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ConsumerInfo;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class BrokerMessageAuthorizationPlugin implements ActiveMQServerPlugin {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String ROLE_PROPERTY = "ROLE_PROPERTY";
   private final AtomicReference<ActiveMQServer> server = new AtomicReference<>();
   private String roleProperty = "requiredRole";

   @Override
   public void init(Map<String, String> properties) {
      roleProperty = properties.getOrDefault(ROLE_PROPERTY, "requiredRole");
   }

   @Override
   public void registered(ActiveMQServer server) {
      this.server.set(server);
   }

   @Override
   public void unregistered(ActiveMQServer server) {
      this.server.set(null);
   }

   @Override
   public boolean canAccept(ServerConsumer consumer, MessageReference reference) throws ActiveMQException {

      String requiredRole = reference.getMessage().getStringProperty(roleProperty);
      if (requiredRole == null) {
         return true;
      }

      Subject subject = getSubject(consumer);
      if (subject == null) {
         if (logger.isDebugEnabled()) {
            logger.debug("Subject not found for consumer: {}", consumer.getID());
         }
         return false;
      }
      boolean permitted = new RolePrincipal(requiredRole).implies(subject);
      if (!permitted && logger.isDebugEnabled()) {
         logger.debug("Message consumer: {} does not have required role `{}` needed to receive message: {}", consumer.getID(), requiredRole, reference.getMessageID());
      }
      return permitted;
   }

   private Subject getSubject(ConsumerInfo consumer) {
      final ActiveMQServer activeMQServer = server.get();
      final SecurityStore securityStore = activeMQServer.getSecurityStore();
      ServerSession session = activeMQServer.getSessionByID(consumer.getSessionName());
      return securityStore.getSessionSubject(session);
   }

}

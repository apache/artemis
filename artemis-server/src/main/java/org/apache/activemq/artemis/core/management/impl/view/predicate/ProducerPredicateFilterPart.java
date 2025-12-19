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
package org.apache.activemq.artemis.core.management.impl.view.predicate;

import org.apache.activemq.artemis.core.management.impl.view.ProducerField;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ServerProducer;

public class ProducerPredicateFilterPart extends PredicateFilterPart<ServerProducer> {

   private final ActiveMQServer server;

   private ProducerField f;

   public ProducerPredicateFilterPart(ActiveMQServer server, String field, String operation, String value) {
      super(operation, value);
      this.server = server;
      if (field != null && !field.isEmpty()) {
         this.f = ProducerField.valueOfName(field);

         //for backward compatibility
         if (this.f == null) {
            this.f = ProducerField.valueOf(field);
         }
      }
   }

   @Override
   public boolean filterPart(ServerProducer producer) throws Exception {
      return switch (f) {
         case ID -> matchesLong(producer.getID());
         case CONNECTION_ID -> matches(producer.getConnectionID());
         case SESSION -> matches(producer.getSessionID());
         case USER -> matches(server.getSessionByID(producer.getSessionID()).getUsername());
         case VALIDATED_USER -> matches(server.getSessionByID(producer.getSessionID()).getValidatedUser());
         case ADDRESS -> matches(producer.getAddress() != null ? producer.getAddress() : server.getSessionByID(producer.getSessionID()).getDefaultAddress());
         case PROTOCOL -> matches(producer.getProtocol());
         case CLIENT_ID -> matches(server.getSessionByID(producer.getSessionID()).getRemotingConnection().getClientID());
         case LOCAL_ADDRESS -> matches(server.getSessionByID(producer.getSessionID()).getRemotingConnection().getTransportConnection().getLocalAddress());
         case REMOTE_ADDRESS -> matches(server.getSessionByID(producer.getSessionID()).getRemotingConnection().getTransportConnection().getRemoteAddress());
         default -> true;
      };
   }
}

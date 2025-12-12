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

import org.apache.activemq.artemis.core.management.impl.view.SessionField;
import org.apache.activemq.artemis.core.server.ServerSession;

public class SessionPredicateFilterPart extends PredicateFilterPart<ServerSession> {

   private SessionField f;

   public SessionPredicateFilterPart(String field, String operation, String value) {
      super(operation, value);
      if (field != null && !field.isEmpty()) {
         f = SessionField.valueOfName(field);

         //for backward compatibility
         if (f == null) {
            f = SessionField.valueOf(field);
         }
      }
   }

   @Override
   public boolean filterPart(ServerSession session) throws Exception {
      return switch (f) {
         case ID -> matches(session.getName());
         case CONNECTION_ID -> matches(session.getConnectionID());
         case CONSUMER_COUNT -> matchesLong(session.getServerConsumers().size());
         case PRODUCER_COUNT -> matchesLong(session.getServerProducers().size());
         case PROTOCOL -> matches(session.getRemotingConnection().getProtocolName());
         case CLIENT_ID -> matches(session.getRemotingConnection().getClientID());
         case LOCAL_ADDRESS -> matches(session.getRemotingConnection().getTransportConnection().getLocalAddress());
         case REMOTE_ADDRESS -> matches(session.getRemotingConnection().getTransportConnection().getRemoteAddress());
         default -> true;
      };
   }
}

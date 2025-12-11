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
package org.apache.activemq.artemis.core.management.impl.view;

import org.apache.activemq.artemis.json.JsonObjectBuilder;
import java.util.Date;
import java.util.Objects;

import org.apache.activemq.artemis.core.management.impl.view.predicate.SessionFilterPredicate;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.utils.JsonLoader;

public class SessionView extends ActiveMQAbstractView<ServerSession> {

   private static final String defaultSortField = SessionField.ID.getName();

   public SessionView() {
      super();
      this.predicate = new SessionFilterPredicate();
   }

   @Override
   public Class getClassT() {
      return ServerSession.class;
   }

   @Override
   public JsonObjectBuilder toJson(ServerSession session) {
      JsonObjectBuilder obj = JsonLoader.createObjectBuilder()
         .add(SessionField.ID.getName(), toString(session.getName()))
         .add(SessionField.USER.getName(), toString(session.getUsername()))
         .add(SessionField.VALIDATED_USER.getName(), toString(session.getValidatedUser()))
         .add(SessionField.CREATION_TIME.getName(), new Date(session.getCreationTime()).toString())
         .add(SessionField.CONSUMER_COUNT.getName(), session.getConsumerCount())
         .add(SessionField.PRODUCER_COUNT.getName(), session.getProducerCount())
         .add(SessionField.CONNECTION_ID.getName(), session.getConnectionID().toString())
         .add(SessionField.CLIENT_ID.getName(), Objects.requireNonNullElse(session.getRemotingConnection().getClientID(), ""));
      return obj;
   }

   @Override
   public Object getField(ServerSession session, String fieldName) {
      SessionField field = SessionField.valueOfName(fieldName);

      return switch (field) {
         case ID -> session.getName();
         case USER -> session.getUsername();
         case VALIDATED_USER -> session.getValidatedUser();
         case CREATION_TIME -> new Date(session.getCreationTime());
         case CONSUMER_COUNT -> session.getConsumerCount();
         case PRODUCER_COUNT -> session.getProducerCount();
         case CONNECTION_ID -> session.getConnectionID();
         case CLIENT_ID -> session.getRemotingConnection().getClientID();
         default -> throw new IllegalArgumentException("Unsupported field, " + fieldName);
      };
   }

   @Override
   public String getDefaultOrderColumn() {
      return defaultSortField;
   }
}

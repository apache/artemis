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

import java.util.Objects;

import org.apache.activemq.artemis.core.management.impl.view.predicate.ProducerFilterPredicate;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ServerProducer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.json.JsonObjectBuilder;
import org.apache.activemq.artemis.utils.JsonLoader;

public class ProducerView extends ActiveMQAbstractView<ServerProducer> {

   private static final String defaultSortField = ProducerField.CREATION_TIME.getName();

   private final ActiveMQServer server;

   public ProducerView(ActiveMQServer server) {
      super();
      this.server = server;
      this.predicate = new ProducerFilterPredicate(server);
   }

   @Override
   public Class getClassT() {
      return ServerProducer.class;
   }

   @Override
   public JsonObjectBuilder toJson(ServerProducer producer) {
      ServerSession session = server.getSessionByID(producer.getSessionID());

      //if session is not available then consumer is not in valid state - ignore
      if (session == null) {
         return null;
      }

      JsonObjectBuilder obj = JsonLoader.createObjectBuilder()
         .add(ProducerField.ID.getName(), toString(producer.getID()))
         .add(ProducerField.NAME.getName(), toString(producer.getName()))
         .add(ProducerField.SESSION.getName(), toString(session.getName()))
         .add(ProducerField.CLIENT_ID.getName(), toString(session.getRemotingConnection().getClientID()))
         .add(ProducerField.USER.getName(), toString(session.getUsername()))
         .add(ProducerField.VALIDATED_USER.getName(), toString(session.getValidatedUser()))
         .add(ProducerField.PROTOCOL.getName(), toString(session.getRemotingConnection().getProtocolName()))
         .add(ProducerField.ADDRESS.getName(), toString(Objects.requireNonNullElse(producer.getAddress(), session.getDefaultAddress())))
         .add(ProducerField.LOCAL_ADDRESS.getName(), toString(session.getRemotingConnection().getTransportConnection().getLocalAddress()))
         .add(ProducerField.REMOTE_ADDRESS.getName(), toString(session.getRemotingConnection().getTransportConnection().getRemoteAddress()))
         .add(ProducerField.CREATION_TIME.getName(), toString(producer.getCreationTime()))
         .add(ProducerField.MESSAGE_SENT.getName(), producer.getMessagesSent())
         .add(ProducerField.MESSAGE_SENT_SIZE.getName(), producer.getMessagesSentSize())
         .add(ProducerField.LAST_PRODUCED_MESSAGE_ID.getName(), toString(producer.getLastProducedMessageID()));
      return obj;
   }

   @Override
   public Object getField(ServerProducer producer, String fieldName) {
      ServerSession session = server.getSessionByID(producer.getSessionID());

      //if session is not available then consumer is not in valid state - ignore
      if (session == null) {
         return null;
      }

      ProducerField field = ProducerField.valueOfName(fieldName);

      return switch (field) {
         case ID -> producer.getID();
         case SESSION -> session.getName();
         case USER -> session.getUsername();
         case VALIDATED_USER -> session.getValidatedUser();
         case CLIENT_ID -> session.getRemotingConnection().getClientID();
         case PROTOCOL -> session.getRemotingConnection().getProtocolName();
         case ADDRESS -> Objects.requireNonNullElse(producer.getAddress(), session.getDefaultAddress());
         case LOCAL_ADDRESS -> session.getRemotingConnection().getTransportConnection().getLocalAddress();
         case REMOTE_ADDRESS -> session.getRemotingConnection().getTransportConnection().getRemoteAddress();
         case CREATION_TIME -> producer.getCreationTime();
         default -> throw new IllegalArgumentException("Unsupported field, " + fieldName);
      };
   }

   @Override
   public String getDefaultOrderColumn() {
      return defaultSortField;
   }
}

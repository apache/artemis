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

package org.apache.activemq.artemis.jms.client.compatible1X;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MapMessage;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.jms.client.ActiveMQMapMessage;

public class ActiveMQMapCompatibleMessage extends ActiveMQMapMessage {

   @Override
   protected SimpleString checkPrefix(SimpleString address) {
      return ActiveMQCompatibleMessage.checkPrefix1X(address);
   }

   @Override
   public void setJMSReplyTo(Destination dest) throws JMSException {
      replyTo = ActiveMQCompatibleMessage.setCompatibleReplyTo(dest, message);
   }

   @Override
   public Destination getJMSReplyTo() throws JMSException {
      if (replyTo == null) {
         replyTo = ActiveMQCompatibleMessage.findCompatibleReplyTo(message);
      }
      return replyTo;
   }

   public ActiveMQMapCompatibleMessage(ClientSession session) {
      super(session);
   }

   public ActiveMQMapCompatibleMessage(ClientMessage message, ClientSession session) {
      super(message, session);
   }

   public ActiveMQMapCompatibleMessage() {
   }

   public ActiveMQMapCompatibleMessage(MapMessage foreign, ClientSession session) throws JMSException {
      super(foreign, session);
   }
}

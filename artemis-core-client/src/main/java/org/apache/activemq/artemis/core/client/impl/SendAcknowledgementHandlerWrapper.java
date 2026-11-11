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
package org.apache.activemq.artemis.core.client.impl;

import java.util.concurrent.Executor;

import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.client.SendAcknowledgementHandler;
import org.apache.activemq.artemis.utils.actors.Actor;

public class SendAcknowledgementHandlerWrapper implements SendAcknowledgementHandler {

   private SendAcknowledgementHandler wrapped;



   private final Actor<Message> messageActor;

   public SendAcknowledgementHandlerWrapper(SendAcknowledgementHandler wrapped, Executor executor) {
      this.wrapped = wrapped;
      messageActor = new Actor<>(executor, wrapped::sendAcknowledged);
   }


   @Override
   public void sendAcknowledged(Message message) {
      ICoreMessage msg = message.toCore();

      // It is possible that a SendAcknowledgementHandler might be called twice due to subsequent
      // packet confirmations on the same connection. Using this boolean avoids that possibility.
      if (!msg.isConfirmed()) {
         try {
            messageActor.act(message);
         } finally {
            msg.setConfirmed(true);
         }
      }
   }

   @Override
   public void sendFailed(Message message, Exception e) {
      ICoreMessage msg = message.toCore();
      if (!msg.isConfirmed()) {
         try {
            wrapped.sendFailed(message, e);
         } finally {
            msg.setConfirmed(true);
         }
      }
   }
}

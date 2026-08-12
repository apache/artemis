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
package org.apache.activemq.artemis.core.protocol.mqtt;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.spi.core.protocol.SessionCallback;
import org.apache.activemq.artemis.spi.core.remoting.ReadyListener;

public class MQTTSessionCallback implements SessionCallback {

   private final MQTTSession session;
   private final MQTTConnection connection;
   private final int defaultMaximumInFlightPublishMessages;

   public MQTTSessionCallback(MQTTSession session, MQTTConnection connection, int defaultMaximumInFlightPublishMessages) {
      this.session = session;
      this.connection = connection;
      this.defaultMaximumInFlightPublishMessages = defaultMaximumInFlightPublishMessages;
   }

   @Override
   public boolean supportsDirectDelivery() {
      return false;
   }

   @Override
   public boolean isWritable(ReadyListener callback, Object protocolContext) {
      return connection.isWritable(callback);
   }

   @Override
   public int sendMessage(MessageReference ref,
                          ServerConsumer consumer,
                          int deliveryCount) {
      try {
         session.getMqttPublishManager().publishToClient(ref.getMessage().toCore(), consumer);
      } catch (Exception e) {
         MQTTLogger.LOGGER.unableToSendMessage(session.getState().getClientId(), ref, e);
      }
      return 1;
   }

   @Override
   public boolean updateDeliveryCountAfterCancel(ServerConsumer consumer, MessageReference ref, boolean failed) {
      return false;
   }

   @Override
   public int sendLargeMessageContinuation(ServerConsumer consumerID,
                                           byte[] body,
                                           boolean continues,
                                           boolean requiresResponse) {
      return 1;
   }

   @Override
   public int sendLargeMessage(MessageReference ref,
                               ServerConsumer consumer,
                               long bodySize,
                               int deliveryCount) {
      return sendMessage(ref, consumer, deliveryCount);
   }

   @Override
   public void disconnect(ServerConsumer consumer, String errorMessage) {
      try {
         consumer.removeItself();
      } catch (Exception e) {
         MQTTLogger.LOGGER.errorDisconnectingConsumer(e);
      }
   }

   @Override
   public void afterDelivery() throws Exception {

   }

   @Override
   public void browserFinished(ServerConsumer consumer) {

   }

   @Override
   public boolean hasCredits(ServerConsumer consumerID) {
      return hasCredits(consumerID, null);
   }

   @Override
   public boolean hasCredits(ServerConsumer consumerID, MessageReference ref) {
      /*
       * [MQTT-3.3.4-9] The Server MUST NOT send more than Receive Maximum QoS 1 and QoS 2 PUBLISH packets for which it
       * has not received PUBACK, PUBCOMP, or PUBREC with a Reason Code of 128 or greater from the Client.
       *
       * Therefore, enforce flow-control based on the number of pending QoS 1 & 2 messages
       */
      int maxInFlightPublishMessages = connection.getReceiveMaximum() > 0 ? connection.getReceiveMaximum() : defaultMaximumInFlightPublishMessages;
      if (ref != null && ref.isDurable() == true && maxInFlightPublishMessages > 0 && session.getState().getSendQuota() >= maxInFlightPublishMessages) {
         return false;
      } else {
         return true;
      }
   }

   @Override
   public void sendProducerCreditsMessage(int credits, SimpleString address) {
   }

   @Override
   public void sendProducerCreditsFailMessage(int credits, SimpleString address) {
   }

   @Override
   public void closed() {
   }

}

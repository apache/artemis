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
package org.apache.activemq.artemis.core.protocol.stomp.v10;

import static org.apache.activemq.artemis.core.protocol.stomp.ActiveMQStompProtocolMessageBundle.BUNDLE;

import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;
import org.apache.activemq.artemis.core.protocol.stomp.ActiveMQStompException;
import org.apache.activemq.artemis.core.protocol.stomp.FrameEventListener;
import org.apache.activemq.artemis.core.protocol.stomp.Stomp;
import org.apache.activemq.artemis.core.protocol.stomp.StompConnection;
import org.apache.activemq.artemis.core.protocol.stomp.StompDecoder;
import org.apache.activemq.artemis.core.protocol.stomp.StompFrame;
import org.apache.activemq.artemis.core.protocol.stomp.StompVersions;
import org.apache.activemq.artemis.core.protocol.stomp.VersionedStompFrameHandler;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.utils.ExecutorFactory;

public class StompFrameHandlerV10 extends VersionedStompFrameHandler implements FrameEventListener {

   public StompFrameHandlerV10(StompConnection connection,
                               ScheduledExecutorService scheduledExecutorService,
                               ExecutorFactory factory) {
      super(connection, scheduledExecutorService, factory);
      decoder = new StompDecoder(this);
      decoder.init();
      connection.addStompEventListener(this);
   }

   @Override
   public StompFrame onConnect(StompFrame frame) {
      StompFrame response = null;
      Map<String, String> headers = frame.getHeadersMap();
      String login = headers.get(Stomp.Headers.Connect.LOGIN);
      String passcode = headers.get(Stomp.Headers.Connect.PASSCODE);
      String clientID = headers.get(Stomp.Headers.Connect.CLIENT_ID);
      String requestID = headers.get(Stomp.Headers.Connect.REQUEST_ID);

      try {
         connection.setClientID(clientID);
         connection.setLogin(login);
         connection.setPasscode(passcode);
         // Create session which will validate user - this will cache the session in the protocol manager
         connection.getSession();
         connection.setValid(true);

         response = new StompFrameV10(Stomp.Responses.CONNECTED);

         if (frame.hasHeader(Stomp.Headers.ACCEPT_VERSION)) {
            response.addHeader(Stomp.Headers.Connected.VERSION, StompVersions.V1_0.toString());
         }

         response.addHeader(Stomp.Headers.Connected.SESSION, connection.getID().toString());

         if (requestID != null) {
            response.addHeader(Stomp.Headers.Connected.RESPONSE_ID, requestID);
         }
      } catch (ActiveMQSecurityException e) {
         response = getFailedAuthenticationResponse(login);
      } catch (ActiveMQStompException e) {
         response = e.getFrame();
      }
      return response;
   }

   @Override
   public StompFrame onDisconnect(StompFrame frame) {
      return null;
   }

   @Override
   public StompFrame onUnsubscribe(StompFrame request) {
      StompFrame response = null;
      try {
         String destination = getDestination(request, Stomp.Headers.Unsubscribe.DESTINATION);
         String id = request.getHeader(Stomp.Headers.Unsubscribe.ID);
         String durableSubscriptionName = request.getHeader(Stomp.Headers.Unsubscribe.DURABLE_SUBSCRIBER_NAME);
         if (durableSubscriptionName == null) {
            durableSubscriptionName = request.getHeader(Stomp.Headers.Unsubscribe.DURABLE_SUBSCRIPTION_NAME);
         }
         if (durableSubscriptionName == null) {
            durableSubscriptionName = request.getHeader(Stomp.Headers.Unsubscribe.ACTIVEMQ_DURABLE_SUBSCRIPTION_NAME);
         }

         String subscriptionID = null;
         if (id != null) {
            subscriptionID = id;
         } else {
            if (destination == null) {
               ActiveMQStompException error = BUNDLE.needIDorDestination().setHandler(this);
               response = error.getFrame();
               return response;
            }
            subscriptionID = "subscription/" + destination;
         }

         connection.unsubscribe(subscriptionID, durableSubscriptionName);

      } catch (ActiveMQStompException e) {
         response = e.getFrame();
      } catch (Exception e) {
         ActiveMQStompException error = BUNDLE.errorHandleSend(e).setHandler(this);
         response = error.getFrame();
      }
      return response;
   }

   @Override
   public StompFrame onAck(StompFrame request) {
      StompFrame response = null;

      String messageID = request.getHeader(Stomp.Headers.Ack.MESSAGE_ID);
      String txID = request.getHeader(Stomp.Headers.TRANSACTION);

      if (txID != null) {
         ActiveMQServerLogger.LOGGER.stompTXAckNorSupported();
      }

      try {
         connection.acknowledge(messageID, null);
      } catch (ActiveMQStompException e) {
         response = e.getFrame();
      }

      return response;
   }

   @Override
   public StompFrame onStomp(StompFrame request) {
      return onUnknown(request.getCommand());
   }

   @Override
   public StompFrame onNack(StompFrame request) {
      return onUnknown(request.getCommand());
   }

   @Override
   public StompFrame createStompFrame(String command) {
      return new StompFrameV10(command);
   }

   @Override
   public void replySent(StompFrame reply) {
      if (reply.needsDisconnect()) {
         connection.destroy();
      }
   }

   @Override
   public void requestAccepted(StompFrame request) {
      // TODO Auto-generated method stub

   }

}

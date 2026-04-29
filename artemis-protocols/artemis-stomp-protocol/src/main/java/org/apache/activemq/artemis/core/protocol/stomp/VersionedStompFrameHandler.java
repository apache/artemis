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
package org.apache.activemq.artemis.core.protocol.stomp;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQBuffers;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.message.LargeBodyReader;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.protocol.stomp.Stomp.Headers;
import org.apache.activemq.artemis.core.protocol.stomp.v10.StompFrameHandlerV10;
import org.apache.activemq.artemis.core.protocol.stomp.v11.StompFrameHandlerV11;
import org.apache.activemq.artemis.core.protocol.stomp.v12.StompFrameHandlerV12;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.utils.ExecutorFactory;

import static org.apache.activemq.artemis.core.protocol.stomp.ActiveMQStompProtocolMessageBundle.BUNDLE;

public abstract class VersionedStompFrameHandler {

   protected static byte[] EMPTY_BODY = new byte[0];

   protected StompConnection connection;
   protected StompDecoder decoder;

   protected final ScheduledExecutorService scheduledExecutorService;
   protected final ExecutorFactory executorFactory;

   protected void disconnect() {
   }

   public static VersionedStompFrameHandler getHandler(StompConnection connection,
                                                       StompVersions version,
                                                       ScheduledExecutorService scheduledExecutorService,
                                                       ExecutorFactory executorFactory) {
      if (version == StompVersions.V1_0) {
         return new StompFrameHandlerV10(connection, scheduledExecutorService, executorFactory);
      }
      if (version == StompVersions.V1_1) {
         return new StompFrameHandlerV11(connection, scheduledExecutorService, executorFactory);
      }
      if (version == StompVersions.V1_2) {
         return new StompFrameHandlerV12(connection, scheduledExecutorService, executorFactory);
      }
      return null;
   }

   protected VersionedStompFrameHandler(StompConnection connection,
                                        ScheduledExecutorService scheduledExecutorService,
                                        ExecutorFactory executorFactory) {
      this.connection = connection;
      this.scheduledExecutorService = scheduledExecutorService;
      this.executorFactory = executorFactory;
   }

   public StompFrame decode(ActiveMQBuffer buffer) throws ActiveMQStompException {
      return decoder.decode(buffer);
   }

   public boolean hasBytes() {
      return decoder.hasBytes();
   }

   public StompDecoder getDecoder() {
      return decoder;
   }

   public StompFrame handleFrame(StompFrame request) {
      StompFrame response = null;

      if (Stomp.Commands.SEND.equals(request.getCommand())) {
         response = onSend(request);
      } else if (Stomp.Commands.ACK.equals(request.getCommand())) {
         response = onAck(request);
      } else if (Stomp.Commands.NACK.equals(request.getCommand())) {
         response = onNack(request);
      } else if (Stomp.Commands.BEGIN.equals(request.getCommand())) {
         response = onBegin(request);
      } else if (Stomp.Commands.COMMIT.equals(request.getCommand())) {
         response = onCommit(request);
      } else if (Stomp.Commands.ABORT.equals(request.getCommand())) {
         response = onAbort(request);
      } else if (Stomp.Commands.SUBSCRIBE.equals(request.getCommand())) {
         return handleSubscribe(request);
      } else if (Stomp.Commands.UNSUBSCRIBE.equals(request.getCommand())) {
         response = onUnsubscribe(request);
      } else if (Stomp.Commands.CONNECT.equals(request.getCommand())) {
         response = onConnect(request);
      } else if (Stomp.Commands.STOMP.equals(request.getCommand())) {
         response = onStomp(request);
      } else if (Stomp.Commands.DISCONNECT.equals(request.getCommand())) {
         response = onDisconnect(request);
      } else {
         response = onUnknown(request.getCommand());
      }

      if (response == null) {
         response = postprocess(request);
      } else {
         if (request.hasHeader(Stomp.Headers.RECEIPT_REQUESTED)) {
            response.addHeader(Stomp.Headers.Response.RECEIPT_ID, request.getHeader(Stomp.Headers.RECEIPT_REQUESTED));
         }
      }

      return response;
   }

   private StompFrame handleSubscribe(StompFrame request) {
      StompFrame response = null;
      try {
         StompPostReceiptFunction postProcessFunction = onSubscribe(request);
         response = postprocess(request);
         connection.sendFrame(response, postProcessFunction);
         return null;
      } catch (ActiveMQStompException e) {
         return e.getFrame();
      } catch (Exception e) {
         return new ActiveMQStompException(e.getMessage(), e).setHandler(this).getFrame();
      }

   }
   public abstract StompFrame onConnect(StompFrame frame);

   public abstract StompFrame onDisconnect(StompFrame frame);

   public abstract StompFrame onAck(StompFrame request);

   public abstract StompFrame onUnsubscribe(StompFrame request);

   public abstract StompFrame onStomp(StompFrame request);

   public abstract StompFrame onNack(StompFrame request);

   public abstract StompFrame createStompFrame(String command);

   public StompFrame onUnknown(String command) {
      ActiveMQStompException error = BUNDLE.unknownCommand(command).setHandler(this);
      StompFrame response = error.getFrame();
      return response;
   }

   public StompFrame handleReceipt(String receiptID) {
      StompFrame receipt = createStompFrame(Stomp.Responses.RECEIPT);
      receipt.addHeader(Stomp.Headers.Response.RECEIPT_ID, receiptID);

      return receipt;
   }

   public StompFrame onCommit(StompFrame request) {
      StompFrame response = null;

      String txID = request.getHeader(Stomp.Headers.TRANSACTION);
      if (txID == null) {
         ActiveMQStompException error = BUNDLE.needTxIDHeader().setHandler(this);
         response = error.getFrame();
         return response;
      }

      try {
         connection.commitTransaction(txID);
      } catch (ActiveMQStompException e) {
         response = e.getFrame();
      }
      return response;
   }

   public StompFrame onSend(StompFrame frame) {
      StompFrame response = null;
      try {
         connection.validate();
         String destination = getDestination(frame);
         RoutingType routingType = getRoutingType(frame.getHeader(Headers.Send.DESTINATION_TYPE), frame.getHeader(Headers.Send.DESTINATION));
         connection.autoCreateDestinationIfPossible(destination, routingType);
         connection.checkDestination(destination);
         connection.checkRoutingSemantics(destination, routingType);
         String txID = frame.getHeader(Stomp.Headers.TRANSACTION);

         long timestamp = System.currentTimeMillis();

         CoreMessage message = connection.createServerMessage();
         if (routingType != null) {
            message.setRoutingType(routingType);
         }
         message.setTimestamp(timestamp);
         message.setAddress(SimpleString.of(destination));
         StompUtils.copyStandardHeadersFromFrameToMessage(frame, message, getPrefix(frame));
         if (frame.hasHeader(Stomp.Headers.CONTENT_LENGTH)) {
            message.setType(Message.BYTES_TYPE);
            message.getBodyBuffer().writeBytes(frame.getBodyAsBytes());
         } else {
            message.setType(Message.TEXT_TYPE);
            String text = frame.getBody();
            message.getBodyBuffer().writeNullableSimpleString(SimpleString.of(text));
         }

         connection.sendServerMessage(message, txID);
      } catch (ActiveMQStompException e) {
         response = e.getFrame();
      } catch (Exception e) {
         ActiveMQStompException error = BUNDLE.errorHandleSend(e).setHandler(this);
         response = error.getFrame();
      }

      return response;
   }

   public StompFrame onBegin(StompFrame frame) {
      StompFrame response = null;
      String txID = frame.getHeader(Stomp.Headers.TRANSACTION);
      if (txID == null) {
         ActiveMQStompException error = BUNDLE.beginTxNoID().setHandler(this);
         response = error.getFrame();
      } else {
         try {
            connection.beginTransaction(txID);
         } catch (ActiveMQStompException e) {
            response = e.getFrame();
         }
      }
      return response;
   }

   public StompFrame onAbort(StompFrame request) {
      StompFrame response = null;
      String txID = request.getHeader(Stomp.Headers.TRANSACTION);

      if (txID == null) {
         ActiveMQStompException error = BUNDLE.abortTxNoID().setHandler(this);
         response = error.getFrame();
         return response;
      }

      try {
         connection.abortTransaction(txID);
      } catch (ActiveMQStompException e) {
         response = e.getFrame();
      }

      return response;
   }

   public StompPostReceiptFunction onSubscribe(StompFrame frame) throws Exception {
      String rawDestination = frame.getHeader(Headers.Subscribe.DESTINATION);
      RoutingType temporaryRoutingType = getTemporaryRoutingType(rawDestination);
      String destination = getDestination(frame);

      String selector = frame.getHeader(Stomp.Headers.Subscribe.SELECTOR);
      String ack = frame.getHeader(Stomp.Headers.Subscribe.ACK_MODE);
      String id = frame.getHeader(Stomp.Headers.Subscribe.ID);
      String durableSubscriptionName = frame.getHeader(Stomp.Headers.Subscribe.DURABLE_SUBSCRIPTION_NAME);
      if (durableSubscriptionName == null) {
         durableSubscriptionName = frame.getHeader(Stomp.Headers.Subscribe.DURABLE_SUBSCRIBER_NAME);
         if (durableSubscriptionName == null) {
            durableSubscriptionName = frame.getHeader(Stomp.Headers.Subscribe.ACTIVEMQ_DURABLE_SUBSCRIPTION_NAME);
         }
      }
      RoutingType routingType = getRoutingType(frame.getHeader(Headers.Subscribe.SUBSCRIPTION_TYPE), frame.getHeader(Headers.Subscribe.DESTINATION));
      boolean noLocal = false;
      if (frame.hasHeader(Stomp.Headers.Subscribe.NO_LOCAL)) {
         noLocal = Boolean.parseBoolean(frame.getHeader(Stomp.Headers.Subscribe.NO_LOCAL));
      } else if (frame.hasHeader(Stomp.Headers.Subscribe.ACTIVEMQ_NO_LOCAL)) {
         noLocal = Boolean.parseBoolean(frame.getHeader(Stomp.Headers.Subscribe.ACTIVEMQ_NO_LOCAL));
      }
      Integer consumerWindowSize = null;
      if (frame.hasHeader(Headers.Subscribe.CONSUMER_WINDOW_SIZE)) {
         consumerWindowSize = Integer.parseInt(frame.getHeader(Stomp.Headers.Subscribe.CONSUMER_WINDOW_SIZE));
      } else if (frame.hasHeader(Headers.Subscribe.ACTIVEMQ_PREFETCH_SIZE)) {
         consumerWindowSize = Integer.parseInt(frame.getHeader(Stomp.Headers.Subscribe.ACTIVEMQ_PREFETCH_SIZE));
      }
      return connection.subscribe(destination, selector, ack, id, durableSubscriptionName, noLocal, routingType, consumerWindowSize, temporaryRoutingType);
   }

   private RoutingType getTemporaryRoutingType(String rawDestination) {
      if (rawDestination != null) {
         SimpleString dest = SimpleString.of(rawDestination);
         for (Map.Entry<SimpleString, RoutingType> entry : connection.getManager().getTemporaryPrefixes().entrySet()) {
            if (dest.startsWith(entry.getKey())) {
               return entry.getValue();
            }
         }
      }
      return null;
   }

   public String getDestination(StompFrame request) throws Exception {
      return getDestination(request, Headers.Subscribe.DESTINATION);
   }

   public String getDestination(StompFrame request, String header) throws Exception {
      String destination = request.getHeader(header);
      if (destination == null) {
         return null;
      }
      return connection.getSession().getCoreSession().removePrefix(SimpleString.of(destination)).toString();
   }

   public String getPrefix(StompFrame request) throws Exception {
      String destination = request.getHeader(Headers.Send.DESTINATION);
      if (destination == null) {
         return null;
      }
      return Objects.toString(connection.getSession().getCoreSession().getPrefix(SimpleString.of(destination)), null);
   }

   public StompFrame postprocess(StompFrame request) {
      StompFrame response = null;
      if (request.hasHeader(Stomp.Headers.RECEIPT_REQUESTED)) {
         response = handleReceipt(request.getHeader(Stomp.Headers.RECEIPT_REQUESTED));
         if (request.getCommand().equals(Stomp.Commands.DISCONNECT)) {
            response.setNeedsDisconnect(true);
         }
      } else {
         //request null, disconnect if so.
         if (request.getCommand().equals(Stomp.Commands.DISCONNECT)) {
            this.connection.disconnect(false);
         }
      }
      return response;
   }

   public StompFrame createMessageFrame(ICoreMessage serverMessage,
                                        StompSubscription subscription,
                                        ServerConsumer consumer,
                                        int deliveryCount) throws ActiveMQException {
      final StompFrame frame = createStompFrame(Stomp.Responses.MESSAGE);

      if (subscription.getID() != null) {
         frame.addHeader(Stomp.Headers.Message.SUBSCRIPTION, subscription.getID());
      }

      if (serverMessage.isLargeMessage()) {
         populateFrameBodyFromLargeMessage(frame, serverMessage);
      } else {
         populateFrameBodyFromMessage(frame, serverMessage);
      }

      frame.addHeader(Stomp.Headers.Message.MESSAGE_ID, new StringBuilder(41).append(consumer.getID()).append(StompSession.MESSAGE_ID_SEPARATOR).append(serverMessage.getMessageID()).toString());
      StompUtils.copyStandardHeadersFromMessageToFrame(serverMessage, frame, deliveryCount);

      return frame;
   }

   private void populateFrameBodyFromMessage(StompFrame frame, ICoreMessage serverMessage) {
      final ActiveMQBuffer buffer = serverMessage.getReadOnlyBodyBuffer();
      final int bodyLength = buffer.readableBytes();

      if (bodyLength > 0) {
         if (serverMessage.containsProperty(Stomp.Headers.CONTENT_LENGTH) || serverMessage.getType() == Message.BYTES_TYPE) {
            final byte[] data = new byte[bodyLength];

            buffer.readBytes(data);

            frame.addHeader(Headers.CONTENT_LENGTH, String.valueOf(bodyLength));
            frame.setByteBody(data);
         } else {
            final SimpleString text = buffer.readNullableSimpleString();

            if (text != null) {
               frame.setByteBody(text.toString().getBytes(StandardCharsets.UTF_8));
            }
         }
      } else {
         frame.setByteBody(EMPTY_BODY);
      }
   }

   private void populateFrameBodyFromLargeMessage(StompFrame frame, ICoreMessage serverMessage) throws ActiveMQException {
      try (LargeBodyReader reader = serverMessage.getLargeBodyReader()) {
         reader.open();

         final int bodyLength = (int) reader.getSize();

         if (bodyLength > 0) {
            final byte[] bodyBytes = new byte[bodyLength];
            final ByteBuffer bodyBuffer = ByteBuffer.wrap(bodyBytes);

            reader.readInto(bodyBuffer);

            if (serverMessage.containsProperty(Stomp.Headers.CONTENT_LENGTH) || serverMessage.getType() == Message.BYTES_TYPE) {
               frame.addHeader(Headers.CONTENT_LENGTH, String.valueOf(bodyLength));
               frame.setByteBody(bodyBytes);
            } else {
               final ActiveMQBuffer buffer = ActiveMQBuffers.wrappedBuffer(bodyBuffer);

               buffer.writerIndex(bodyLength);

               final SimpleString text = buffer.readNullableSimpleString();

               if (text != null) {
                  frame.setByteBody(text.toString().getBytes(StandardCharsets.UTF_8));
               }
            }
         } else {
            frame.setByteBody(EMPTY_BODY);
         }
      }
   }

   /**
    * this method is called when a newer version of handler is created. It should take over the state of the decoder of
    * the existingHandler so that the decoding can be continued. For V10 handler it's never called.
    */
   public void initDecoder(VersionedStompFrameHandler existingHandler) {
      throw BUNDLE.invalidCall();
   }

   //sends an ERROR frame back to client if possible then close the connection
   public void onError(ActiveMQStompException e) {
      this.connection.sendFrame(e.getFrame(), null);
      connection.destroy();
   }

   private RoutingType getRoutingType(String typeHeader, String destination) throws Exception {
      // null is valid to return here so we know when the user didn't provide any routing info
      RoutingType routingType;
      if (typeHeader != null) {
         routingType = RoutingType.valueOf(typeHeader);
      } else {
         routingType = connection.getSession().getCoreSession().getRoutingTypeFromPrefix(SimpleString.of(destination), null);
      }
      return routingType;
   }

   protected StompFrame getFailedAuthenticationResponse(String login) {
      StompFrame response;
      response = createStompFrame(Stomp.Responses.ERROR);
      response.setNeedsDisconnect(true);
      String responseText = "Security Error occurred: User name [" + login + "] or password is invalid";
      response.setBody(responseText);
      response.addHeader(Stomp.Headers.Error.MESSAGE, responseText);
      return response;
   }
}

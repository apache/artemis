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
package org.apache.activemq.artemis.core.protocol.stomp.v11;

import static org.apache.activemq.artemis.core.protocol.stomp.ActiveMQStompProtocolMessageBundle.BUNDLE;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;
import org.apache.activemq.artemis.core.protocol.stomp.ActiveMQStompException;
import org.apache.activemq.artemis.core.protocol.stomp.FrameEventListener;
import org.apache.activemq.artemis.core.protocol.stomp.SimpleBytes;
import org.apache.activemq.artemis.core.protocol.stomp.Stomp;
import org.apache.activemq.artemis.core.protocol.stomp.StompConnection;
import org.apache.activemq.artemis.core.protocol.stomp.StompDecoder;
import org.apache.activemq.artemis.core.protocol.stomp.StompFrame;
import org.apache.activemq.artemis.core.protocol.stomp.VersionedStompFrameHandler;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.remoting.server.impl.RemotingServiceImpl;
import org.apache.activemq.artemis.core.server.ActiveMQScheduledComponent;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.spi.core.protocol.ConnectionEntry;
import org.apache.activemq.artemis.utils.ExecutorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class StompFrameHandlerV11 extends VersionedStompFrameHandler implements FrameEventListener {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected static final char ESC_CHAR = '\\';

   private HeartBeater heartBeater;

   public StompFrameHandlerV11(StompConnection connection,
                               ScheduledExecutorService scheduledExecutorService,
                               ExecutorFactory executorFactory) {
      super(connection, scheduledExecutorService, executorFactory);
      connection.addStompEventListener(this);
      decoder = new StompDecoderV11(this);
      decoder.init();
   }

   public ActiveMQScheduledComponent getHeartBeater() {
      return heartBeater;
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

         response = this.createStompFrame(Stomp.Responses.CONNECTED);

         // version
         response.addHeader(Stomp.Headers.Connected.VERSION, connection.getVersion());

         // session
         response.addHeader(Stomp.Headers.Connected.SESSION, connection.getID().toString());

         // server
         Object disableServerHeader = connection.getAcceptorUsed().getConfiguration().get(TransportConstants.DISABLE_STOMP_SERVER_HEADER);
         if (disableServerHeader == null || !Boolean.parseBoolean(disableServerHeader.toString())) {
            response.addHeader(Stomp.Headers.Connected.SERVER, connection.getActiveMQServerName());
         }

         if (requestID != null) {
            response.addHeader(Stomp.Headers.Connected.RESPONSE_ID, requestID);
         }

         // heart-beat. We need to start after connected frame has been sent.
         // otherwise the client may receive heart-beat before it receives
         // connected frame.
         String heartBeat = headers.get(Stomp.Headers.Connect.HEART_BEAT);

         if (heartBeat != null) {
            handleHeartBeat(heartBeat);
            if (heartBeater == null) {
               response.addHeader(Stomp.Headers.Connected.HEART_BEAT, "0,0");
            } else {
               response.addHeader(Stomp.Headers.Connected.HEART_BEAT, heartBeater.serverPingPeriod + "," + heartBeater.clientPingResponse);
            }
         }
      } catch (ActiveMQSecurityException e) {
         response = getFailedAuthenticationResponse(login);
      } catch (ActiveMQStompException e) {
         response = e.getFrame();
      }

      return response;
   }

   @Override
   protected StompFrame getFailedAuthenticationResponse(String login) {
      StompFrame response = super.getFailedAuthenticationResponse(login);
      response.addHeader(Stomp.Headers.CONTENT_TYPE, "text/plain");
      return response;
   }

   private void handleHeartBeat(String heartBeatHeader) throws ActiveMQStompException {
      String[] params = heartBeatHeader.split(",");
      if (params.length != 2) {
         throw new ActiveMQStompException(connection, "Incorrect heartbeat header " + heartBeatHeader);
      }

      //client ping
      long minPingInterval = Long.parseLong(params[0]);
      //client receive ping
      long minAcceptInterval = Long.parseLong(params[1]);

      if (heartBeater == null) {
         heartBeater = new HeartBeater(scheduledExecutorService, executorFactory.getExecutor(), minPingInterval, minAcceptInterval);
      }
   }

   @Override
   public StompFrame onDisconnect(StompFrame frame) {
      disconnect();
      return null;
   }

   @Override
   protected void disconnect() {
      if (this.heartBeater != null) {
         heartBeater.shutdown();
      }
   }

   @Override
   public StompFrame onUnsubscribe(StompFrame request) {
      StompFrame response = null;
      //unsubscribe in 1.1 only needs id header
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
      } else if (durableSubscriptionName == null) {
         response = BUNDLE.needSubscriptionID().setHandler(this).getFrame();
         return response;
      }

      try {
         connection.unsubscribe(subscriptionID, durableSubscriptionName);
      } catch (ActiveMQStompException e) {
         response = e.getFrame();
      }
      return response;
   }

   @Override
   public StompFrame onAck(StompFrame request) {
      StompFrame response = null;

      String messageID = request.getHeader(Stomp.Headers.Ack.MESSAGE_ID);
      String txID = request.getHeader(Stomp.Headers.TRANSACTION);
      String subscriptionID = request.getHeader(Stomp.Headers.Ack.SUBSCRIPTION);

      if (txID != null) {
         ActiveMQServerLogger.LOGGER.stompTXAckNorSupported();
      }

      if (subscriptionID == null) {
         response = BUNDLE.needSubscriptionID().setHandler(this).getFrame();
         return response;
      }

      try {
         connection.acknowledge(messageID, subscriptionID);
      } catch (ActiveMQStompException e) {
         response = e.getFrame();
      }

      return response;
   }

   @Override
   public StompFrame onStomp(StompFrame request) {
      if (!connection.isValid()) {
         return onConnect(request);
      }
      return null;
   }

   @Override
   public StompFrame onNack(StompFrame request) {
      //this eventually means discard the message (it never be redelivered again).
      //we can consider supporting redeliver to a different sub.
      return onAck(request);
   }

   @Override
   public void replySent(StompFrame reply) {
      if (reply.getCommand().equals(Stomp.Responses.CONNECTED)) {
         //kick off the pinger
         startHeartBeat();
      }

      if (reply.needsDisconnect()) {
         connection.disconnect(false);
      } else {
         //update ping
         if (heartBeater != null) {
            heartBeater.pinged();
         }
      }
   }

   private void startHeartBeat() {
      if (heartBeater != null && heartBeater.serverPingPeriod != 0) {
         heartBeater.start();
      }
   }

   public StompFrame createPingFrame() {
      StompFrame frame = createStompFrame(Stomp.Commands.STOMP);
      frame.setPing(true);
      return frame;
   }

   /*
    * HeartBeater functions:
    * (a) server ping: if server hasn't sent any frame within serverPingPeriod interval, send a ping
    * (b) configure connection ttl so that org.apache.activemq.artemis.core.remoting.server.impl.RemotingServiceImpl.FailureCheckAndFlushThread
    *     can deal with closing connections which go stale
    */
   private class HeartBeater extends ActiveMQScheduledComponent {

      private static final int MIN_SERVER_PING = 500;

      private boolean previouslyStopped = false;

      long serverPingPeriod = 0;
      long clientPingResponse;
      AtomicLong lastPingTimestamp = new AtomicLong(0);
      ConnectionEntry connectionEntry;

      private HeartBeater(ScheduledExecutorService scheduledExecutorService,
                          Executor executor,
                          final long clientPing,
                          final long clientAcceptPing) {
         super(scheduledExecutorService, executor, clientAcceptPing > MIN_SERVER_PING ? clientAcceptPing : MIN_SERVER_PING, TimeUnit.MILLISECONDS, false);

         if (clientAcceptPing != 0) {
            serverPingPeriod = super.getPeriod();
         }

         connectionEntry = ((RemotingServiceImpl) connection.getManager().getServer().getRemotingService()).getConnectionEntry(connection.getID());

         if (connectionEntry != null) {
            String heartBeatToTtlModifierStr = (String) connection.getAcceptorUsed().getConfiguration().get(TransportConstants.HEART_BEAT_TO_CONNECTION_TTL_MODIFIER);
            double heartBeatToTtlModifier = heartBeatToTtlModifierStr == null ? 2 : Double.parseDouble(heartBeatToTtlModifierStr);

            // the default response to the client
            clientPingResponse = (long) (connectionEntry.ttl / heartBeatToTtlModifier);

            if (clientPing != 0) {
               clientPingResponse = clientPing;
               String ttlMaxStr = (String) connection.getAcceptorUsed().getConfiguration().get(TransportConstants.CONNECTION_TTL_MAX);
               long ttlMax = ttlMaxStr == null ? Long.MAX_VALUE : Long.parseLong(ttlMaxStr);

               String ttlMinStr = (String) connection.getAcceptorUsed().getConfiguration().get(TransportConstants.CONNECTION_TTL_MIN);
               long ttlMin = ttlMinStr == null ? 1000 : Long.parseLong(ttlMinStr);

               /*
                * The connection's TTL should be one of the following:
                *   1) clientPing * heartBeatToTtlModifier
                *   2) ttlMin
                *   3) ttlMax
                */
               long connectionTtl = (long) (clientPing * heartBeatToTtlModifier);
               if (connectionTtl < ttlMin) {
                  connectionTtl = ttlMin;
                  clientPingResponse = (long) (ttlMin / heartBeatToTtlModifier);
               } else if (connectionTtl > ttlMax) {
                  connectionTtl = ttlMax;
                  clientPingResponse = (long) (ttlMax / heartBeatToTtlModifier);
               }

               logger.debug("Setting STOMP client TTL to: {}", connectionTtl);
               connectionEntry.ttl = connectionTtl;
            }
         }

      }

      public void shutdown() {
         this.stop();
         previouslyStopped = true;
      }

      @Override
      public void start() {
         if (!previouslyStopped) {
            super.start();
         }
      }

      public void pinged() {
         lastPingTimestamp.set(System.currentTimeMillis());
      }

      @Override
      public void run() {
         lastPingTimestamp.set(System.currentTimeMillis());
         connection.ping(createPingFrame());
      }
   }

   @Override
   public void requestAccepted(StompFrame request) {
   }

   @Override
   public StompFrame createStompFrame(String command) {
      return new StompFrameV11(command);
   }

   @Override
   public void initDecoder(VersionedStompFrameHandler existingHandler) {
      decoder.init(existingHandler.getDecoder());
   }

   protected class StompDecoderV11 extends StompDecoder {

      protected boolean isEscaping = false;
      protected SimpleBytes holder = new SimpleBytes(1024);

      public StompDecoderV11(StompFrameHandlerV11 handler) {
         super(handler);
      }

      @Override
      public void init(StompDecoder decoder) {
         this.data = decoder.data;
         this.workingBuffer = decoder.workingBuffer;
         this.pos = decoder.pos;
         this.command = decoder.command;
      }

      @Override
      public void init() {
         super.init();
         isEscaping = false;
         holder.reset();
      }

      @Override
      protected boolean parseCommand() throws ActiveMQStompException {
         int offset = 0;
         boolean nextChar = false;

         //check for ping
         // Some badly behaved STOMP clients add a \n *after* the terminating NUL char at the end of the
         // STOMP frame this can manifest as an extra \n at the beginning when the
         // next STOMP frame is read - we need to deal with this.
         // Besides, Stomp 1.2 allows for extra EOLs after NULL (i.e.
         // either "[\r]\n"s or "\n"s)
         while (true) {
            if (workingBuffer[offset] == NEW_LINE) {
               //client ping
               nextChar = false;
            } else if (workingBuffer[offset] == CR) {
               if (nextChar) {
                  throw BUNDLE.invalidTwoCRs().setHandler(handler);
               }
               nextChar = true;
            } else {
               break;
            }
            offset++;
            if (offset == data) {
               return false; //no more bytes
            }
         }

         if (nextChar) {
            throw BUNDLE.badCRs().setHandler(handler);
         }

         //if some EOLs have been processed, drop those bytes before parsing command
         if (offset > 0) {
            System.arraycopy(workingBuffer, offset, workingBuffer, 0, data - offset);
            data = data - offset;
            offset = 0;
         }

         if (data < 4) {
            // Need at least four bytes to identify the command
            // - up to 3 bytes for the command name + potentially another byte for a leading \n
            return false;
         }

         byte b = workingBuffer[offset];

         switch (b) {
            case A: {
               if (workingBuffer[offset + 1] == StompDecoder.B) {
                  if (!tryIncrement(offset + COMMAND_ABORT_LENGTH + eolLen)) {
                     return false;
                  }

                  // ABORT
                  command = COMMAND_ABORT;
               } else {
                  if (!tryIncrement(offset + COMMAND_ACK_LENGTH + eolLen)) {
                     return false;
                  }

                  // ACK
                  command = COMMAND_ACK;
               }
               break;
            }
            case B: {
               if (!tryIncrement(offset + COMMAND_BEGIN_LENGTH + eolLen)) {
                  return false;
               }

               // BEGIN
               command = COMMAND_BEGIN;

               break;
            }
            case C: {
               if (workingBuffer[offset + 2] == M) {
                  if (!tryIncrement(offset + COMMAND_COMMIT_LENGTH + eolLen)) {
                     return false;
                  }

                  // COMMIT
                  command = COMMAND_COMMIT;
               } else if (workingBuffer[offset + 7] == E) {
                  if (!tryIncrement(offset + COMMAND_CONNECTED_LENGTH + eolLen)) {
                     return false;
                  }

                  // CONNECTED
                  command = COMMAND_CONNECTED;
               } else {
                  if (!tryIncrement(offset + COMMAND_CONNECT_LENGTH + eolLen)) {
                     return false;
                  }

                  // CONNECT
                  command = COMMAND_CONNECT;
               }
               break;
            }
            case D: {
               if (!tryIncrement(offset + COMMAND_DISCONNECT_LENGTH + eolLen)) {
                  return false;
               }

               // DISCONNECT
               command = COMMAND_DISCONNECT;

               break;
            }
            case R: {
               if (!tryIncrement(offset + COMMAND_RECEIPT_LENGTH + eolLen)) {
                  return false;
               }

               // RECEIPT
               command = COMMAND_RECEIPT;

               break;
            }
            case E: {
               if (!tryIncrement(offset + COMMAND_ERROR_LENGTH + eolLen)) {
                  return false;
               }

               // ERROR
               command = COMMAND_ERROR;

               break;
            }
            case M: {
               if (!tryIncrement(offset + COMMAND_MESSAGE_LENGTH + eolLen)) {
                  return false;
               }

               // MESSAGE
               command = COMMAND_MESSAGE;

               break;
            }
            case S: {
               if (workingBuffer[offset + 1] == E) {
                  if (!tryIncrement(offset + COMMAND_SEND_LENGTH + eolLen)) {
                     return false;
                  }

                  // SEND
                  command = COMMAND_SEND;
               } else if (workingBuffer[offset + 1] == U) {
                  if (!tryIncrement(offset + COMMAND_SUBSCRIBE_LENGTH + eolLen)) {
                     return false;
                  }

                  // SUBSCRIBE
                  command = COMMAND_SUBSCRIBE;
               } else {
                  if (!tryIncrement(offset + StompDecoder.COMMAND_STOMP_LENGTH + eolLen)) {
                     return false;
                  }

                  // SUBSCRIBE
                  command = COMMAND_STOMP;
               }
               break;
            }
            case U: {
               if (!tryIncrement(offset + COMMAND_UNSUBSCRIBE_LENGTH + eolLen)) {
                  return false;
               }

               // UNSUBSCRIBE
               command = COMMAND_UNSUBSCRIBE;

               break;
            }
            case N: {
               if (!tryIncrement(offset + COMMAND_NACK_LENGTH + eolLen)) {
                  return false;
               }
               //NACK
               command = COMMAND_NACK;
               break;
            }
            default: {
               throwInvalid();
            }
         }

         checkEol();

         return true;
      }

      protected void checkEol() throws ActiveMQStompException {
         if (workingBuffer[pos - 1] != NEW_LINE) {
            throwInvalid();
         }
      }

      protected void throwUndefinedEscape(byte b) throws ActiveMQStompException {
         ActiveMQStompException error = BUNDLE.undefinedEscapeSequence(new String(new char[]{ESC_CHAR, (char) b})).setHandler(handler);
         error.setCode(ActiveMQStompException.UNDEFINED_ESCAPE);
         throw error;
      }

      @Override
      protected boolean parseHeaders() throws ActiveMQStompException {

      outer:
         while (true) {
            byte b = workingBuffer[pos++];

            switch (b) {
               //escaping
               case ESC_CHAR: {
                  if (isEscaping) {
                     //this is a backslash
                     holder.append(b);
                     isEscaping = false;
                  } else {
                     //begin escaping
                     isEscaping = true;
                  }
                  break;
               }
               case HEADER_SEPARATOR: {
                  if (inHeaderName) {
                     headerName = holder.getString();

                     holder.reset();

                     inHeaderName = false;

                     headerValueWhitespace = true;
                  }

                  whiteSpaceOnly = false;

                  break;
               }
               case StompDecoder.LN: {
                  if (isEscaping) {
                     holder.append(StompDecoder.NEW_LINE);
                     isEscaping = false;
                  } else {
                     holder.append(b);
                  }
                  break;
               }
               case StompDecoder.c: {
                  if (isEscaping) {
                     holder.append(StompDecoder.HEADER_SEPARATOR);
                     isEscaping = false;
                  } else {
                     holder.append(b);
                  }
                  break;
               }
               case StompDecoder.NEW_LINE: {
                  if (whiteSpaceOnly) {
                     // Headers are terminated by a blank line
                     readingHeaders = false;

                     break outer;
                  }

                  String headerValue = holder.getString();
                  holder.reset();

                  headers.put(headerName, headerValue);

                  if (headerName.equals(Stomp.Headers.CONTENT_LENGTH)) {
                     contentLength = Integer.parseInt(headerValue);
                  }

                  if (headerName.equals(Stomp.Headers.CONTENT_TYPE)) {
                     contentType = headerValue;
                  }

                  whiteSpaceOnly = true;

                  inHeaderName = true;

                  headerValueWhitespace = false;

                  break;
               }
               default: {
                  whiteSpaceOnly = false;

                  headerValueWhitespace = false;

                  if (isEscaping) {
                     throwUndefinedEscape(b);
                  }
                  holder.append(b);
               }
            }
            if (pos == data) {
               // Run out of data
               return false;
            }
         }
         return true;
      }

      @Override
      protected StompFrame parseBody() throws ActiveMQStompException {
         byte[] content = null;

         if (contentLength != -1) {
            if (pos + contentLength + 1 > data) {
               // Need more bytes
            } else {
               content = new byte[contentLength];

               System.arraycopy(workingBuffer, pos, content, 0, contentLength);

               pos += contentLength;

               //drain all the rest
               if (bodyStart == -1) {
                  bodyStart = pos;
               }

               while (pos < data) {
                  if (workingBuffer[pos++] == 0) {
                     break;
                  }
               }
            }
         } else {
            // Need to scan for terminating NUL

            if (bodyStart == -1) {
               bodyStart = pos;
            }

            while (pos < data) {
               if (workingBuffer[pos++] == 0) {
                  content = new byte[pos - bodyStart - 1];

                  System.arraycopy(workingBuffer, bodyStart, content, 0, content.length);

                  break;
               }
            }
         }

         if (content != null) {
            if (data > pos) {
               if (workingBuffer[pos] == NEW_LINE) {
                  pos++;
               }

               if (data > pos) {
                  // More data still in the buffer from the next packet
                  System.arraycopy(workingBuffer, pos, workingBuffer, 0, data - pos);
               }
            }

            data = data - pos;

            // reset

            StompFrame ret = new StompFrameV11(command, headers, content);

            init();

            return ret;
         } else {
            return null;
         }
      }
   }
}

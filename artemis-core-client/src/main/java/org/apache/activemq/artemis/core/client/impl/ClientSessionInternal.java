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
package org.apache.activemq.artemis.core.client.impl;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.SendAcknowledgementHandler;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.ConsumerContext;
import org.apache.activemq.artemis.spi.core.remoting.ReadyListener;
import org.apache.activemq.artemis.spi.core.remoting.SessionContext;

public interface ClientSessionInternal extends ClientSession {

   String getName();

   void acknowledge(ClientConsumer consumer, Message message) throws ActiveMQException;

   void individualAcknowledge(ClientConsumer consumer, Message message) throws ActiveMQException;

   boolean isCacheLargeMessageClient();

   int getMinLargeMessageSize();

   boolean isCompressLargeMessages();

   int getCompressionLevel();

   void expire(ClientConsumer consumer, Message message) throws ActiveMQException;

   void addConsumer(ClientConsumerInternal consumer);

   void addProducer(ClientProducerInternal producer);

   void removeConsumer(ClientConsumerInternal consumer) throws ActiveMQException;

   void removeProducer(ClientProducerInternal producer);

   void handleReceiveMessage(ConsumerContext consumerID, ClientMessageInternal message) throws Exception;

   void handleReceiveLargeMessage(ConsumerContext consumerID,
                                  ClientLargeMessageInternal clientLargeMessage,
                                  long largeMessageSize) throws Exception;

   void handleReceiveContinuation(ConsumerContext consumerID,
                                  byte[] chunk,
                                  int flowControlSize,
                                  boolean isContinues) throws Exception;

   void handleConsumerDisconnect(ConsumerContext consumerContext) throws ActiveMQException;

   void preHandleFailover(RemotingConnection connection);

   boolean handleFailover(RemotingConnection backupConnection, ActiveMQException cause);

   void postHandleFailover(RemotingConnection connection, boolean successful);

   RemotingConnection getConnection();

   void cleanUp(boolean failingOver) throws ActiveMQException;

   void setForceNotSameRM(boolean force);

   void workDone();

   void sendProducerCreditsMessage(int credits, SimpleString address);

   ClientProducerCredits getCredits(SimpleString address, boolean anon);

   void returnCredits(SimpleString address);

   void handleReceiveProducerCredits(SimpleString address, int credits);

   void handleReceiveProducerFailCredits(SimpleString address, int credits);

   ClientProducerCreditManager getProducerCreditManager();

   /**
    * This will set the address at the message
    */
   void setAddress(Message message, SimpleString address);

   void setPacketSize(int packetSize);

   void resetIfNeeded() throws ActiveMQException;

   void markRollbackOnly();

   /**
    * This is used internally to control and educate the user about using the thread boundaries properly. if more than
    * one thread is using the session simultaneously this will generate a big warning on the docs. There are a limited
    * number of places where we can call this such as acks and sends. otherwise we could get false warns
    */
   void startCall();

   /**
    * Opposite of {@link #startCall()}
    *
    * @see #startCall()
    */
   void endCall();

   void setStopSignal();

   boolean isConfirmationWindowEnabled();

   boolean isClosing();

   String getNodeId();

   boolean isWritable(ReadyListener callback);

   SessionContext getSessionContext();

   SendAcknowledgementHandler wrap(SendAcknowledgementHandler handler);
}

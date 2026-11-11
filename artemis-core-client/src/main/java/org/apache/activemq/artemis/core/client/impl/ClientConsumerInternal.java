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
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.utils.FutureLatch;

public interface ClientConsumerInternal extends ClientConsumer {

   SimpleString getQueueName();

   SimpleString getFilterString();

   int getPriority();

   boolean isBrowseOnly();

   void handleMessage(ClientMessageInternal message) throws Exception;

   void handleLargeMessage(ClientLargeMessageInternal clientLargeMessage, long largeMessageSize) throws Exception;

   void handleLargeMessageContinuation(byte[] chunk, int flowControlSize, boolean isContinues) throws Exception;

   void flowControl(int messageBytes, boolean discountSlowConsumer) throws ActiveMQException;

   void clear(boolean waitForOnMessage) throws ActiveMQException;

   Thread getCurrentThread();

   /**
    * To be called by things like MDBs during shutdown of the server
    */
   Thread prepareForClose(FutureLatch future) throws ActiveMQException;

   void clearAtFailover();

   int getClientWindowSize();

   int getInitialWindowSize();

   int getBufferSize();

   void cleanUp() throws ActiveMQException;

   void acknowledge(ClientMessage message) throws ActiveMQException;

   void individualAcknowledge(ClientMessage message) throws ActiveMQException;

   void flushAcks() throws ActiveMQException;

   void stop(boolean waitForOnMessage) throws ActiveMQException;

   void start();

   ClientSession.QueueQuery getQueueInfo();

   long getForceDeliveryCount();

   ClientConsumer setManualFlowMessageHandler(MessageHandler theHandler) throws ActiveMQException;

   void resetIfSlowConsumer();
}

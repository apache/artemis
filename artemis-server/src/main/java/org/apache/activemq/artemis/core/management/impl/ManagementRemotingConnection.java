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

package org.apache.activemq.artemis.core.management.impl;

import javax.security.auth.Subject;
import java.util.List;
import java.util.concurrent.Future;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.remoting.AuthenticationListener;
import org.apache.activemq.artemis.core.remoting.CloseListener;
import org.apache.activemq.artemis.core.remoting.FailureListener;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.protocol.SessionCallback;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.spi.core.remoting.ReadyListener;

public class ManagementRemotingConnection implements RemotingConnection {

   Subject subject;

   @Override
   public Object getID() {
      return "management";
   }

   @Override
   public long getCreationTime() {
      return 0;
   }

   @Override
   public String getRemoteAddress() {
      return "management";
   }

   @Override
   public void scheduledFlush() {
   }

   @Override
   public void addFailureListener(FailureListener listener) {
   }

   @Override
   public boolean removeFailureListener(FailureListener listener) {
      return false;
   }

   @Override
   public void addCloseListener(CloseListener listener) {
   }

   @Override
   public boolean removeCloseListener(CloseListener listener) {
      return false;
   }

   @Override
   public List<CloseListener> removeCloseListeners() {
      return null;
   }

   @Override
   public void setCloseListeners(List<CloseListener> listeners) {
   }

   @Override
   public List<FailureListener> getFailureListeners() {
      return null;
   }

   @Override
   public List<FailureListener> removeFailureListeners() {
      return null;
   }

   @Override
   public void setFailureListeners(List<FailureListener> listeners) {
   }

   @Override
   public ActiveMQBuffer createTransportBuffer(int size) {
      return null;
   }

   @Override
   public void fail(ActiveMQException me) {
   }

   @Override
   public Future asyncFail(ActiveMQException me) {
      return null;
   }

   @Override
   public void fail(ActiveMQException me, String scaleDownTargetNodeID) {
   }

   @Override
   public void destroy() {
   }

   @Override
   public Connection getTransportConnection() {
      return null;
   }

   @Override
   public boolean isClient() {
      return false;
   }

   @Override
   public boolean isDestroyed() {
      return false;
   }

   @Override
   public void disconnect(boolean criticalError) {
   }

   @Override
   public void disconnect(String scaleDownNodeID, boolean criticalError) {
   }

   @Override
   public boolean checkDataReceived() {
      return false;
   }

   @Override
   public void flush() {
   }

   @Override
   public boolean isWritable(ReadyListener callback) {
      return false;
   }

   @Override
   public void killMessage(SimpleString nodeID) {
   }

   @Override
   public boolean isSupportReconnect() {
      return false;
   }

   @Override
   public boolean isSupportsFlowControl() {
      return false;
   }

   @Override
   public void setSubject(Subject subject) {
      this.subject = subject;
   }

   @Override
   public Subject getSubject() {
      return subject;
   }

   @Override
   public void setAuthenticated() {
   }

   @Override
   public boolean isAuthenticated() {
      return subject != null;
   }

   @Override
   public void addAuthenticationListener(AuthenticationListener listener) {
   }

   @Override
   public boolean removeAuthenticationListener(AuthenticationListener listener) {
      return false;
   }

   @Override
   public String getProtocolName() {
      return null;
   }

   @Override
   public void setClientID(String cID) {
   }

   @Override
   public String getClientID() {
      return null;
   }

   @Override
   public String getTransportLocalAddress() {
      return "Management";
   }

   @Override
   public void bufferReceived(Object connectionID, ActiveMQBuffer buffer) {
   }

   @Override
   public void close() {
   }

   public SessionCallback callback = new SessionCallback() {
      @Override
      public boolean hasCredits(ServerConsumer consumerID) {
         return false;
      }

      @Override
      public void afterDelivery() throws Exception {
      }

      @Override
      public boolean updateDeliveryCountAfterCancel(ServerConsumer consumer, MessageReference ref, boolean failed) {
         return false;
      }

      @Override
      public void sendProducerCreditsMessage(int credits, SimpleString address) {
      }

      @Override
      public void sendProducerCreditsFailMessage(int credits, SimpleString address) {
      }

      @Override
      public int sendMessage(MessageReference ref, ServerConsumer consumerID, int deliveryCount) {
         return 0;
      }

      @Override
      public int sendLargeMessage(MessageReference ref,
                                  ServerConsumer consumerID,
                                  long bodySize,
                                  int deliveryCount) {
         return 0;
      }

      @Override
      public int sendLargeMessageContinuation(ServerConsumer consumerID,
                                              byte[] body,
                                              boolean continues,
                                              boolean requiresResponse) {
         return 0;
      }

      @Override
      public void closed() {

      }

      @Override
      public void disconnect(ServerConsumer consumerId, String message) {
      }

      @Override
      public boolean isWritable(ReadyListener callback, Object protocolContext) {
         return false;
      }

      @Override
      public void browserFinished(ServerConsumer consumer) {
      }
   };
}


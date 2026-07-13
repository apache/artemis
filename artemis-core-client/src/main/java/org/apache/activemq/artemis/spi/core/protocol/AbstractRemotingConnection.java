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
package org.apache.activemq.artemis.spi.core.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQInterruptedException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.client.ActiveMQClientLogger;
import org.apache.activemq.artemis.core.client.ActiveMQClientMessageBundle;
import org.apache.activemq.artemis.core.remoting.AuthenticationListener;
import org.apache.activemq.artemis.core.remoting.CloseListener;
import org.apache.activemq.artemis.core.remoting.FailureListener;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.spi.core.remoting.ReadyListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

import javax.security.auth.Subject;

public abstract class AbstractRemotingConnection implements RemotingConnection {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   protected final List<FailureListener> failureListeners = new CopyOnWriteArrayList<>();
   protected final List<CloseListener> closeListeners = new CopyOnWriteArrayList<>();
   protected final List<AuthenticationListener> authenticationListeners = new CopyOnWriteArrayList<>();
   protected final Connection transportConnection;
   protected final Executor executor;
   protected final long creationTime;
   protected volatile boolean destroyed;
   protected volatile boolean dataReceived;
   private String clientId;
   private Subject subject;
   private volatile boolean authenticated;

   public AbstractRemotingConnection(final Connection transportConnection, final Executor executor) {
      this.transportConnection = transportConnection;
      this.transportConnection.setProtocolConnection(this);
      this.executor = executor;
      this.creationTime = System.currentTimeMillis();
   }

   @Override
   public void scheduledFlush() {
      flush();
   }

   @Override
   public List<FailureListener> getFailureListeners() {
      return new ArrayList<>(failureListeners);
   }

   @Override
   public boolean isClient() {
      return false;
   }

   @Override
   public boolean isDestroyed() {
      return destroyed;
   }

   @Override
   public void flush() {
      // noop
   }

   @Override
   public boolean isWritable(ReadyListener callback) {
      return transportConnection.isWritable(callback);
   }

   protected void callFailureListeners(final ActiveMQException me, String scaleDownTargetNodeID) {
      final List<FailureListener> listenersClone = new ArrayList<>(failureListeners);

      for (final FailureListener listener : listenersClone) {
         try {
            listener.connectionFailed(me, false, scaleDownTargetNodeID);
         } catch (ActiveMQInterruptedException interrupted) {
            // this is an expected behaviour.. no warn or error here
            logger.debug("thread interrupted", interrupted);
         } catch (final Throwable t) {
            // Failure of one listener to execute shouldn't prevent others
            // from
            // executing
            ActiveMQClientLogger.LOGGER.errorCallingFailureListener(t);
         }
      }
   }

   protected void callClosingListeners() {
      final List<CloseListener> listenersClone = new ArrayList<>(closeListeners);

      for (final CloseListener listener : listenersClone) {
         try {
            listener.connectionClosed();
         } catch (final Throwable t) {
            // Failure of one listener to execute shouldn't prevent others
            // from
            // executing
            ActiveMQClientLogger.LOGGER.errorCallingFailureListener(t);
         }
      }
   }

   @Override
   public void setFailureListeners(final List<FailureListener> listeners) {
      failureListeners.clear();

      failureListeners.addAll(listeners);
   }

   @Override
   public Object getID() {
      return transportConnection.getID();
   }

   public String getLocalAddress() {
      return transportConnection.getLocalAddress();
   }

   @Override
   public String getRemoteAddress() {
      return transportConnection.getRemoteAddress();
   }

   @Override
   public void addFailureListener(final FailureListener listener) {
      if (listener == null) {
         throw ActiveMQClientMessageBundle.BUNDLE.failListenerCannotBeNull();
      }
      failureListeners.add(listener);
   }

   @Override
   public boolean removeFailureListener(final FailureListener listener) {
      if (listener == null) {
         throw ActiveMQClientMessageBundle.BUNDLE.failListenerCannotBeNull();
      }

      return failureListeners.remove(listener);
   }

   @Override
   public void addCloseListener(final CloseListener listener) {
      if (listener == null) {
         throw ActiveMQClientMessageBundle.BUNDLE.closeListenerCannotBeNull();
      }

      closeListeners.add(listener);
   }

   @Override
   public boolean removeCloseListener(final CloseListener listener) {
      if (listener == null) {
         throw ActiveMQClientMessageBundle.BUNDLE.closeListenerCannotBeNull();
      }

      return closeListeners.remove(listener);
   }

   @Override
   public List<CloseListener> removeCloseListeners() {
      List<CloseListener> deletedCloseListeners = new ArrayList<>(closeListeners);
      closeListeners.clear();
      return deletedCloseListeners;
   }

   @Override
   public List<FailureListener> removeFailureListeners() {
      List<FailureListener> deletedFailureListeners = getFailureListeners();
      failureListeners.clear();
      return deletedFailureListeners;
   }

   @Override
   public void setCloseListeners(List<CloseListener> listeners) {
      closeListeners.clear();

      closeListeners.addAll(listeners);
   }

   @Override
   public ActiveMQBuffer createTransportBuffer(final int size) {
      return transportConnection.createTransportBuffer(size);
   }

   @Override
   public Connection getTransportConnection() {
      return transportConnection;
   }

   @Override
   public long getCreationTime() {
      return creationTime;
   }

   @Override
   public boolean checkDataReceived() {
      boolean res = dataReceived;

      dataReceived = false;

      return res;
   }

   // This can be called concurrently by more than one thread so needs to be locked
   @Override
   public void fail(final ActiveMQException me) {
      fail(me, null);
   }

   @Override
   public void close() {
      fail(new ActiveMQException());
   }

   @Override
   public Future asyncFail(final ActiveMQException me) {

      FutureTask<Void> task = new FutureTask(() -> {
         fail(me);
         return null;
      });

      if (executor == null) {
         // only tests cases can do this
         task.run();
      } else {
         executor.execute(task);
      }
      return task;
   }

   @Override
   public void bufferReceived(final Object connectionID, final ActiveMQBuffer buffer) {
      dataReceived = true;
   }

   @Override
   public void killMessage(SimpleString nodeID) {
      // noop
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
      if (!this.authenticated) {
         this.authenticated = true;

         final List<AuthenticationListener> listeners = new ArrayList<>(authenticationListeners);
         for (final AuthenticationListener listener : listeners) {
            try {
               listener.connectionAuthenticated();
            } catch (final Throwable t) {
               ActiveMQClientLogger.LOGGER.errorCallingAuthenticationListener(t);
            }
         }
      }
   }

   @Override
   public boolean isAuthenticated() {
      return authenticated;
   }

   @Override
   public void addAuthenticationListener(final AuthenticationListener listener) {
      Objects.requireNonNull(listener);
      authenticationListeners.add(listener);
   }

   @Override
   public boolean removeAuthenticationListener(final AuthenticationListener listener) {
      Objects.requireNonNull(listener);
      return authenticationListeners.remove(listener);
   }

   @Override
   public void setClientID(String clientId) {
      this.clientId = clientId;
   }

   @Override
   public String getClientID() {
      return clientId;
   }

   @Override
   public String getTransportLocalAddress() {
      return transportConnection.getLocalAddress();
   }
}

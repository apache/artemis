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

import javax.security.auth.Subject;
import java.util.List;
import java.util.concurrent.Future;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.DisconnectReason;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.remoting.AuthenticationListener;
import org.apache.activemq.artemis.core.remoting.CloseListener;
import org.apache.activemq.artemis.core.remoting.FailureListener;
import org.apache.activemq.artemis.spi.core.remoting.BufferHandler;
import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.spi.core.remoting.ReadyListener;

/**
 * A RemotingConnection is a connection between a client and a server.
 * <p>
 * Perhaps a better name for this class now would be ProtocolConnection as this represents the link with the used
 * protocol
 */
public interface RemotingConnection extends BufferHandler {

   /**
    * {@return the unique id of the {@link RemotingConnection}}
    */
   Object getID();

   /**
    * {@return the creation time of the {@link RemotingConnection}}
    */
   long getCreationTime();

   /**
    * {@return a string representation of the remote address of this connection}
    */
   String getRemoteAddress();

   void scheduledFlush();

   /**
    * add a failure listener.
    * <p>
    * The listener will be called in the event of connection failure.
    *
    * @param listener the listener
    */
   void addFailureListener(FailureListener listener);

   /**
    * remove the failure listener
    *
    * @param listener the lister to remove
    * @return true if removed
    */
   boolean removeFailureListener(FailureListener listener);

   /**
    * add a CloseListener.
    * <p>
    * This will be called in the event of the connection being closed.
    *
    * @param listener the listener to add
    */
   void addCloseListener(CloseListener listener);

   /**
    * remove a Close Listener
    *
    * @param listener the listener to remove
    * @return true if removed
    */
   boolean removeCloseListener(CloseListener listener);

   List<CloseListener> removeCloseListeners();

   void setCloseListeners(List<CloseListener> listeners);

   /**
    * {@return all the failure listeners}
    */
   List<FailureListener> getFailureListeners();

   List<FailureListener> removeFailureListeners();

   /**
    * set the failure listeners.
    * <p>
    * These will be called in the event of the connection being closed. Any previously added listeners will be removed.
    *
    * @param listeners the listeners to add.
    */
   void setFailureListeners(List<FailureListener> listeners);

   /**
    * creates a new ActiveMQBuffer of the specified size. For the purpose of i/o outgoing packets
    *
    * @param size the size of buffer required
    * @return the buffer
    */
   ActiveMQBuffer createTransportBuffer(int size);

   /**
    * called when the underlying connection fails.
    *
    * @param me the exception that caused the failure
    */
   void fail(ActiveMQException me);

   void close();

   /**
    * Same thing as fail, but using an executor. semantic of send here, is asynchrounous.
    */
   Future asyncFail(ActiveMQException me);

   /**
    * called when the underlying connection fails.
    *
    * @param me                    the exception that caused the failure
    * @param scaleDownTargetNodeID the ID of the node where scale down is targeted
    */
   void fail(ActiveMQException me, String scaleDownTargetNodeID);

   /**
    * destroys this connection.
    */
   void destroy();

   /**
    * return the underlying Connection.
    *
    * @return the connection
    */
   Connection getTransportConnection();

   /**
    * {@return true if the {@link RemotingConnection} is a client, otherwise false}
    */
   boolean isClient();

   /**
    * {@return true if this {@link RemotingConnection} has been destroyed, otherwise false}
    */
   boolean isDestroyed();

   /**
    * Disconnect the connection, closing all channels
    */
   void disconnect(boolean criticalError);

   /**
    * Disconnect the connection, closing all channels
    */
   void disconnect(String scaleDownNodeID, boolean criticalError);

   /**
    * Disconnect the connection, closing all channels
    */
   default void disconnect(DisconnectReason reason, String targetNodeID, TransportConfiguration targetConnector) {
      disconnect(reason.isScaleDown() ? targetNodeID : null, reason.isCriticalError());
   }

   /**
    * {@return true if any data has been received since the last time this method was called}
    */
   boolean checkDataReceived();

   /**
    * flush all outstanding data from the connection.
    */
   void flush();

   boolean isWritable(ReadyListener callback);

   /**
    * if slow consumer is killed,send the msessage to client.
    */
   void killMessage(SimpleString nodeID);

   /**
    * This will check if reconnects are supported on the protocol and configuration. In case it's not supported a
    * connection failure could remove messages right away from pending deliveries.
    */
   boolean isSupportReconnect();

   /**
    * Return true if the protocol supports flow control. This is because in some cases we may need to hold message
    * producers in cases like disk full. If the protocol doesn't support it we trash the connection and throw
    * exceptions.
    */
   boolean isSupportsFlowControl();

   /**
    * sets the currently associated subject for this connection
    */
   void setSubject(Subject subject);

   /**
    * the possibly null subject associated with this connection
    */
   Subject getSubject();

   /**
    * marks this connection as authenticated
    */
   void setAuthenticated();

   /**
    * {@return true if the {@link RemotingConnection} is authenticated, otherwise false}
    */
   boolean isAuthenticated();

   /**
    * add a AuthenticationListener.
    * <p>
    * This will be called when the connection is authenticated.
    *
    * @param listener the listener to add
    */
   void addAuthenticationListener(AuthenticationListener listener);

   /**
    * remove a AuthenticationListener
    *
    * @param listener the listener to remove
    * @return true if removed
    */
   boolean removeAuthenticationListener(AuthenticationListener listener);

   /**
    * {@return the name of the protocol for this Remoting Connection}
    */
   String getProtocolName();

   /**
    * Sets the client ID associated with this connection
    */
   void setClientID(String cID);

   /**
    * {@return the Client ID associated with this connection}
    */
   String getClientID();

   /**
    * Returns a string representation of the local address this connection is connected to This is useful when the
    * server is configured at 0.0.0.0 (or multiple IPs). This will give you the actual IP that's being used.
    *
    * @return the local address of transport connection
    */
   String getTransportLocalAddress();

   default boolean isSameTarget(TransportConfiguration... configs) {
      return getTransportConnection().isSameTarget(configs);
   }
}

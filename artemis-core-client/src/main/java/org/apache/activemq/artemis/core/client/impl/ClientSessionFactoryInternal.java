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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.SessionFailureListener;
import org.apache.activemq.artemis.utils.ConfirmationWindowWarning;

public interface ClientSessionFactoryInternal extends ClientSessionFactory {

   void causeExit();

   void addFailureListener(SessionFailureListener listener);

   boolean removeFailureListener(SessionFailureListener listener);

   boolean waitForTopology(long timeout, TimeUnit unit);

   String getPrimaryNodeId();

   // for testing

   int numConnections();

   int numSessions();

   void removeSession(ClientSessionInternal session, boolean failingOver);

   void connect(int reconnectAttempts) throws ActiveMQException;

   /**
    * @deprecated This method is no longer acceptable to connect. Replaced by
    * {@link ClientSessionFactoryInternal#connect(int)}.
    */
   @Deprecated
   void connect(int reconnectAttempts, boolean failoverOnInitialConnection) throws ActiveMQException;

   void setBackupConnector(TransportConfiguration live, TransportConfiguration backUp);

   Object getConnector();

   Object getBackupConnector();

   void setReconnectAttempts(int i);

   ConfirmationWindowWarning getConfirmationWindowWarning();

   Lock lockFailover();

   boolean waitForRetry(long interval);
}

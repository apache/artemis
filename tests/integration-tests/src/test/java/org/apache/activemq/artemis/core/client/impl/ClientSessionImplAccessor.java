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

import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.Executor;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.spi.core.remoting.SessionContext;

public class ClientSessionImplAccessor {

   public static void setName(ClientSessionImpl session, String name) {
      try {
         Field nameField = ClientSessionImpl.class.getDeclaredField("name");
         nameField.setAccessible(true);
         nameField.set(session, name);
      } catch (Exception e) {
         throw new RuntimeException("Failed to set name field", e);
      }
   }

   public static ClientSessionImpl createSession(ClientSessionFactoryInternal sessionFactory,
                                                   String name,
                                                   String username,
                                                   String password,
                                                   boolean xa,
                                                   boolean autoCommitSends,
                                                   boolean autoCommitAcks,
                                                   boolean preAcknowledge,
                                                   boolean blockOnAcknowledge,
                                                   boolean autoGroup,
                                                   int ackBatchSize,
                                                   int consumerWindowSize,
                                                   int consumerMaxRate,
                                                   int confirmationWindowSize,
                                                   int producerWindowSize,
                                                   int producerMaxRate,
                                                   boolean blockOnNonDurableSend,
                                                   boolean blockOnDurableSend,
                                                   boolean cacheLargeMessageClient,
                                                   int minLargeMessageSize,
                                                   boolean compressLargeMessages,
                                                   int compressionLevel,
                                                   int initialMessagePacketSize,
                                                   String groupID,
                                                   int onMessageCloseTimeout,
                                                   SessionContext sessionContext,
                                                   Executor executor,
                                                   Executor confirmationExecutor,
                                                   Executor flowControlExecutor,
                                                   Executor closeExecutor) throws ActiveMQException {

      ClientSessionImpl session = new ClientSessionImpl(sessionFactory, name, username, password, xa, autoCommitSends,
                                    autoCommitAcks, preAcknowledge, blockOnAcknowledge, autoGroup,
                                    ackBatchSize, consumerWindowSize, consumerMaxRate, confirmationWindowSize,
                                    producerWindowSize, producerMaxRate, blockOnNonDurableSend, blockOnDurableSend,
                                    cacheLargeMessageClient, minLargeMessageSize, compressLargeMessages,
                                    compressionLevel, initialMessagePacketSize, groupID, onMessageCloseTimeout,
                                    sessionContext, executor, confirmationExecutor, flowControlExecutor, closeExecutor);

      try {
         ClientSessionFactoryImpl factoryImpl = (ClientSessionFactoryImpl) sessionFactory;
         Field sessionsField = ClientSessionFactoryImpl.class.getDeclaredField("sessions");
         sessionsField.setAccessible(true);
         @SuppressWarnings("unchecked")
         Set<ClientSessionInternal> sessions = (Set<ClientSessionInternal>) sessionsField.get(factoryImpl);
         synchronized (sessions) {
            sessions.add(session);
         }
      } catch (Exception e) {
         throw new RuntimeException("Failed to access sessions field", e);
      }

      return session;
   }

   public static Executor getExecutor(ClientSessionImpl session) {
      try {
         Field executorField = ClientSessionImpl.class.getDeclaredField("executor");
         executorField.setAccessible(true);
         return (Executor) executorField.get(session);
      } catch (Exception e) {
         throw new RuntimeException("Failed to get executor field", e);
      }
   }

   public static Executor getConfirmationExecutor(ClientSessionImpl session) {
      try {
         Field confirmationExecutorField = ClientSessionImpl.class.getDeclaredField("confirmationExecutor");
         confirmationExecutorField.setAccessible(true);
         return (Executor) confirmationExecutorField.get(session);
      } catch (Exception e) {
         throw new RuntimeException("Failed to get confirmationExecutor field", e);
      }
   }

   public static Executor getFlowControlExecutor(ClientSessionImpl session) {
      try {
         Field flowControlExecutorField = ClientSessionImpl.class.getDeclaredField("flowControlExecutor");
         flowControlExecutorField.setAccessible(true);
         return (Executor) flowControlExecutorField.get(session);
      } catch (Exception e) {
         throw new RuntimeException("Failed to get flowControlExecutor field", e);
      }
   }

   public static Executor getCloseExecutor(ClientSessionImpl session) {
      try {
         Field closeExecutorField = ClientSessionImpl.class.getDeclaredField("closeExecutor");
         closeExecutorField.setAccessible(true);
         return (Executor) closeExecutorField.get(session);
      } catch (Exception e) {
         throw new RuntimeException("Failed to get closeExecutor field", e);
      }
   }

}

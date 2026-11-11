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
package org.apache.activemq.artemis.jms.client;

import javax.jms.JMSException;
import javax.jms.Session;
import javax.jms.XAQueueConnection;
import javax.jms.XAQueueSession;
import javax.jms.XASession;
import javax.jms.XATopicConnection;
import javax.jms.XATopicSession;

import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;

/**
 * Apache Artemis implementation of a JMS XAConnection.
 * <p>
 * The flat implementation of {@link XATopicConnection} and {@link XAQueueConnection} is per design, following common
 * practices of JMS 1.1.
 */
public final class ActiveMQXAConnection extends ActiveMQConnection implements XATopicConnection, XAQueueConnection {

   public ActiveMQXAConnection(final ConnectionFactoryOptions options,
                               final String username,
                               final String password,
                               final int connectionType,
                               final String clientID,
                               final int dupsOKBatchSize,
                               final int transactionBatchSize,
                               final boolean cacheDestinations,
                               final boolean enable1xNaming,
                               final ClientSessionFactory sessionFactory) {
      super(options, username, password, connectionType, clientID, dupsOKBatchSize, transactionBatchSize, cacheDestinations, enable1xNaming, sessionFactory);
   }

   @Override
   public synchronized XASession createXASession() throws JMSException {
      checkClosed();
      return (XASession) createSessionInternal(isXA(), true, Session.SESSION_TRANSACTED, ActiveMQSession.TYPE_GENERIC_SESSION);
   }

   @Override
   public synchronized XAQueueSession createXAQueueSession() throws JMSException {
      checkClosed();
      return (XAQueueSession) createSessionInternal(isXA(), true, Session.SESSION_TRANSACTED, ActiveMQSession.TYPE_QUEUE_SESSION);

   }

   @Override
   public synchronized XATopicSession createXATopicSession() throws JMSException {
      checkClosed();
      return (XATopicSession) createSessionInternal(isXA(), true, Session.SESSION_TRANSACTED, ActiveMQSession.TYPE_TOPIC_SESSION);
   }

   @Override
   protected boolean isXA() {
      return true;
   }

}

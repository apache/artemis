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
package org.apache.activemq.artemis.api.core.client;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;

/**
 * A ClientSessionFactory is the entry point to create and configure Apache Artemis resources to produce and consume
 * messages.
 * <p>
 * It is possible to configure a factory using the setter methods only if no session has been created. Once a session is
 * created, the configuration is fixed and any call to a setter method will throw an IllegalStateException.
 */
public interface ClientSessionFactory extends AutoCloseable {

   /**
    * Creates a session with XA transaction semantics.
    *
    * @return a ClientSession with XA transaction semantics
    * @throws ActiveMQException if an exception occurs while creating the session
    */
   ClientSession createXASession() throws ActiveMQException;

   /**
    * Creates a <em>transacted</em> session.
    * <p>
    * It is up to the client to commit when sending and acknowledging messages.
    *
    * @return a transacted ClientSession
    * @throws ActiveMQException if an exception occurs while creating the session
    * @see ClientSession#commit()
    */
   ClientSession createTransactedSession() throws ActiveMQException;

   /**
    * Creates a <em>non-transacted</em> session. Message sends and acknowledgements are automatically committed by the
    * session. <em>This does not mean that messages are automatically acknowledged</em>, only that when messages are
    * acknowledged, the session will automatically commit the transaction containing the acknowledgements.
    *
    * @return a non-transacted ClientSession
    * @throws ActiveMQException if an exception occurs while creating the session
    */
   ClientSession createSession() throws ActiveMQException;

   /**
    * Creates a session.
    *
    * @param autoCommitSends {@code true} to automatically commit message sends, {@code false} to commit manually
    * @param autoCommitAcks  {@code true} to automatically commit message acknowledgement, {@code false} to commit
    *                        manually
    * @return a ClientSession
    * @throws ActiveMQException if an exception occurs while creating the session
    */
   ClientSession createSession(boolean autoCommitSends, boolean autoCommitAcks) throws ActiveMQException;

   /**
    * Creates a session.
    *
    * @param autoCommitSends {@code true} to automatically commit message sends, {@code false} to commit manually
    * @param autoCommitAcks  {@code true} to automatically commit message acknowledgement, {@code false} to commit
    *                        manually
    * @param ackBatchSize    the batch size of the acknowledgements
    * @return a ClientSession
    * @throws ActiveMQException if an exception occurs while creating the session
    */
   ClientSession createSession(boolean autoCommitSends,
                               boolean autoCommitAcks,
                               int ackBatchSize) throws ActiveMQException;

   /**
    * Creates a session.
    *
    * @param xa              whether the session support XA transaction semantic or not
    * @param autoCommitSends {@code true} to automatically commit message sends, {@code false} to commit manually
    * @param autoCommitAcks  {@code true} to automatically commit message acknowledgement, {@code false} to commit
    *                        manually
    * @return a ClientSession
    * @throws ActiveMQException if an exception occurs while creating the session
    */
   ClientSession createSession(boolean xa, boolean autoCommitSends, boolean autoCommitAcks) throws ActiveMQException;

   /**
    * Creates a session.
    * <p>
    * It is possible to <em>pre-acknowledge messages on the server</em> so that the client can avoid additional network
    * trip to the server to acknowledge messages. While this increase performance, this does not guarantee delivery (as
    * messages can be lost after being pre-acknowledged on the server). Use with caution if your application design
    * permits it.
    *
    * @param xa              whether the session support XA transaction semantic or not
    * @param autoCommitSends {@code true} to automatically commit message sends, {@code false} to commit manually
    * @param autoCommitAcks  {@code true} to automatically commit message acknowledgement, {@code false} to commit
    *                        manually
    * @param preAcknowledge  {@code true} to pre-acknowledge messages on the server, {@code false} to let the client
    *                        acknowledge the messages
    * @return a ClientSession
    * @throws ActiveMQException if an exception occurs while creating the session
    */
   ClientSession createSession(boolean xa,
                               boolean autoCommitSends,
                               boolean autoCommitAcks,
                               boolean preAcknowledge) throws ActiveMQException;

   /**
    * Creates an <em>authenticated</em> session.
    * <p>
    * It is possible to <em>pre-acknowledge messages on the server</em> so that the client can avoid additional network
    * trip to the server to acknowledge messages. While this increase performance, this does not guarantee delivery (as
    * messages can be lost after being pre-acknowledged on the server). Use with caution if your application design
    * permits it.
    *
    * @param username        the user name
    * @param password        the user password
    * @param xa              whether the session support XA transaction semantic or not
    * @param autoCommitSends {@code true} to automatically commit message sends, {@code false} to commit manually
    * @param autoCommitAcks  {@code true} to automatically commit message acknowledgement, {@code false} to commit
    *                        manually
    * @param preAcknowledge  {@code true} to pre-acknowledge messages on the server, {@code false} to let the client
    *                        acknowledge the messages
    * @return a ClientSession
    * @throws ActiveMQException if an exception occurs while creating the session
    */
   ClientSession createSession(String username,
                               String password,
                               boolean xa,
                               boolean autoCommitSends,
                               boolean autoCommitAcks,
                               boolean preAcknowledge,
                               int ackBatchSize) throws ActiveMQException;

   /**
    * Creates an <em>authenticated</em> session.
    * <p>
    * It is possible to <em>pre-acknowledge messages on the server</em> so that the client can avoid additional network
    * trip to the server to acknowledge messages. While this increase performance, this does not guarantee delivery (as
    * messages can be lost after being pre-acknowledged on the server). Use with caution if your application design
    * permits it.
    *
    * @param username        the user name
    * @param password        the user password
    * @param xa              whether the session support XA transaction semantic or not
    * @param autoCommitSends {@code true} to automatically commit message sends, {@code false} to commit manually
    * @param autoCommitAcks  {@code true} to automatically commit message acknowledgement, {@code false} to commit
    *                        manually
    * @param preAcknowledge  {@code true} to pre-acknowledge messages on the server, {@code false} to let the client
    *                        acknowledge the messages
    * @param clientID        the session clientID
    * @return a ClientSession
    * @throws ActiveMQException if an exception occurs while creating the session
    */
   ClientSession createSession(String username,
                               String password,
                               boolean xa,
                               boolean autoCommitSends,
                               boolean autoCommitAcks,
                               boolean preAcknowledge,
                               int ackBatchSize,
                               String clientID) throws ActiveMQException;

   /**
    * Closes this factory and any session created by it.
    */
   @Override
   void close();

   /**
    * {@return {@code true} if the factory is closed, {@code false} otherwise}
    */
   boolean isClosed();

   /**
    * Adds a FailoverEventListener to the session which is notified if a failover event  occurs on the session.
    *
    * @param listener the listener to add
    * @return this ClientSessionFactory
    */
   ClientSessionFactory addFailoverListener(FailoverEventListener listener);

   /**
    * Removes a FailoverEventListener to the session.
    *
    * @param listener the listener to remove
    * @return {@code true} if the listener was removed, {@code false} else
    */
   boolean removeFailoverListener(FailoverEventListener listener);

   /**
    * Opposed to close, will call cleanup only on every created session and children objects.
    */
   void cleanup();

   /**
    * {@return the server locator associated with this session factory}
    */
   ServerLocator getServerLocator();

   /**
    * {@return the code connection used by this session factory}
    */
   RemotingConnection getConnection();

   /**
    * {@return the configuration used}
    */
   TransportConfiguration getConnectorConfiguration();

}

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

import java.lang.ref.WeakReference;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.jms.ConnectionConsumer;
import javax.jms.ConnectionMetaData;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.IllegalStateException;
import javax.jms.InvalidClientIDException;
import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueSession;
import javax.jms.ServerSessionPool;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicSession;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.FailoverEventListener;
import org.apache.activemq.artemis.api.core.client.FailoverEventType;
import org.apache.activemq.artemis.api.core.client.SessionFailureListener;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSConstants;
import org.apache.activemq.artemis.core.client.impl.ClientSessionInternal;
import org.apache.activemq.artemis.core.version.Version;
import org.apache.activemq.artemis.reader.MessageUtil;
import org.apache.activemq.artemis.utils.ActiveMQThreadFactory;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.apache.activemq.artemis.utils.VersionLoader;
import org.apache.activemq.artemis.utils.collections.ConcurrentHashSet;

/**
 * ActiveMQ Artemis implementation of a JMS Connection.
 * <p>
 * The flat implementation of {@link TopicConnection} and {@link QueueConnection} is per design,
 * following the common usage of these as one flat API in JMS 1.1.
 */
public class ActiveMQConnection extends ActiveMQConnectionForContextImpl implements TopicConnection, QueueConnection {


   public static final int TYPE_GENERIC_CONNECTION = 0;

   public static final int TYPE_QUEUE_CONNECTION = 1;

   public static final int TYPE_TOPIC_CONNECTION = 2;

   public static final String EXCEPTION_FAILOVER = "FAILOVER";

   public static final String EXCEPTION_DISCONNECT = "DISCONNECT";

   public static final SimpleString CONNECTION_ID_PROPERTY_NAME = MessageUtil.CONNECTION_ID_PROPERTY_NAME;


   private final int connectionType;

   private final Set<ActiveMQSession> sessions = new ConcurrentHashSet<>();

   private final Set<SimpleString> tempQueues = new ConcurrentHashSet<>();

   private volatile boolean hasNoLocal;

   private volatile ExceptionListener exceptionListener;

   private volatile FailoverEventListener failoverEventListener;

   private volatile boolean justCreated = true;

   private volatile ConnectionMetaData metaData;

   private volatile boolean closed;

   private volatile boolean started;

   private String clientID;

   private final ClientSessionFactory sessionFactory;

   private final SimpleString uid;

   private final String username;

   private final String password;

   private final SessionFailureListener listener = new JMSFailureListener(this);

   private final FailoverEventListener failoverListener = new FailoverEventListenerImpl(this);

   private final ExecutorService failoverListenerExecutor = Executors.newFixedThreadPool(1, ActiveMQThreadFactory.defaultThreadFactory());

   private final Version thisVersion;

   private final int dupsOKBatchSize;

   private final int transactionBatchSize;

   private final boolean cacheDestinations;

   private final boolean enable1xPrefixes;

   private ClientSession initialSession;

   private final Exception creationStack;

   private ActiveMQConnectionFactory factoryReference;

   private final ConnectionFactoryOptions options;


   public ActiveMQConnection(final ConnectionFactoryOptions options,
                             final String username,
                             final String password,
                             final int connectionType,
                             final String clientID,
                             final int dupsOKBatchSize,
                             final int transactionBatchSize,
                             final boolean cacheDestinations,
                             final boolean enable1xPrefixes,
                             final ClientSessionFactory sessionFactory) {
      this.options = options;

      this.username = username;

      this.password = password;

      this.connectionType = connectionType;

      this.clientID = clientID;

      this.sessionFactory = sessionFactory;

      uid = UUIDGenerator.getInstance().generateSimpleStringUUID();

      thisVersion = VersionLoader.getVersion();

      this.dupsOKBatchSize = dupsOKBatchSize;

      this.transactionBatchSize = transactionBatchSize;

      this.cacheDestinations = cacheDestinations;

      this.enable1xPrefixes = enable1xPrefixes;

      creationStack = new Exception();
   }

   /**
    * This internal method serves basically the Resource Adapter.
    * The resource adapter plays with an XASession and a non XASession.
    * When there is no enlisted transaction, the EE specification mandates that the commit should
    * be done as if it was a nonXA Session (i.e. SessionTransacted).
    * For that reason we have this method to force that nonXASession, since the JMS Javadoc
    * mandates createSession to return a XASession.
    */
   public synchronized Session createNonXASession(final boolean transacted, final int acknowledgeMode) throws JMSException {
      checkClosed();

      return createSessionInternal(false, transacted, acknowledgeMode, ActiveMQConnection.TYPE_GENERIC_CONNECTION);
   }

   /**
    * This internal method serves basically the Resource Adapter.
    * The resource adapter plays with an XASession and a non XASession.
    * When there is no enlisted transaction, the EE specification mandates that the commit should
    * be done as if it was a nonXA Session (i.e. SessionTransacted).
    * For that reason we have this method to force that nonXASession, since the JMS Javadoc
    * mandates createSession to return a XASession.
    */
   public synchronized Session createNonXATopicSession(final boolean transacted, final int acknowledgeMode) throws JMSException {
      checkClosed();

      return createSessionInternal(false, transacted, acknowledgeMode, ActiveMQConnection.TYPE_TOPIC_CONNECTION);
   }

   /**
    * This internal method serves basically the Resource Adapter.
    * The resource adapter plays with an XASession and a non XASession.
    * When there is no enlisted transaction, the EE specification mandates that the commit should
    * be done as if it was a nonXA Session (i.e. SessionTransacted).
    * For that reason we have this method to force that nonXASession, since the JMS Javadoc
    * mandates createSession to return a XASession.
    */
   public synchronized Session createNonXAQueueSession(final boolean transacted, final int acknowledgeMode) throws JMSException {
      checkClosed();

      return createSessionInternal(false, transacted, acknowledgeMode, ActiveMQConnection.TYPE_QUEUE_CONNECTION);
   }

   // Connection implementation --------------------------------------------------------------------

   @Override
   public synchronized Session createSession(final boolean transacted, final int acknowledgeMode) throws JMSException {
      checkClosed();

      return createSessionInternal(false, transacted, checkAck(transacted, acknowledgeMode), ActiveMQConnection.TYPE_GENERIC_CONNECTION);
   }

   @Override
   public String getClientID() throws JMSException {
      checkClosed();

      return clientID;
   }

   @Override
   public void setClientID(final String clientID) throws JMSException {
      checkClosed();

      if (this.clientID != null) {
         throw new IllegalStateException("Client id has already been set");
      }

      if (!justCreated) {
         throw new IllegalStateException("setClientID can only be called directly after the connection is created");
      }

      try {
         validateClientID(initialSession, clientID);
         this.clientID = clientID;
         this.addSessionMetaData(initialSession);
      } catch (ActiveMQException e) {
         JMSException ex = new JMSException("Internal error setting metadata jms-client-id");
         ex.setLinkedException(e);
         ex.initCause(e);
         throw ex;
      }

      justCreated = false;
   }

   private void validateClientID(ClientSession validateSession, String clientID)
         throws InvalidClientIDException, ActiveMQException {
      try {
         validateSession.addUniqueMetaData(ClientSession.JMS_SESSION_CLIENT_ID_PROPERTY, clientID);
      } catch (ActiveMQException e) {
         if (e.getType() == ActiveMQExceptionType.DUPLICATE_METADATA) {
            throw new InvalidClientIDException("clientID=" + clientID + " was already set into another connection");
         } else {
            throw e;
         }
      }
   }

   @Override
   public ConnectionMetaData getMetaData() throws JMSException {
      checkClosed();

      if (metaData == null) {
         metaData = new ActiveMQConnectionMetaData(thisVersion);
      }

      return metaData;
   }

   @Override
   public ExceptionListener getExceptionListener() throws JMSException {
      checkClosed();

      return exceptionListener;
   }

   @Override
   public void setExceptionListener(final ExceptionListener listener) throws JMSException {
      checkClosed();

      exceptionListener = listener;
   }

   @Override
   public synchronized void start() throws JMSException {
      checkClosed();

      for (ActiveMQSession session : sessions) {
         session.start();
      }

      justCreated = false;
      started = true;
   }

   public synchronized void signalStopToAllSessions() {
      for (ActiveMQSession session : sessions) {
         ClientSession coreSession = session.getCoreSession();
         if (coreSession instanceof ClientSessionInternal) {
            ClientSessionInternal internalSession = (ClientSessionInternal) coreSession;
            internalSession.setStopSignal();
         }
      }

   }

   @Override
   public synchronized void stop() throws JMSException {
      threadAwareContext.assertNotMessageListenerThread();

      checkClosed();

      for (ActiveMQSession session : sessions) {
         session.stop();
      }

      started = false;
   }

   @Override
   public final synchronized void close() throws JMSException {
      threadAwareContext.assertNotCompletionListenerThread();
      threadAwareContext.assertNotMessageListenerThread();

      if (closed) {
         return;
      }

      sessionFactory.close();

      try {
         for (ActiveMQSession session : new HashSet<>(sessions)) {
            session.close();
         }

         try {
            if (!tempQueues.isEmpty()) {
               // Remove any temporary queues

               for (SimpleString queueName : tempQueues) {
                  if (!initialSession.isClosed()) {
                     try {
                        initialSession.deleteQueue(queueName);
                     } catch (ActiveMQException ignore) {
                        // Exception on deleting queue shouldn't prevent close from completing
                     }
                  }
               }
            }
         } finally {
            if (initialSession != null) {
               initialSession.close();
            }
         }

         AccessController.doPrivileged(new PrivilegedAction() {
            @Override
            public Object run() {
               failoverListenerExecutor.shutdown();
               return null;
            }
         });

         closed = true;
      } catch (ActiveMQException e) {
         throw JMSExceptionHelper.convertFromActiveMQException(e);
      }
   }

   @Override
   public ConnectionConsumer createConnectionConsumer(final Destination destination,
                                                      final String messageSelector,
                                                      final ServerSessionPool sessionPool,
                                                      final int maxMessages) throws JMSException {
      checkClosed();

      checkTempQueues(destination);

      // We offer a RA, so no need to implement this for MDBs
      return null;
   }

   private void checkTempQueues(Destination destination) throws JMSException {
      ActiveMQDestination jbdest = (ActiveMQDestination) destination;

      if (jbdest.isTemporary() && !containsTemporaryQueue(jbdest.getSimpleAddress())) {
         throw new JMSException("Can not create consumer for temporary destination " + destination +
                                   " from another JMS connection");
      }
   }

   @Override
   public ConnectionConsumer createDurableConnectionConsumer(final Topic topic,
                                                             final String subscriptionName,
                                                             final String messageSelector,
                                                             final ServerSessionPool sessionPool,
                                                             final int maxMessages) throws JMSException {
      checkClosed();
      // As spec. section 4.11
      if (connectionType == ActiveMQConnection.TYPE_QUEUE_CONNECTION) {
         String msg = "Cannot create a durable connection consumer on a QueueConnection";
         throw new javax.jms.IllegalStateException(msg);
      }
      checkTempQueues(topic);
      // We offer RA, so no need for this
      return null;
   }

   @Override
   public synchronized Session createSession(int sessionMode) throws JMSException {
      checkClosed();
      return createSessionInternal(false, sessionMode == Session.SESSION_TRANSACTED, sessionMode, ActiveMQSession.TYPE_GENERIC_SESSION);

   }

   @Override
   public synchronized Session createSession() throws JMSException {
      checkClosed();
      return createSessionInternal(false, false, Session.AUTO_ACKNOWLEDGE, ActiveMQSession.TYPE_GENERIC_SESSION);
   }

   // QueueConnection implementation ---------------------------------------------------------------

   @Override
   public synchronized QueueSession createQueueSession(final boolean transacted, int acknowledgeMode) throws JMSException {
      checkClosed();
      return createSessionInternal(false, transacted, checkAck(transacted, acknowledgeMode), ActiveMQSession.TYPE_QUEUE_SESSION);
   }

   /**
    * I'm keeping this as static as the same check will be done within RA.
    * This is to conform with TCK Tests where we must return ackMode exactly as they want if transacted=false
    */
   public static int checkAck(boolean transacted, int acknowledgeMode) {
      if (!transacted && acknowledgeMode == Session.SESSION_TRANSACTED) {
         return Session.AUTO_ACKNOWLEDGE;
      }

      return acknowledgeMode;
   }

   @Override
   public ConnectionConsumer createConnectionConsumer(final Queue queue,
                                                      final String messageSelector,
                                                      final ServerSessionPool sessionPool,
                                                      final int maxMessages) throws JMSException {
      checkClosed();
      checkTempQueues(queue);
      return null;
   }

   // TopicConnection implementation ---------------------------------------------------------------

   @Override
   public synchronized TopicSession createTopicSession(final boolean transacted, final int acknowledgeMode) throws JMSException {
      checkClosed();
      return createSessionInternal(false, transacted, checkAck(transacted, acknowledgeMode), ActiveMQSession.TYPE_TOPIC_SESSION);
   }

   @Override
   public ConnectionConsumer createConnectionConsumer(final Topic topic,
                                                      final String messageSelector,
                                                      final ServerSessionPool sessionPool,
                                                      final int maxMessages) throws JMSException {
      checkClosed();
      checkTempQueues(topic);
      return null;
   }

   @Override
   public ConnectionConsumer createSharedConnectionConsumer(Topic topic,
                                                            String subscriptionName,
                                                            String messageSelector,
                                                            ServerSessionPool sessionPool,
                                                            int maxMessages) throws JMSException {
      return null; // we offer RA
   }

   @Override
   public ConnectionConsumer createSharedDurableConnectionConsumer(Topic topic,
                                                                   String subscriptionName,
                                                                   String messageSelector,
                                                                   ServerSessionPool sessionPool,
                                                                   int maxMessages) throws JMSException {
      return null; // we offer RA
   }

   /**
    * Sets a FailureListener for the session which is notified if a failure occurs on the session.
    *
    * @param listener the listener to add
    * @throws JMSException
    */
   public void setFailoverListener(final FailoverEventListener listener) throws JMSException {
      checkClosed();

      justCreated = false;

      this.failoverEventListener = listener;

   }

   /**
    * @return {@link FailoverEventListener} the current failover event listener for this connection
    * @throws JMSException
    */
   public FailoverEventListener getFailoverListener() throws JMSException {
      checkClosed();

      justCreated = false;

      return failoverEventListener;
   }

   public void addTemporaryQueue(final SimpleString queueAddress) {
      tempQueues.add(queueAddress);
   }

   public void removeTemporaryQueue(final SimpleString queueAddress) {
      tempQueues.remove(queueAddress);
   }

   public boolean containsTemporaryQueue(final SimpleString queueAddress) {
      return tempQueues.contains(queueAddress);
   }

   public boolean hasNoLocal() {
      return hasNoLocal;
   }

   public void setHasNoLocal() {
      hasNoLocal = true;
   }

   public SimpleString getUID() {
      return uid;
   }

   public void removeSession(final ActiveMQSession session) {
      sessions.remove(session);
   }

   public ClientSession getInitialSession() {
      return initialSession;
   }

   @Override
   protected final void finalize() throws Throwable {
      if (!closed) {
         if (this.factoryReference.isFinalizeChecks()) {
            ActiveMQJMSClientLogger.LOGGER.connectionLeftOpen(creationStack);
         }

         close();
      }
   }

   protected boolean isXA() {
      return false;
   }

   protected final ActiveMQSession createSessionInternal(final boolean isXA,
                                                         final boolean transacted,
                                                         int acknowledgeMode,
                                                         final int type) throws JMSException {
      if (transacted) {
         acknowledgeMode = Session.SESSION_TRANSACTED;
      }

      try {
         ClientSession session;
         boolean isBlockOnAcknowledge = sessionFactory.getServerLocator().isBlockOnAcknowledge();
         int ackBatchSize = sessionFactory.getServerLocator().getAckBatchSize();
         if (acknowledgeMode == Session.SESSION_TRANSACTED) {
            session = sessionFactory.createSession(username, password, isXA, false, false, sessionFactory.getServerLocator().isPreAcknowledge(), transactionBatchSize, clientID);
         } else if (acknowledgeMode == Session.AUTO_ACKNOWLEDGE) {
            session = sessionFactory.createSession(username, password, isXA, true, true, sessionFactory.getServerLocator().isPreAcknowledge(), 0, clientID);
         } else if (acknowledgeMode == Session.DUPS_OK_ACKNOWLEDGE) {
            session = sessionFactory.createSession(username, password, isXA, true, true, sessionFactory.getServerLocator().isPreAcknowledge(), dupsOKBatchSize, clientID);
         } else if (acknowledgeMode == Session.CLIENT_ACKNOWLEDGE) {
            session = sessionFactory.createSession(username, password, isXA, true, false, sessionFactory.getServerLocator().isPreAcknowledge(), isBlockOnAcknowledge ? transactionBatchSize : ackBatchSize, clientID);
         } else if (acknowledgeMode == ActiveMQJMSConstants.INDIVIDUAL_ACKNOWLEDGE) {
            session = sessionFactory.createSession(username, password, isXA, true, false, false, isBlockOnAcknowledge ? transactionBatchSize : ackBatchSize, clientID);
         } else if (acknowledgeMode == ActiveMQJMSConstants.PRE_ACKNOWLEDGE) {
            session = sessionFactory.createSession(username, password, isXA, true, false, true, transactionBatchSize, clientID);
         } else {
            throw new JMSRuntimeException("Invalid ackmode: " + acknowledgeMode);
         }

         justCreated = false;

         // Setting multiple times on different sessions doesn't matter since RemotingConnection
         // maintains
         // a set (no duplicates)
         session.addFailureListener(listener);
         session.addFailoverListener(failoverListener);

         ActiveMQSession jbs = createAMQSession(isXA, transacted, acknowledgeMode, session, type);

         sessions.add(jbs);

         if (started) {
            session.start();
         }

         return jbs;
      } catch (ActiveMQException e) {
         throw JMSExceptionHelper.convertFromActiveMQException(e);
      }
   }

   public ClientSessionFactory getSessionFactory() {
      return sessionFactory;
   }


   /**
    * @param transacted
    * @param acknowledgeMode
    * @param session
    * @param type
    * @return
    */
   protected ActiveMQSession createAMQSession(boolean isXA,
                                              boolean transacted,
                                              int acknowledgeMode,
                                              ClientSession session,
                                              int type) {
      if (isXA) {
         return new ActiveMQXASession(options, this, transacted, true, acknowledgeMode, cacheDestinations, enable1xPrefixes, session, type);
      } else {
         return new ActiveMQSession(options, this, transacted, false, acknowledgeMode, cacheDestinations, enable1xPrefixes, session, type);
      }
   }

   protected final void checkClosed() throws JMSException {
      if (closed) {
         throw new IllegalStateException("Connection is closed");
      }
   }

   public void authorize() throws JMSException {
      authorize(true);
   }

   public void authorize(boolean validateClientId) throws JMSException {
      try {
         initialSession = sessionFactory.createSession(username, password, false, false, false, false, 0, clientID);

         if (clientID != null) {
            if (validateClientId) {
               validateClientID(initialSession, clientID);
            } else {
               initialSession.addMetaData(ClientSession.JMS_SESSION_CLIENT_ID_PROPERTY, clientID);
            }
         }

         addSessionMetaData(initialSession);

         initialSession.addFailureListener(listener);
         initialSession.addFailoverListener(failoverListener);
      } catch (ActiveMQException me) {
         throw JMSExceptionHelper.convertFromActiveMQException(me);
      }
   }

   private void addSessionMetaData(ClientSession session) throws ActiveMQException {
      session.addMetaData(ClientSession.JMS_SESSION_IDENTIFIER_PROPERTY, "");
      if (clientID != null) {
         session.addMetaData(ClientSession.JMS_SESSION_CLIENT_ID_PROPERTY, clientID);
      }
   }

   public void setReference(ActiveMQConnectionFactory factory) {
      this.factoryReference = factory;
   }

   public boolean isStarted() {
      return started;
   }

   public String getDeserializationBlackList() {
      return this.factoryReference.getDeserializationBlackList();
   }

   public String getDeserializationWhiteList() {
      return this.factoryReference.getDeserializationWhiteList();
   }


   private static class JMSFailureListener implements SessionFailureListener {

      private final WeakReference<ActiveMQConnection> connectionRef;

      JMSFailureListener(final ActiveMQConnection connection) {
         connectionRef = new WeakReference<>(connection);
      }

      @Override
      public synchronized void connectionFailed(final ActiveMQException me, boolean failedOver) {
         if (me == null) {
            return;
         }

         ActiveMQConnection conn = connectionRef.get();

         if (conn != null) {
            try {
               final ExceptionListener exceptionListener = conn.getExceptionListener();

               if (exceptionListener != null) {
                  final JMSException je = new JMSException(me.toString(), failedOver ? EXCEPTION_FAILOVER : EXCEPTION_DISCONNECT);

                  je.initCause(me);

                  new Thread(new Runnable() {
                     @Override
                     public void run() {
                        exceptionListener.onException(je);
                     }
                  }).start();
               }
            } catch (JMSException e) {
               if (!conn.closed) {
                  ActiveMQJMSClientLogger.LOGGER.errorCallingExcListener(e);
               }
            }
         }
      }

      @Override
      public void connectionFailed(final ActiveMQException me, boolean failedOver, String scaleDownTargetNodeID) {
         connectionFailed(me, failedOver);
      }

      @Override
      public void beforeReconnect(final ActiveMQException me) {

      }

   }

   private static class FailoverEventListenerImpl implements FailoverEventListener {

      private final WeakReference<ActiveMQConnection> connectionRef;

      FailoverEventListenerImpl(final ActiveMQConnection connection) {
         connectionRef = new WeakReference<>(connection);
      }

      @Override
      public void failoverEvent(final FailoverEventType eventType) {
         ActiveMQConnection conn = connectionRef.get();

         if (conn != null) {
            try {
               final FailoverEventListener failoverListener = conn.getFailoverListener();

               if (failoverListener != null) {

                  conn.failoverListenerExecutor.execute(new Runnable() {
                     @Override
                     public void run() {
                        failoverListener.failoverEvent(eventType);
                     }
                  });
               }
            } catch (JMSException e) {
               if (!conn.closed) {
                  ActiveMQJMSClientLogger.LOGGER.errorCallingFailoverListener(e);
               }
            }
         }

      }
   }
}

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
package org.apache.activemq.artemis.ra;

import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.ResourceAllocationException;
import javax.jms.Session;
import javax.jms.XASession;
import javax.resource.ResourceException;
import javax.resource.spi.ConnectionEvent;
import javax.resource.spi.ConnectionEventListener;
import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.IllegalStateException;
import javax.resource.spi.LocalTransaction;
import javax.resource.spi.ManagedConnection;
import javax.resource.spi.ManagedConnectionMetaData;
import javax.resource.spi.SecurityException;
import javax.security.auth.Subject;
import javax.transaction.Status;
import javax.transaction.TransactionSynchronizationRegistry;
import javax.transaction.xa.XAResource;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.activemq.artemis.core.client.impl.ClientSessionInternal;
import org.apache.activemq.artemis.jms.client.ActiveMQConnection;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQXAConnection;
import org.apache.activemq.artemis.service.extensions.ServiceUtils;
import org.apache.activemq.artemis.service.extensions.xa.ActiveMQXAResourceWrapper;
import org.apache.activemq.artemis.utils.VersionLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ActiveMQRAManagedConnection implements ManagedConnection, ExceptionListener {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final ActiveMQRAManagedConnectionFactory mcf;

   private final ActiveMQRAConnectionRequestInfo cri;

   private final ActiveMQResourceAdapter ra;

   private final String userName;

   private final String password;

   private final AtomicBoolean isDestroyed = new AtomicBoolean(false);

   private final List<ConnectionEventListener> eventListeners = Collections.synchronizedList(new ArrayList<>());

   private final Set<ActiveMQRASession> handles = ConcurrentHashMap.newKeySet();

   private ReentrantLock lock = new ReentrantLock();

   // Physical connection stuff
   private ActiveMQConnectionFactory connectionFactory;

   private ActiveMQXAConnection connection;

   // The ManagedConnection will play with a XA and a NonXASession to couple with
   // cases where a commit is called on a non-XAed (or non-enlisted) case.
   private Session nonXAsession;

   private XASession xaSession;

   private XAResource xaResource;

   private final TransactionSynchronizationRegistry tsr;

   private boolean inManagedTx;

   public ActiveMQRAManagedConnection(final ActiveMQRAManagedConnectionFactory mcf,
                                      final ActiveMQRAConnectionRequestInfo cri,
                                      final ActiveMQResourceAdapter ra,
                                      final String userName,
                                      final String password) throws ResourceException {
      if (logger.isTraceEnabled()) {
         logger.trace("constructor({}, {}, {}, ****))", mcf, cri, userName);
      }

      this.mcf = mcf;
      this.cri = cri;
      this.tsr = ra.getTSR();
      this.ra = ra;
      this.userName = userName;
      this.password = password;

      connection = null;
      nonXAsession = null;
      xaSession = null;
      xaResource = null;

      try {
         setup();
      } catch (ResourceException e) {
         try {
            destroy();
         } catch (Throwable ignored) {
         }

         throw e;
      } catch (Throwable t) {
         try {
            destroy();
         } catch (Throwable ignored) {
         }
         throw new ResourceException("Error during setup", t);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public synchronized Object getConnection(final Subject subject,
                                            final ConnectionRequestInfo cxRequestInfo) throws ResourceException {
      logger.trace("getConnection({}, {})", subject, cxRequestInfo);

      // Check user first
      ActiveMQRACredential credential = ActiveMQRACredential.getCredential(mcf, subject, cxRequestInfo);

      // Null users are allowed!
      if (userName != null && !userName.equals(credential.getUserName())) {
         throw new SecurityException("Password credentials not the same, reauthentication not allowed");
      }

      if (userName == null && credential.getUserName() != null) {
         throw new SecurityException("Password credentials not the same, reauthentication not allowed");
      }

      if (isDestroyed.get()) {
         throw new IllegalStateException("The managed connection is already destroyed");
      }

      ActiveMQRASession session = new ActiveMQRASession(this, (ActiveMQRAConnectionRequestInfo) cxRequestInfo);
      handles.add(session);
      return session;
   }

   private void destroyHandles() throws ResourceException {
      logger.trace("destroyHandles()");

      for (ActiveMQRASession session : handles) {
         session.destroy();
      }

      handles.clear();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void destroy() throws ResourceException {
      logger.trace("destroy()");

      if (isDestroyed.get() || connection == null) {
         return;
      }

      isDestroyed.set(true);

      try {
         connection.setExceptionListener(null);
      } catch (JMSException e) {
         logger.debug("Error unsetting the exception listener {}", this, e);
      }

      connection.signalStopToAllSessions();

      try {
         // we must close the ActiveMQConnectionFactory because it contains a ServerLocator
         if (connectionFactory != null) {
            ra.closeConnectionFactory(mcf.getProperties());
         }
      } catch (Exception e) {
         logger.debug(e.getMessage(), e);
      }

      destroyHandles();

      try {
         // The following calls should not be necessary, as the connection should close the
         // ClientSessionFactory, which will close the sessions.
         try {
            /*
             * (xa|nonXA)Session.close() may NOT be called BEFORE connection.close()
             * If the ClientSessionFactory is trying to fail-over or reconnect with -1 attempts, and
             * one calls session.close() it may effectively dead-lock.
             * connection close will close the ClientSessionFactory which will close all sessions.
             */
            connection.close();

            if (nonXAsession != null) {
               nonXAsession.close();
            }

            if (xaSession != null) {
               xaSession.close();
            }
         } catch (JMSException e) {
            logger.debug("Error closing session {}", this, e);
         }

      } catch (Throwable e) {
         throw new ResourceException("Could not properly close the session and connection", e);
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void cleanup() throws ResourceException {
      logger.trace("cleanup()");

      if (isDestroyed.get()) {
         throw new IllegalStateException("ManagedConnection already destroyed");
      }

      destroyHandles();

      inManagedTx = false;

      inManagedTx = false;

      // I'm recreating the lock object when we return to the pool
      // because it looks too nasty to expect the connection handle
      // to unlock properly in certain race conditions
      // where the dissociation of the managed connection is "random".
      lock = new ReentrantLock();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void associateConnection(final Object obj) throws ResourceException {
      logger.trace("associateConnection({})", obj);

      if (!isDestroyed.get() && obj instanceof ActiveMQRASession activeMQRASession) {
         activeMQRASession.setManagedConnection(this);
         handles.add(activeMQRASession);
      } else {
         throw new IllegalStateException("ManagedConnection in an illegal state");
      }
   }

   public void checkTransactionActive() throws JMSException {
      // don't bother looking at the transaction if there's an active XID
      if (!inManagedTx && tsr != null) {
         int status = tsr.getTransactionStatus();
         // Only allow states that will actually succeed
         if (status == Status.STATUS_COMMITTED || status == Status.STATUS_MARKED_ROLLBACK || status == Status.STATUS_ROLLEDBACK || status == Status.STATUS_ROLLING_BACK) {
            String statusMessage = "";
            if (status == Status.STATUS_COMMITTED) {
               statusMessage = "committed";
            } else if (status == Status.STATUS_MARKED_ROLLBACK) {
               statusMessage = "marked for rollback";
            } else if (status == Status.STATUS_ROLLEDBACK) {
               statusMessage = "rolled back";
            } else if (status == Status.STATUS_ROLLING_BACK) {
               statusMessage = "rolling back";
            }
            throw new javax.jms.IllegalStateException("Transaction is " + statusMessage);
         }
      }
   }

   /**
    * Aqquire a lock on the managed connection
    */
   protected void lock() {
      logger.trace("lock()");

      lock.lock();
   }

   /**
    * Aqquire a lock on the managed connection within the specified period
    *
    * @throws JMSException Thrown if an error occurs
    */
   protected void tryLock() throws JMSException {
      logger.trace("tryLock()");

      Integer tryLock = mcf.getUseTryLock();
      if (tryLock == null || tryLock.intValue() <= 0) {
         lock();
         return;
      }
      try {
         if (lock.tryLock(tryLock.intValue(), TimeUnit.SECONDS) == false) {
            throw new ResourceAllocationException("Unable to obtain lock in " + tryLock + " seconds: " + this);
         }
      } catch (InterruptedException e) {
         throw new ResourceAllocationException("Interrupted attempting lock: " + this);
      }
   }

   /**
    * Unlock the managed connection
    */
   protected void unlock() {
      logger.trace("unlock()");

      lock.unlock();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void addConnectionEventListener(final ConnectionEventListener l) {
      logger.trace("addConnectionEventListener({})", l);

      eventListeners.add(l);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void removeConnectionEventListener(final ConnectionEventListener l) {
      logger.trace("removeConnectionEventListener({})", l);

      eventListeners.remove(l);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public XAResource getXAResource() throws ResourceException {
      logger.trace("getXAResource()");

      //
      // Spec says a mc must always return the same XA resource,
      // so we cache it.
      //
      if (xaResource == null) {
         ClientSessionInternal csi = (ClientSessionInternal) xaSession.getXAResource();
         ActiveMQRAXAResource activeMQRAXAResource = new ActiveMQRAXAResource(this, xaSession.getXAResource());
         Map<String, Object> xaResourceProperties = new HashMap<>();
         xaResourceProperties.put(ActiveMQXAResourceWrapper.ACTIVEMQ_JNDI_NAME, ra.getJndiName());
         xaResourceProperties.put(ActiveMQXAResourceWrapper.ACTIVEMQ_NODE_ID, csi.getNodeId());
         xaResourceProperties.put(ActiveMQXAResourceWrapper.ACTIVEMQ_PRODUCT_NAME, ActiveMQResourceAdapter.PRODUCT_NAME);
         xaResourceProperties.put(ActiveMQXAResourceWrapper.ACTIVEMQ_PRODUCT_VERSION, VersionLoader.getVersion().getFullVersion());
         xaResource = ServiceUtils.wrapXAResource(activeMQRAXAResource, xaResourceProperties);
      }

      logger.trace("XAResource={}", xaResource);

      return xaResource;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public LocalTransaction getLocalTransaction() throws ResourceException {
      logger.trace("getLocalTransaction()");

      LocalTransaction tx = new ActiveMQRALocalTransaction(this);

      logger.trace("LocalTransaction={}", tx);

      return tx;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public ManagedConnectionMetaData getMetaData() throws ResourceException {
      logger.trace("getMetaData()");

      if (isDestroyed.get()) {
         throw new IllegalStateException("The managed connection is already destroyed");
      }

      return new ActiveMQRAMetaData(this);
   }

   /**
    * <b>NOT SUPPORTED</b>
    * <p>
    * {@inheritDoc}
    */
   @Override
   public void setLogWriter(final PrintWriter out) throws ResourceException {
      logger.trace("setLogWriter({})", out);
   }

   /**
    * <b>NOT SUPPORTED</b>
    * <p>
    * {@inheritDoc}
    */
   @Override
   public PrintWriter getLogWriter() throws ResourceException {
      logger.trace("getLogWriter()");

      return null;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void onException(final JMSException exception) {
      if (ActiveMQConnection.EXCEPTION_FAILOVER.equals(exception.getErrorCode())) {
         return;
      }
      if (logger.isTraceEnabled()) {
         logger.trace("onException()", exception);
      }

      if (isDestroyed.get()) {
         if (logger.isTraceEnabled()) {
            logger.trace("Ignoring error on already destroyed connection {}", this, exception);
         }
         return;
      }

      ActiveMQRALogger.LOGGER.handlingJMSFailure(exception);

      try {
         connection.setExceptionListener(null);
      } catch (JMSException e) {
         logger.debug("Unable to unset exception listener", e);
      }

      ConnectionEvent event = new ConnectionEvent(this, ConnectionEvent.CONNECTION_ERROR_OCCURRED, exception);
      sendEvent(event);
   }

   protected Session getSession() throws JMSException {
      if (xaResource != null && inManagedTx) {
         logger.trace("getSession() -> XA session {}", xaSession.getSession());

         return xaSession.getSession();
      } else {
         logger.trace("getSession() -> non XA session {}", nonXAsession);

         return nonXAsession;
      }
   }

   protected void sendEvent(final ConnectionEvent event) {
      logger.trace("sendEvent({})", event);

      int type = event.getId();

      // convert to an array to avoid concurrent modification exceptions
      ConnectionEventListener[] list = eventListeners.toArray(new ConnectionEventListener[eventListeners.size()]);

      for (ConnectionEventListener l : list) {
         switch (type) {
            case ConnectionEvent.CONNECTION_CLOSED:
               l.connectionClosed(event);
               break;

            case ConnectionEvent.LOCAL_TRANSACTION_STARTED:
               l.localTransactionStarted(event);
               break;

            case ConnectionEvent.LOCAL_TRANSACTION_COMMITTED:
               l.localTransactionCommitted(event);
               break;

            case ConnectionEvent.LOCAL_TRANSACTION_ROLLEDBACK:
               l.localTransactionRolledback(event);
               break;

            case ConnectionEvent.CONNECTION_ERROR_OCCURRED:
               l.connectionErrorOccurred(event);
               break;

            default:
               throw new IllegalArgumentException("Illegal eventType: " + type);
         }
      }
   }

   protected void removeHandle(final ActiveMQRASession handle) {
      logger.trace("removeHandle({})", handle);

      handles.remove(handle);
   }

   /**
    * Get the request info for this connection.
    *
    * @return The connection request info for this connection
    */
   protected ActiveMQRAConnectionRequestInfo getCRI() {
      logger.trace("getCRI()");

      return cri;
   }

   protected ActiveMQRAManagedConnectionFactory getManagedConnectionFactory() {
      logger.trace("getManagedConnectionFactory()");

      return mcf;
   }

   void start() throws JMSException {
      logger.trace("start()");

      if (connection != null) {
         connection.start();
      }
   }

   void stop() throws JMSException {
      logger.trace("stop()");

      if (connection != null) {
         connection.stop();
      }
   }

   protected String getUserName() {
      logger.trace("getUserName()");

      return userName;
   }

   private void setup() throws ResourceException {
      logger.trace("setup()");

      try {

         createCF();

         boolean transacted = cri.isTransacted();
         int acknowledgeMode = Session.AUTO_ACKNOWLEDGE;
         if (cri.getType() == ActiveMQRAConnectionFactory.TOPIC_CONNECTION) {
            if (userName != null && password != null) {
               connection = (ActiveMQXAConnection) connectionFactory.createXATopicConnection(userName, password);
            } else {
               connection = (ActiveMQXAConnection) connectionFactory.createXATopicConnection();
            }

            connection.setExceptionListener(this);

            xaSession = connection.createXATopicSession();
            nonXAsession = connection.createNonXATopicSession(transacted, acknowledgeMode);

         } else if (cri.getType() == ActiveMQRAConnectionFactory.QUEUE_CONNECTION) {
            if (userName != null && password != null) {
               connection = (ActiveMQXAConnection) connectionFactory.createXAQueueConnection(userName, password);
            } else {
               connection = (ActiveMQXAConnection) connectionFactory.createXAQueueConnection();
            }

            connection.setExceptionListener(this);

            xaSession = connection.createXAQueueSession();
            nonXAsession = connection.createNonXAQueueSession(transacted, acknowledgeMode);

         } else {
            if (userName != null && password != null) {
               connection = (ActiveMQXAConnection) connectionFactory.createXAConnection(userName, password);
            } else {
               connection = (ActiveMQXAConnection) connectionFactory.createXAConnection();
            }

            connection.setExceptionListener(this);

            xaSession = connection.createXASession();
            nonXAsession = connection.createNonXASession(transacted, acknowledgeMode);
         }

      } catch (JMSException je) {
         throw new ResourceException(je.getMessage(), je);
      }
   }

   private void createCF() {
      if (connectionFactory == null) {
         connectionFactory = ra.getConnectionFactory(mcf.getProperties());
      }
   }

   protected void setInManagedTx(boolean inManagedTx) {
      this.inManagedTx = inManagedTx;
   }

   public ActiveMQConnectionFactory getConnectionFactory() {
      return connectionFactory;
   }
}

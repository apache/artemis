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
package org.apache.activemq.artemis.service.extensions.xa.recovery;

import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.util.Arrays;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.ActiveMQNotConnectedException;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.client.SessionFailureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * XAResourceWrapper.
 * <p>
 * Mainly from org.jboss.server.XAResourceWrapper from the JBoss AS server module
 * <p>
 * The reason why we don't use that class directly is that it assumes on failure of connection the RM_FAIL or RM_ERR is
 * thrown, but in Apache Artemis we throw XA_RETRY since we want the recovery manager to be able to retry on failure
 * without having to manually retry
 */
public class ActiveMQXAResourceWrapper implements XAResource, SessionFailureListener {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final Object lock = new Object();

   private ServerLocator serverLocator;

   private ClientSessionFactory csf;

   private ClientSession delegate;

   private XARecoveryConfig[] xaRecoveryConfigs;

   public ActiveMQXAResourceWrapper(XARecoveryConfig... xaRecoveryConfigs) {
      this.xaRecoveryConfigs = xaRecoveryConfigs;

      if (logger.isDebugEnabled()) {
         logger.debug("Recovery configured with {}, instance={}", Arrays.toString(xaRecoveryConfigs), System.identityHashCode(this));
      }
   }

   public void updateRecoveryConfig(XARecoveryConfig... xaRecoveryConfigs) {
      synchronized (ActiveMQXAResourceWrapper.lock) {
         close();
         this.xaRecoveryConfigs = xaRecoveryConfigs;
      }
   }

   @Override
   public Xid[] recover(final int flag) throws XAException {
      XAResource xaResource = getDelegate(false);

      if (logger.isDebugEnabled()) {
         logger.debug("looking for recover at {} configuration {}", xaResource, Arrays.toString(this.xaRecoveryConfigs));
      }

      try {
         Xid[] xids = xaResource.recover(flag);

         if (logger.isDebugEnabled() && xids != null && xids.length > 0) {
            logger.debug("Recovering these following IDs {} at {}", Arrays.toString(xids), this);
         }

         return xids;
      } catch (XAException e) {
         ActiveMQXARecoveryLogger.LOGGER.xaRecoverError(e);
         throw check(e);
      }
   }

   @Override
   public void commit(final Xid xid, final boolean onePhase) throws XAException {
      XAResource xaResource = getDelegate(true);
      logger.debug("Commit {} xid onePhase={}", xaResource, onePhase);
      try {
         xaResource.commit(xid, onePhase);
      } catch (XAException e) {
         throw check(e);
      }
   }

   @Override
   public void rollback(final Xid xid) throws XAException {
      XAResource xaResource = getDelegate(true);
      logger.debug("Rollback {} xid", xaResource);
      try {
         xaResource.rollback(xid);
      } catch (XAException e) {
         throw check(e);
      }
   }

   @Override
   public void forget(final Xid xid) throws XAException {
      XAResource xaResource = getDelegate(false);
      logger.debug("Forget {} xid ", xaResource);
      try {
         xaResource.forget(xid);
      } catch (XAException e) {
         throw check(e);
      }
   }

   @Override
   public boolean isSameRM(XAResource xaRes) throws XAException {
      if (xaRes instanceof ActiveMQXAResourceWrapper wrapper) {
         xaRes = wrapper.getDelegate(false);
      }

      XAResource xaResource = getDelegate(false);
      try {
         return xaResource.isSameRM(xaRes);
      } catch (XAException e) {
         throw check(e);
      }
   }

   @Override
   public int prepare(final Xid xid) throws XAException {
      XAResource xaResource = getDelegate(true);
      logger.debug("prepare {} xid ", xaResource);
      try {
         return xaResource.prepare(xid);
      } catch (XAException e) {
         throw check(e);
      }
   }

   @Override
   public void start(final Xid xid, final int flags) throws XAException {
      XAResource xaResource = getDelegate(false);
      logger.debug("start {} xid ", xaResource);
      try {
         xaResource.start(xid, flags);
      } catch (XAException e) {
         throw check(e);
      }
   }

   @Override
   public void end(final Xid xid, final int flags) throws XAException {
      XAResource xaResource = getDelegate(false);
      logger.debug("end {} xid ", xaResource);
      try {
         xaResource.end(xid, flags);
      } catch (XAException e) {
         throw check(e);
      }
   }

   @Override
   public int getTransactionTimeout() throws XAException {
      XAResource xaResource = getDelegate(false);
      logger.debug("getTransactionTimeout {} xid ", xaResource);
      try {
         return xaResource.getTransactionTimeout();
      } catch (XAException e) {
         throw check(e);
      }
   }

   @Override
   public boolean setTransactionTimeout(final int seconds) throws XAException {
      XAResource xaResource = getDelegate(false);
      logger.debug("setTransactionTimeout {} xid ", xaResource);
      try {
         return xaResource.setTransactionTimeout(seconds);
      } catch (XAException e) {
         throw check(e);
      }
   }

   @Override
   public void connectionFailed(final ActiveMQException me, boolean failedOver) {
      if (me.getType() == ActiveMQExceptionType.DISCONNECTED) {
         logger.debug("being disconnected for server shutdown", me);
      } else {
         ActiveMQXARecoveryLogger.LOGGER.xaRecoverConnectionError(csf, me);
      }
      close();
   }

   @Override
   public void connectionFailed(final ActiveMQException me, boolean failedOver, String scaleDownTargetNodeID) {
      connectionFailed(me, failedOver);
   }

   @Override
   public void beforeReconnect(final ActiveMQException me) {
   }

   /**
    * Get the connectionFactory XAResource
    *
    * @return the connectionFactory
    * @throws XAException for any problem
    */
   private XAResource getDelegate(boolean retry) throws XAException {
      XAResource result = null;
      Exception error = null;
      try {
         result = connect();
      } catch (Exception e) {
         error = e;
      }

      if (result == null) {
         // we should always throw a retry for certain methods commit etc, if not the tx is marked as a heuristic and
         // all chaos is let loose
         XAException xae;
         if (retry) {
            xae = new XAException("Connection unavailable for xa recovery");
            xae.errorCode = XAException.XA_RETRY;
         } else {
            xae = new XAException("Error trying to connect to any providers for xa recovery");
            xae.errorCode = XAException.XAER_RMFAIL;
         }

         if (error != null) {
            xae.initCause(error);
         }
         logger.debug("Cannot get connectionFactory XAResource", xae);
         throw xae;
      }

      return result;
   }

   /**
    * Connect to the server if not already done so
    *
    * @return the connectionFactory XAResource
    * @throws Exception for any problem
    */
   protected XAResource connect() throws Exception {
      // Do we already have a valid connectionFactory?
      synchronized (ActiveMQXAResourceWrapper.lock) {
         if (delegate != null) {
            return delegate;
         }
      }

      for (XARecoveryConfig xaRecoveryConfig : xaRecoveryConfigs) {
         if (xaRecoveryConfig == null) {
            continue;
         }

         if (logger.isDebugEnabled()) {
            logger.debug("Trying to connect recovery on {} of {}", xaRecoveryConfig, Arrays.toString(xaRecoveryConfigs));
         }

         ClientSession cs = null;
         try {
            // setting ha=false because otherwise the connector would go towards any server, causing Heuristic exceptions
            // we really need to control what server it's connected to

            // Manual configuration may still use discovery, so we will keep this
            if (xaRecoveryConfig.getDiscoveryConfiguration() != null) {
               serverLocator = ActiveMQClient.createServerLocator(false, xaRecoveryConfig.getDiscoveryConfiguration());
            } else {
               serverLocator = ActiveMQClient.createServerLocator(false, xaRecoveryConfig.getTransportConfig());
            }
            if (xaRecoveryConfig.getLocatorConfig() != null) {
               serverLocator.setLocatorConfig(xaRecoveryConfig.getLocatorConfig());
            }
            serverLocator.setProtocolManagerFactory(xaRecoveryConfig.getClientProtocolManager());
            csf = serverLocator.createSessionFactory();
            if (xaRecoveryConfig.getUsername() == null) {
               cs = csf.createSession(true, false, false);
            } else {
               cs = csf.createSession(xaRecoveryConfig.getUsername(), xaRecoveryConfig.getPassword(), true, false, false, false, 1);
            }
         } catch (Throwable e) {
            ActiveMQXARecoveryLogger.LOGGER.xaRecoverAutoConnectionError(xaRecoveryConfig, e);
            logger.debug(e.getMessage(), e);

            try {
               if (serverLocator != null) {
                  serverLocator.close();
               }
            } catch (Throwable ignored) {
               logger.trace(e.getMessage(), ignored);
            }
            continue;
         }

         cs.addFailureListener(this);

         synchronized (ActiveMQXAResourceWrapper.lock) {
            delegate = cs;
         }

         return delegate;
      }
      ActiveMQXARecoveryLogger.LOGGER.recoveryConnectFailed(Arrays.toString(xaRecoveryConfigs));
      throw new ActiveMQNotConnectedException();
   }

   @Override
   public String toString() {
      return "ActiveMQXAResourceWrapper [serverLocator=" + serverLocator +
         ", csf=" +
         csf +
         ", delegate=" +
         delegate +
         ", xaRecoveryConfigs=" +
         Arrays.toString(xaRecoveryConfigs) +
         ", instance=" +
         System.identityHashCode(this) +
         "]";
   }

   /**
    * Close the connection
    */
   public void close() {
      ServerLocator oldServerLocator = null;
      ClientSessionFactory oldCSF = null;
      ClientSession oldDelegate = null;
      synchronized (ActiveMQXAResourceWrapper.lock) {
         oldCSF = csf;
         csf = null;
         oldDelegate = delegate;
         delegate = null;
         oldServerLocator = serverLocator;
         serverLocator = null;
      }

      if (oldDelegate != null) {
         try {
            oldDelegate.close();
         } catch (Throwable ignorable) {
            logger.debug(ignorable.getMessage(), ignorable);
         }
      }

      if (oldCSF != null) {
         try {
            oldCSF.close();
         } catch (Throwable ignorable) {
            logger.debug(ignorable.getMessage(), ignorable);
         }
      }

      if (oldServerLocator != null) {
         try {
            oldServerLocator.close();
         } catch (Throwable ignorable) {
            logger.debug(ignorable.getMessage(), ignorable);
         }
      }
   }

   /**
    * Check whether an XAException is fatal. If it is an RM problem we close the connection so the next call will
    * reconnect.
    *
    * @param e the xa exception
    * @return never
    * @throws XAException always
    */
   protected XAException check(final XAException e) throws XAException {
      ActiveMQXARecoveryLogger.LOGGER.xaRecoveryError(e);
      if (e.errorCode != XAException.XAER_NOTA) {
         // If any exception happened, we close the connection so we may start fresh
         close();
      }
      throw e;
   }

}

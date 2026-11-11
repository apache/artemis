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
package org.apache.activemq.artemis.ra.inflow;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.jms.MessageListener;
import javax.resource.ResourceException;
import javax.resource.spi.endpoint.MessageEndpoint;
import javax.resource.spi.endpoint.MessageEndpointFactory;
import javax.transaction.Status;
import javax.transaction.TransactionSynchronizationRegistry;
import javax.transaction.xa.XAResource;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession.QueueQuery;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.FailoverEventListener;
import org.apache.activemq.artemis.api.core.client.FailoverEventType;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.core.client.impl.ClientConsumerInternal;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.client.impl.ClientSessionInternal;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSClientLogger;
import org.apache.activemq.artemis.jms.client.ActiveMQMessage;
import org.apache.activemq.artemis.jms.client.ConnectionFactoryOptions;
import org.apache.activemq.artemis.jms.client.compatible1X.ActiveMQCompatibleMessage;
import org.apache.activemq.artemis.ra.ActiveMQRALogger;
import org.apache.activemq.artemis.ra.ActiveMQResourceAdapter;
import org.apache.activemq.artemis.service.extensions.ServiceUtils;
import org.apache.activemq.artemis.service.extensions.xa.ActiveMQXAResourceWrapper;
import org.apache.activemq.artemis.utils.AutoCreateUtil;
import org.apache.activemq.artemis.utils.FutureLatch;
import org.apache.activemq.artemis.utils.VersionLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;

public class ActiveMQMessageHandler implements MessageHandler, FailoverEventListener {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final ClientSessionInternal session;

   private ClientConsumerInternal consumer;

   private MessageEndpoint endpoint;

   private final ConnectionFactoryOptions options;

   private final ActiveMQActivation activation;

   private boolean useLocalTx;

   private boolean transacted;

   private boolean useXA = false;

   private final int sessionNr;

   private final TransactionSynchronizationRegistry tsr;

   private ClientSessionFactory cf;

   private volatile boolean connected;

   private boolean enable1XPrefix;

   public ActiveMQMessageHandler(final ConnectionFactoryOptions options,
                                 final ActiveMQActivation activation,
                                 final TransactionSynchronizationRegistry tsr,
                                 final ClientSessionInternal session,
                                 final ClientSessionFactory cf,
                                 final int sessionNr) {
      this.options = options;
      this.activation = activation;
      this.session = session;
      this.cf = cf;
      this.sessionNr = sessionNr;
      this.tsr = tsr;
   }

   public void setup() throws Exception {
      logger.trace("setup()");

      this.enable1XPrefix = activation.getConnectionFactory().isEnable1xPrefixes();

      ActiveMQActivationSpec spec = activation.getActivationSpec();
      String selector = spec.getMessageSelector();

      // Create the message consumer
      SimpleString selectorString = selector == null || selector.trim().isEmpty() ? null : SimpleString.of(selector);
      if (activation.isTopic() && spec.isSubscriptionDurable()) {
         SimpleString queueName = ActiveMQDestination.createQueueNameForSubscription(true, spec.getClientID(), spec.getSubscriptionName());

         QueueQuery subResponse = session.queueQuery(queueName);

         if (!subResponse.isExists()) {
            session.createQueue(QueueConfiguration.of(queueName).setAddress(activation.getAddress()).setFilterString(selectorString));
         } else {
            // The check for already exists should be done only at the first session
            // As a deployed MDB could set up multiple instances in order to process messages in parallel.
            if (sessionNr == 0 && subResponse.getConsumerCount() > 0) {
               if (!spec.isShareSubscriptions()) {
                  throw ActiveMQRALogger.LOGGER.canNotCreatedNonSharedSubscriber();
               } else if (logger.isDebugEnabled()) {
                  logger.debug("the mdb on destination {} already had {} consumers but the MDB is configured to share subscriptions, so no exceptions are thrown",
                     queueName, subResponse.getConsumerCount());
               }
            }

            SimpleString oldFilterString = subResponse.getFilterString();

            boolean selectorChanged = selector == null && oldFilterString != null || oldFilterString == null && selector != null || (oldFilterString != null && selector != null && !oldFilterString.toString().equals(selector));

            SimpleString oldTopicName = (enable1XPrefix && !subResponse.getAddress().startsWith(PacketImpl.OLD_TOPIC_PREFIX) ? PacketImpl.OLD_TOPIC_PREFIX : SimpleString.of("")).concat(subResponse.getAddress());

            boolean topicChanged = !oldTopicName.equals(activation.getAddress());

            if (selectorChanged || topicChanged) {
               // Delete the old durable sub
               session.deleteQueue(queueName);

               // Create the new one
               session.createQueue(QueueConfiguration.of(queueName).setAddress(activation.getAddress()).setFilterString(selectorString));
            }
         }
         consumer = (ClientConsumerInternal) session.createConsumer(queueName, null, false);
      } else {
         SimpleString tempQueueName;
         if (activation.isTopic()) {
            if (activation.getTopicTemporaryQueue() == null) {
               tempQueueName = SimpleString.of(UUID.randomUUID().toString());
               session.createQueue(QueueConfiguration.of(tempQueueName).setAddress(activation.getAddress()).setFilterString(selectorString).setDurable(false).setTemporary(true));
               activation.setTopicTemporaryQueue(tempQueueName);
            } else {
               tempQueueName = activation.getTopicTemporaryQueue();
               QueueQuery queueQuery = session.queueQuery(tempQueueName);
               if (!queueQuery.isExists()) {
                  // this is because we could be using remote servers (in cluster maybe)
                  // and the queue wasn't created on that node yet.
                  session.createQueue(QueueConfiguration.of(tempQueueName).setAddress(activation.getAddress()).setFilterString(selectorString).setDurable(false).setTemporary(true));
               }
            }
         } else {
            tempQueueName = activation.getAddress();
            AutoCreateUtil.autoCreateQueue(session, tempQueueName, null);
         }
         consumer = (ClientConsumerInternal) session.createConsumer(tempQueueName, selectorString);
      }

      // Create the endpoint, if we are transacted pass the session so it is enlisted, unless using Local TX
      MessageEndpointFactory endpointFactory = activation.getMessageEndpointFactory();
      useLocalTx = !activation.isDeliveryTransacted() && activation.getActivationSpec().isUseLocalTx();
      transacted = activation.isDeliveryTransacted();
      if (activation.isDeliveryTransacted() && !activation.getActivationSpec().isUseLocalTx()) {
         Map<String, Object> xaResourceProperties = new HashMap<>();
         xaResourceProperties.put(ActiveMQXAResourceWrapper.ACTIVEMQ_JNDI_NAME, ((ActiveMQResourceAdapter) spec.getResourceAdapter()).getJndiName());
         xaResourceProperties.put(ActiveMQXAResourceWrapper.ACTIVEMQ_NODE_ID, ((ClientSessionFactoryInternal) cf).getPrimaryNodeId());
         xaResourceProperties.put(ActiveMQXAResourceWrapper.ACTIVEMQ_PRODUCT_NAME, ActiveMQResourceAdapter.PRODUCT_NAME);
         xaResourceProperties.put(ActiveMQXAResourceWrapper.ACTIVEMQ_PRODUCT_VERSION, VersionLoader.getVersion().getFullVersion());
         XAResource xaResource = ServiceUtils.wrapXAResource(session, xaResourceProperties);

         endpoint = endpointFactory.createEndpoint(xaResource);
         useXA = true;
      } else {
         endpoint = endpointFactory.createEndpoint(null);
         useXA = false;
      }
      connected = true;
      session.addFailoverListener(this);
      consumer.setMessageHandler(this);
   }

   XAResource getXAResource() {
      return useXA ? session : null;
   }

   public Thread getCurrentThread() {
      if (consumer == null) {
         return null;
      }

      return consumer.getCurrentThread();
   }

   public Thread interruptConsumer(FutureLatch future) {
      try {
         if (consumer != null) {
            return consumer.prepareForClose(future);
         }
      } catch (Throwable e) {
         ActiveMQRALogger.LOGGER.errorInterruptingHandler(endpoint.toString(), consumer.toString(), e);
      }
      return null;
   }

   /**
    * Stop the handler
    */
   public void teardown() {
      logger.trace("teardown()");

      try {
         if (endpoint != null) {
            endpoint.release();
            endpoint = null;
         }
      } catch (Throwable t) {
         logger.debug("Error releasing endpoint {}", endpoint, t);
      }

      //only do this if we haven't been disconnected at some point whilst failing over
      if (connected) {
         try {
            consumer.close();
            if (activation.getTopicTemporaryQueue() != null) {
               // We need to delete temporary topics when the activation is stopped or messages will build up on the server
               SimpleString tmpQueue = activation.getTopicTemporaryQueue();
               QueueQuery subResponse = session.queueQuery(tmpQueue);
               if (subResponse.getConsumerCount() == 0) {
                  // This is optional really, since we now use temporaryQueues, we could simply ignore this
                  // and the server temporary queue would remove this as soon as the queue was removed
                  session.deleteQueue(tmpQueue);
               }
            }
         } catch (Throwable t) {
            logger.debug("Error closing core-queue consumer", t);
         }

         try {
            if (session != null) {
               session.close();
            }
         } catch (Throwable t) {
            logger.debug("Error releasing session {}", session, t);
         }
         try {
            if (cf != null) {
               cf.close();
            }
         } catch (Throwable t) {
            logger.debug("Error releasing session factory {}", session, t);
         }
      } else {
         //otherwise we just clean up
         try {
            if (cf != null) {
               cf.cleanup();
            }
         } catch (Throwable t) {
            logger.debug("Error releasing session factory {}", session, t);
         }

      }
   }

   @Override
   public void onMessage(final ClientMessage message) {
      logger.trace("onMessage({})", message);
      ActiveMQMessage msg;
      if (enable1XPrefix) {
         msg = ActiveMQCompatibleMessage.createMessage(message, session, options);
      } else {
         msg = ActiveMQMessage.createMessage(message, session, options);
      }

      boolean beforeDelivery = false;

      try {
         if (activation.getActivationSpec().getTransactionTimeout() > 0) {
            TransactionManager tm = ServiceUtils.getTransactionManager();
            if (tm != null) {
               tm.setTransactionTimeout(activation.getActivationSpec().getTransactionTimeout());
            }
         }
         logger.trace("ActiveMQMessageHandler::calling beforeDelivery on message {}", message);

         endpoint.beforeDelivery(ActiveMQActivation.ONMESSAGE);
         beforeDelivery = true;
         msg.doBeforeReceive();

         //In the transacted case the message must be acked *before* onMessage is called

         if (transacted) {
            message.individualAcknowledge();
         }

         ((MessageListener) endpoint).onMessage(msg);

         if (!transacted) {
            message.individualAcknowledge();
         }

         logger.trace("ActiveMQMessageHandler::calling afterDelivery on message {}", message);

         try {
            endpoint.afterDelivery();
         } catch (ResourceException e) {
            ActiveMQRALogger.LOGGER.unableToCallAfterDelivery(e);
            // If we get here, The TX was already rolled back
            // However we must do some stuff now to make sure the client message buffer is cleared
            // so we mark this as rollbackonly
            session.markRollbackOnly();
            return;
         }
         if (useLocalTx) {
            session.commit();
         }

         logger.trace("finished onMessage on {}", message);
      } catch (Throwable e) {
         ActiveMQRALogger.LOGGER.errorDeliveringMessage(e);
         // we need to call before/afterDelivery as a pair
         int status = Status.STATUS_NO_TRANSACTION;
         if (useXA && tsr != null) {
            status = tsr.getTransactionStatus();
         }
         if (beforeDelivery || status != Status.STATUS_NO_TRANSACTION) {
            if (useXA && tsr != null) {
               // This is the job for the container,
               // however if the container throws an exception because of some other errors,
               // there are situations where the container is not setting the rollback only
               // this is to avoid a scenario where afterDelivery would kick in
               tsr.setRollbackOnly();
            }

            MessageEndpoint endToUse = endpoint;
            try {
               // to avoid a NPE that would happen while the RA is in tearDown
               if (endToUse != null) {
                  endToUse.afterDelivery();
               }
            } catch (ResourceException e1) {
               ActiveMQRALogger.LOGGER.unableToCallAfterDelivery(e1);
            }
         }
         if (useLocalTx || !activation.isDeliveryTransacted()) {
            try {
               session.rollback(true);
            } catch (ActiveMQException e1) {
               ActiveMQRALogger.LOGGER.unableToRollbackTX();
            }
         }

         // This is to make sure we will issue a rollback after failures
         // so that would cleanup consumer buffers among other things
         session.markRollbackOnly();
      } finally {
         try {
            if (activation.getActivationSpec().getTransactionTimeout() > 0) {
               TransactionManager tm = ServiceUtils.getTransactionManager();
               if (tm != null) {
                  try {
                     tm.setTransactionTimeout(0);
                  } catch (SystemException ex) {
                  }
               }
            }
            session.resetIfNeeded();
         } catch (ActiveMQException e) {
            ActiveMQRALogger.LOGGER.unableToResetSession(activation.toString(), e);
            activation.startReconnectThread("Reset MessageHandler after Failure Thread");
         }
      }

   }


   @Override
   public void onMessageExpired(ClientMessage message) {
      try {
         message.checkCompletion();
      } catch (ActiveMQException e) {
         ActiveMQJMSClientLogger.LOGGER.errorProcessingMessage(e);
      }
   }

   public void start() throws ActiveMQException {
      session.start();
   }

   @Override
   public void failoverEvent(FailoverEventType eventType) {
      connected = eventType == FailoverEventType.FAILOVER_COMPLETED;
   }
}

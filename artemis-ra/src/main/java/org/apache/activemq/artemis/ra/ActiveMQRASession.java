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

import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.IllegalStateException;
import javax.jms.InvalidDestinationException;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.QueueReceiver;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;
import javax.jms.TopicSubscriber;
import javax.jms.TransactionInProgressException;
import javax.jms.XAQueueSession;
import javax.jms.XATopicSession;
import javax.resource.ResourceException;
import javax.resource.spi.ConnectionEvent;
import javax.resource.spi.ManagedConnection;
import javax.transaction.xa.XAResource;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.jms.client.ActiveMQSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * A joint interface for {@link QueueSession}, {@link TopicSession}, {@link XAQueueSession}, and {@link XATopicSession}.
 */
public class ActiveMQRASession implements QueueSession, TopicSession, XAQueueSession, XATopicSession {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private ActiveMQRAManagedConnection mc;

   private final ActiveMQRAConnectionRequestInfo cri;

   private ActiveMQRASessionFactory sf;

   private final Set<MessageConsumer> consumers;

   private final Set<MessageProducer> producers;

   public ActiveMQRASession(final ActiveMQRAManagedConnection mc, final ActiveMQRAConnectionRequestInfo cri) {
      logger.trace("constructor({}, {})", mc, cri);

      this.mc = mc;
      this.cri = cri;
      sf = null;
      consumers = new HashSet<>();
      producers = new HashSet<>();
   }

   public void setActiveMQSessionFactory(final ActiveMQRASessionFactory sf) {
      logger.trace("setActiveMQSessionFactory({})", sf);

      this.sf = sf;
   }

   /**
    * Lock
    *
    * @throws JMSException          Thrown if an error occurs
    * @throws IllegalStateException The session is closed
    */
   protected void lock() throws JMSException {
      logger.trace("lock()");

      final ActiveMQRAManagedConnection mcLocal = this.mc;
      if (mcLocal != null) {
         mcLocal.tryLock();
      } else {
         throw new IllegalStateException("Connection is not associated with a managed connection. " + this);
      }
   }

   /**
    * Unlock
    */
   protected void unlock() {
      logger.trace("unlock()");

      final ActiveMQRAManagedConnection mcLocal = this.mc;
      if (mcLocal != null) {
         mcLocal.unlock();
      }

      // We recreate the lock when returned to the pool
      // so missing the unlock after disassociation is not important
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public BytesMessage createBytesMessage() throws JMSException {
      Session session = getSessionInternal();

      logger.trace("createBytesMessage(), {}", session);

      return session.createBytesMessage();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public MapMessage createMapMessage() throws JMSException {
      Session session = getSessionInternal();

      logger.trace("createMapMessage(), {}", session);

      return session.createMapMessage();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Message createMessage() throws JMSException {
      Session session = getSessionInternal();

      logger.trace("createMessage(), {}", session);

      return session.createMessage();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public ObjectMessage createObjectMessage() throws JMSException {
      Session session = getSessionInternal();

      logger.trace("createObjectMessage(), {}", session);

      return session.createObjectMessage();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public ObjectMessage createObjectMessage(final Serializable object) throws JMSException {
      Session session = getSessionInternal();

      logger.trace("createObjectMessage({})", object, session);

      return session.createObjectMessage(object);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public StreamMessage createStreamMessage() throws JMSException {
      Session session = getSessionInternal();

      logger.trace("createStreamMessage(), {}", session);

      return session.createStreamMessage();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public TextMessage createTextMessage() throws JMSException {
      Session session = getSessionInternal();

      logger.trace("createTextMessage(), {}", session);

      return session.createTextMessage();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public TextMessage createTextMessage(final String string) throws JMSException {
      Session session = getSessionInternal();

      logger.trace("createTextMessage({}) {}", string, session);

      return session.createTextMessage(string);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public boolean getTransacted() throws JMSException {
      logger.trace("getTransacted()");

      getSessionInternal();
      return cri.isTransacted();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public MessageListener getMessageListener() throws JMSException {
      logger.trace("getMessageListener()");

      throw new IllegalStateException("Method not allowed");
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setMessageListener(final MessageListener listener) throws JMSException {
      logger.trace("setMessageListener({})", listener);

      throw new IllegalStateException("Method not allowed");
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void run() {
      logger.trace("run()");

      throw new Error("Method not allowed");
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void close() throws JMSException {
      logger.trace("close()");

      sf.closeSession(this);
      closeSession();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void commit() throws JMSException {
      if (cri.getType() == ActiveMQRAConnectionFactory.XA_CONNECTION || cri.getType() == ActiveMQRAConnectionFactory.XA_QUEUE_CONNECTION ||
         cri.getType() == ActiveMQRAConnectionFactory.XA_TOPIC_CONNECTION) {
         throw new TransactionInProgressException("XA connection");
      }

      lock();
      try {
         Session session = getSessionInternal();

         if (cri.isTransacted() == false) {
            throw new IllegalStateException("Session is not transacted");
         }

         logger.trace("Commit session {}", this);

         session.commit();
      } finally {
         unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void rollback() throws JMSException {
      if (cri.getType() == ActiveMQRAConnectionFactory.XA_CONNECTION || cri.getType() == ActiveMQRAConnectionFactory.XA_QUEUE_CONNECTION ||
         cri.getType() == ActiveMQRAConnectionFactory.XA_TOPIC_CONNECTION) {
         throw new TransactionInProgressException("XA connection");
      }

      lock();
      try {
         Session session = getSessionInternal();

         if (cri.isTransacted() == false) {
            throw new IllegalStateException("Session is not transacted");
         }

         logger.trace("Rollback session {}", this);

         session.rollback();
      } finally {
         unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void recover() throws JMSException {
      lock();
      try {
         Session session = getSessionInternal();

         if (cri.isTransacted()) {
            throw new IllegalStateException("Session is transacted");
         }

         logger.trace("Recover session {}", this);

         session.recover();
      } finally {
         unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Topic createTopic(final String topicName) throws JMSException {
      if (cri.getType() == ActiveMQRAConnectionFactory.QUEUE_CONNECTION || cri.getType() == ActiveMQRAConnectionFactory.XA_QUEUE_CONNECTION) {
         throw new IllegalStateException("Cannot create topic for javax.jms.QueueSession");
      }

      Session session = getSessionInternal();

      if (logger.isTraceEnabled()) {
         logger.trace("createTopic {} topicName={}", session, topicName);
      }

      Topic result = session.createTopic(topicName);

      if (logger.isTraceEnabled()) {
         logger.trace("createdTopic {} topic={}", session, result);
      }

      return result;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public TopicSubscriber createSubscriber(final Topic topic) throws JMSException {
      lock();
      try {
         TopicSession session = getTopicSessionInternal();

         logger.trace("createSubscriber {} topic={}", session, topic);

         TopicSubscriber result = session.createSubscriber(topic);
         result = new ActiveMQRATopicSubscriber(result, this);

         logger.trace("createdSubscriber {} ActiveMQTopicSubscriber={}", session, result);

         addConsumer(result);

         return result;
      } finally {
         unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public TopicSubscriber createSubscriber(final Topic topic,
                                           final String messageSelector,
                                           final boolean noLocal) throws JMSException {
      lock();
      try {
         TopicSession session = getTopicSessionInternal();

         if (logger.isTraceEnabled()) {
            logger.trace("createSubscriber {} topic={} selector={} noLocal={}", session, topic, messageSelector, noLocal);
         }

         TopicSubscriber result = session.createSubscriber(topic, messageSelector, noLocal);
         result = new ActiveMQRATopicSubscriber(result, this);

         logger.trace("createdSubscriber {} ActiveMQTopicSubscriber={}", session, result);

         addConsumer(result);

         return result;
      } finally {
         unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public TopicSubscriber createDurableSubscriber(final Topic topic, final String name) throws JMSException {
      if (cri.getType() == ActiveMQRAConnectionFactory.QUEUE_CONNECTION || cri.getType() == ActiveMQRAConnectionFactory.XA_QUEUE_CONNECTION) {
         throw new IllegalStateException("Cannot create durable subscriber from javax.jms.QueueSession");
      }

      lock();
      try {
         Session session = getSessionInternal();

         if (logger.isTraceEnabled()) {
            logger.trace("createDurableSubscriber {} topic={} name={}", session, topic, name);
         }

         TopicSubscriber result = session.createDurableSubscriber(topic, name);
         result = new ActiveMQRATopicSubscriber(result, this);

         logger.trace("createdDurableSubscriber {} ActiveMQTopicSubscriber={}", session, result);

         addConsumer(result);

         return result;
      } finally {
         unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public TopicSubscriber createDurableSubscriber(final Topic topic,
                                                  final String name,
                                                  final String messageSelector,
                                                  final boolean noLocal) throws JMSException {
      lock();
      try {
         Session session = getSessionInternal();

         if (logger.isTraceEnabled()) {
            logger.trace("createDurableSubscriber {} topic={} name={} selector={} noLocal={}",
               session, topic, name, messageSelector, noLocal);
         }

         TopicSubscriber result = session.createDurableSubscriber(topic, name, messageSelector, noLocal);
         result = new ActiveMQRATopicSubscriber(result, this);

         logger.trace("createdDurableSubscriber {} ActiveMQTopicSubscriber={}", session, result);

         addConsumer(result);

         return result;
      } finally {
         unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public TopicPublisher createPublisher(final Topic topic) throws JMSException {
      lock();
      try {
         TopicSession session = getTopicSessionInternal();

         logger.trace("createPublisher {} topic={}", session, topic);

         TopicPublisher result = session.createPublisher(topic);
         result = new ActiveMQRATopicPublisher(result, this);

         logger.trace("createdPublisher {} publisher=", session, result);

         addProducer(result);

         return result;
      } finally {
         unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public TemporaryTopic createTemporaryTopic() throws JMSException {
      if (cri.getType() == ActiveMQRAConnectionFactory.QUEUE_CONNECTION || cri.getType() == ActiveMQRAConnectionFactory.XA_QUEUE_CONNECTION) {
         throw new IllegalStateException("Cannot create temporary topic for javax.jms.QueueSession");
      }

      lock();
      try {
         Session session = getSessionInternal();

         logger.trace("createTemporaryTopic {}", session);

         TemporaryTopic temp = session.createTemporaryTopic();

         logger.trace("createdTemporaryTopic {} temp={}", session, temp);

         sf.addTemporaryTopic(temp);

         return temp;
      } finally {
         unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void unsubscribe(final String name) throws JMSException {
      if (cri.getType() == ActiveMQRAConnectionFactory.QUEUE_CONNECTION || cri.getType() == ActiveMQRAConnectionFactory.XA_QUEUE_CONNECTION) {
         throw new IllegalStateException("Cannot unsubscribe for javax.jms.QueueSession");
      }

      lock();
      try {
         Session session = getSessionInternal();

         logger.trace("unsubscribe {} name={}", session, name);

         session.unsubscribe(name);
      } finally {
         unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public QueueBrowser createBrowser(final Queue queue) throws JMSException {
      if (cri.getType() == ActiveMQRAConnectionFactory.TOPIC_CONNECTION || cri.getType() == ActiveMQRAConnectionFactory.XA_TOPIC_CONNECTION) {
         throw new IllegalStateException("Cannot create browser for javax.jms.TopicSession");
      }

      Session session = getSessionInternal();

      logger.trace("createBrowser {} queue={}", session, queue);

      QueueBrowser result = session.createBrowser(queue);

      logger.trace("createdBrowser {} browser={}", session, result);

      return result;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public QueueBrowser createBrowser(final Queue queue, final String messageSelector) throws JMSException {
      if (cri.getType() == ActiveMQRAConnectionFactory.TOPIC_CONNECTION || cri.getType() == ActiveMQRAConnectionFactory.XA_TOPIC_CONNECTION) {
         throw new IllegalStateException("Cannot create browser for javax.jms.TopicSession");
      }

      Session session = getSessionInternal();

      if (logger.isTraceEnabled()) {
         logger.trace("createBrowser {} queue={} selector={}", session, queue, messageSelector);
      }

      QueueBrowser result = session.createBrowser(queue, messageSelector);

      logger.trace("createdBrowser {} browser={}", session, result);

      return result;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Queue createQueue(final String queueName) throws JMSException {
      if (cri.getType() == ActiveMQRAConnectionFactory.TOPIC_CONNECTION || cri.getType() == ActiveMQRAConnectionFactory.XA_TOPIC_CONNECTION) {
         throw new IllegalStateException("Cannot create browser or javax.jms.TopicSession");
      }

      Session session = getSessionInternal();

      logger.trace("createQueue {} queueName={}", session, queueName);

      Queue result = session.createQueue(queueName);

      logger.trace("createdQueue {} queue={}", session, result);

      return result;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public QueueReceiver createReceiver(final Queue queue) throws JMSException {
      lock();
      try {
         QueueSession session = getQueueSessionInternal();

         logger.trace("createReceiver {} queue={}", session, queue);

         QueueReceiver result = session.createReceiver(queue);
         result = new ActiveMQRAQueueReceiver(result, this);

         logger.trace("createdReceiver {} receiver={}", session, result);

         addConsumer(result);

         return result;
      } finally {
         unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public QueueReceiver createReceiver(final Queue queue, final String messageSelector) throws JMSException {
      lock();
      try {
         QueueSession session = getQueueSessionInternal();

         if (logger.isTraceEnabled()) {
            logger.trace("createReceiver {} queue={} selector={}", session, queue, messageSelector);
         }

         QueueReceiver result = session.createReceiver(queue, messageSelector);
         result = new ActiveMQRAQueueReceiver(result, this);

         logger.trace("createdReceiver {} receiver={}", session, result);

         addConsumer(result);

         return result;
      } finally {
         unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public QueueSender createSender(final Queue queue) throws JMSException {
      lock();
      try {
         QueueSession session = getQueueSessionInternal();

         logger.trace("createSender {} queue={}", session, queue);

         QueueSender result = session.createSender(queue);
         result = new ActiveMQRAQueueSender(result, this);

         logger.trace("createdSender {} sender={}", session, result);

         addProducer(result);

         return result;
      } finally {
         unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public TemporaryQueue createTemporaryQueue() throws JMSException {
      if (cri.getType() == ActiveMQRAConnectionFactory.TOPIC_CONNECTION || cri.getType() == ActiveMQRAConnectionFactory.XA_TOPIC_CONNECTION) {
         throw new IllegalStateException("Cannot create temporary queue for javax.jms.TopicSession");
      }

      lock();
      try {
         Session session = getSessionInternal();

         logger.trace("createTemporaryQueue {}", session);

         TemporaryQueue temp = session.createTemporaryQueue();

         logger.trace("createdTemporaryQueue {} temp={}", session, temp);

         sf.addTemporaryQueue(temp);

         return temp;
      } finally {
         unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public MessageConsumer createConsumer(final Destination destination) throws JMSException {
      lock();
      try {
         Session session = getSessionInternal();

         logger.trace("createConsumer {} dest={}", session, destination);

         MessageConsumer result = session.createConsumer(destination);
         result = new ActiveMQRAMessageConsumer(result, this);

         logger.trace("createdConsumer {} consumer={}", session, result);

         addConsumer(result);

         return result;
      } finally {
         unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public MessageConsumer createConsumer(final Destination destination,
                                         final String messageSelector) throws JMSException {
      lock();
      try {
         Session session = getSessionInternal();

         if (logger.isTraceEnabled()) {
            logger.trace("createConsumer {} dest={} messageSelector={}", session, destination, messageSelector);
         }

         MessageConsumer result = session.createConsumer(destination, messageSelector);
         result = new ActiveMQRAMessageConsumer(result, this);

         logger.trace("createdConsumer {} consumer={}", session, result);

         addConsumer(result);

         return result;
      } finally {
         unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public MessageConsumer createConsumer(final Destination destination,
                                         final String messageSelector,
                                         final boolean noLocal) throws JMSException {
      lock();
      try {
         Session session = getSessionInternal();

         if (logger.isTraceEnabled()) {
            logger.trace("createConsumer {} dest={} messageSelector={} noLocal={}",
               session, destination, messageSelector, noLocal);
         }

         MessageConsumer result = session.createConsumer(destination, messageSelector, noLocal);
         result = new ActiveMQRAMessageConsumer(result, this);

         logger.trace("createdConsumer {} consumer={}", session, result);

         addConsumer(result);

         return result;
      } finally {
         unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public MessageProducer createProducer(final Destination destination) throws JMSException {
      lock();
      try {
         Session session = getSessionInternal();

         logger.trace("createProducer {} dest={}", session, destination);

         MessageProducer result = session.createProducer(destination);
         result = new ActiveMQRAMessageProducer(result, this);

         logger.trace("createdProducer {} producer={}", session, result);

         addProducer(result);

         return result;
      } finally {
         unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int getAcknowledgeMode() throws JMSException {
      logger.trace("getAcknowledgeMode()");

      getSessionInternal();
      return cri.getAcknowledgeMode();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public XAResource getXAResource() {
      logger.trace("getXAResource()");

      if (cri.getType() == ActiveMQRAConnectionFactory.CONNECTION || cri.getType() == ActiveMQRAConnectionFactory.QUEUE_CONNECTION ||
         cri.getType() == ActiveMQRAConnectionFactory.TOPIC_CONNECTION) {
         return null;
      }

      try {
         lock();

         return getXAResourceInternal();
      } catch (Throwable t) {
         return null;
      } finally {
         unlock();
      }
   }

   /**
    * {@return the ID of the Node that this session is associated with.}
    */
   public String getNodeId() throws JMSException {
      ActiveMQSession session = (ActiveMQSession) getSessionInternal();
      ClientSessionFactoryInternal factory = (ClientSessionFactoryInternal) session.getCoreSession().getSessionFactory();
      return factory.getPrimaryNodeId();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Session getSession() throws JMSException {
      logger.trace("getNonXAsession()");

      if (cri.getType() == ActiveMQRAConnectionFactory.CONNECTION || cri.getType() == ActiveMQRAConnectionFactory.QUEUE_CONNECTION ||
         cri.getType() == ActiveMQRAConnectionFactory.TOPIC_CONNECTION) {
         throw new IllegalStateException("Non XA connection");
      }

      lock();
      try {
         return this;
      } finally {
         unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public QueueSession getQueueSession() throws JMSException {
      logger.trace("getQueueSession()");

      if (cri.getType() == ActiveMQRAConnectionFactory.CONNECTION || cri.getType() == ActiveMQRAConnectionFactory.QUEUE_CONNECTION ||
         cri.getType() == ActiveMQRAConnectionFactory.TOPIC_CONNECTION) {
         throw new IllegalStateException("Non XA connection");
      }

      lock();
      try {
         return this;
      } finally {
         unlock();
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public TopicSession getTopicSession() throws JMSException {
      logger.trace("getTopicSession()");

      if (cri.getType() == ActiveMQRAConnectionFactory.CONNECTION || cri.getType() == ActiveMQRAConnectionFactory.QUEUE_CONNECTION ||
         cri.getType() == ActiveMQRAConnectionFactory.TOPIC_CONNECTION) {
         throw new IllegalStateException("Non XA connection");
      }

      lock();
      try {
         return this;
      } finally {
         unlock();
      }
   }

   @Override
   public MessageConsumer createSharedConsumer(final Topic topic,
                                               final String sharedSubscriptionName) throws JMSException {
      lock();
      try {
         Session session = getSessionInternal();

         if (logger.isTraceEnabled()) {
            logger.trace("createSharedConsumer {} topic={}, sharedSubscriptionName={}", session, topic, sharedSubscriptionName);
         }

         MessageConsumer result = session.createSharedConsumer(topic, sharedSubscriptionName);
         result = new ActiveMQRAMessageConsumer(result, this);

         logger.trace("createdConsumer {} consumer={}", session, result);

         addConsumer(result);

         return result;
      } finally {
         unlock();
      }
   }

   @Override
   public MessageConsumer createSharedConsumer(final Topic topic,
                                               final String sharedSubscriptionName,
                                               final String messageSelector) throws JMSException {
      lock();
      try {
         Session session = getSessionInternal();

         if (logger.isTraceEnabled()) {
            logger.trace("createSharedConsumer {} topic={}, sharedSubscriptionName={}, messageSelector={}",
               session, topic, sharedSubscriptionName, messageSelector);
         }

         MessageConsumer result = session.createSharedConsumer(topic, sharedSubscriptionName, messageSelector);
         result = new ActiveMQRAMessageConsumer(result, this);

         logger.trace("createdConsumer {} consumer={}", session, result);

         addConsumer(result);

         return result;
      } finally {
         unlock();
      }
   }

   @Override
   public MessageConsumer createDurableConsumer(final Topic topic, final String name) throws JMSException {
      lock();
      try {
         Session session = getSessionInternal();

         if (logger.isTraceEnabled()) {
            logger.trace("createSharedConsumer {} topic={}, name={}", session, topic, name);
         }

         MessageConsumer result = session.createDurableConsumer(topic, name);
         result = new ActiveMQRAMessageConsumer(result, this);

         logger.trace("createdConsumer {} consumer={}", session, result);

         addConsumer(result);

         return result;
      } finally {
         unlock();
      }
   }

   @Override
   public MessageConsumer createDurableConsumer(Topic topic,
                                                String name,
                                                String messageSelector,
                                                boolean noLocal) throws JMSException {
      lock();
      try {
         Session session = getSessionInternal();

         if (logger.isTraceEnabled()) {
            logger.trace("createDurableConsumer {} topic={}, name={}, messageSelector={}, noLocal={}",
               session, topic, name, messageSelector, noLocal);
         }

         MessageConsumer result = session.createDurableConsumer(topic, name, messageSelector, noLocal);
         result = new ActiveMQRAMessageConsumer(result, this);

         logger.trace("createdConsumer {} consumer={}", session, result);

         addConsumer(result);

         return result;
      } finally {
         unlock();
      }
   }

   @Override
   public MessageConsumer createSharedDurableConsumer(Topic topic, String name) throws JMSException {
      lock();
      try {
         Session session = getSessionInternal();

         if (logger.isTraceEnabled()) {
            logger.trace("createSharedDurableConsumer {} topic={}, name={}", session, topic, name);
         }

         MessageConsumer result = session.createSharedDurableConsumer(topic, name);
         result = new ActiveMQRAMessageConsumer(result, this);

         logger.trace("createdConsumer {} consumer={}", session, result);

         addConsumer(result);

         return result;
      } finally {
         unlock();
      }
   }

   @Override
   public MessageConsumer createSharedDurableConsumer(Topic topic,
                                                      String name,
                                                      String messageSelector) throws JMSException {
      lock();
      try {
         Session session = getSessionInternal();

         if (logger.isTraceEnabled()) {
            logger.trace("createSharedDurableConsumer {} topic={}, name={}, messageSelector={}",
               session, topic, name, messageSelector);
         }

         MessageConsumer result = session.createSharedDurableConsumer(topic, name, messageSelector);
         result = new ActiveMQRAMessageConsumer(result, this);

         logger.trace("createdConsumer {} consumer={}", session, result);

         addConsumer(result);

         return result;
      } finally {
         unlock();
      }
   }

   void setManagedConnection(final ActiveMQRAManagedConnection managedConnection) {
      logger.trace("setManagedConnection({})", managedConnection);

      if (mc != null) {
         mc.removeHandle(this);
      }

      mc = managedConnection;
   }

   /**
    * for tests only
    */
   public ManagedConnection getManagedConnection() {
      return mc;
   }

   void destroy() {
      logger.trace("destroy()");

      mc = null;
   }

   void start() throws JMSException {
      logger.trace("start()");

      if (mc != null) {
         mc.start();
      }
   }

   void stop() throws JMSException {
      logger.trace("stop()");

      if (mc != null) {
         mc.stop();
      }
   }

   void checkStrict() throws JMSException {
      logger.trace("checkStrict()");

      if (mc != null) {
         throw new IllegalStateException(ActiveMQRASessionFactory.ISE);
      }
   }

   void closeSession() throws JMSException {
      if (mc != null) {
         logger.trace("Closing session");

         try {
            mc.stop();
         } catch (Throwable t) {
            logger.trace("Error stopping managed connection", t);
         }

         synchronized (consumers) {
            for (Iterator<MessageConsumer> i = consumers.iterator(); i.hasNext(); ) {
               ActiveMQRAMessageConsumer consumer = (ActiveMQRAMessageConsumer) i.next();
               try {
                  consumer.closeConsumer();
               } catch (Throwable t) {
                  logger.trace("Error closing consumer", t);
               }
               i.remove();
            }
         }

         synchronized (producers) {
            for (Iterator<MessageProducer> i = producers.iterator(); i.hasNext(); ) {
               ActiveMQRAMessageProducer producer = (ActiveMQRAMessageProducer) i.next();
               try {
                  producer.closeProducer();
               } catch (Throwable t) {
                  logger.trace("Error closing producer", t);
               }
               i.remove();
            }
         }

         mc.removeHandle(this);
         ConnectionEvent ev = new ConnectionEvent(mc, ConnectionEvent.CONNECTION_CLOSED);
         ev.setConnectionHandle(this);
         mc.sendEvent(ev);
         mc = null;
      }
   }

   void addConsumer(final MessageConsumer consumer) {
      logger.trace("addConsumer({})", consumer);

      synchronized (consumers) {
         consumers.add(consumer);
      }
   }

   void removeConsumer(final MessageConsumer consumer) {
      logger.trace("removeConsumer({})", consumer);

      synchronized (consumers) {
         consumers.remove(consumer);
      }
   }

   void addProducer(final MessageProducer producer) {
      logger.trace("addProducer({})", producer);

      synchronized (producers) {
         producers.add(producer);
      }
   }

   void removeProducer(final MessageProducer producer) {
      logger.trace("removeProducer({})", producer);

      synchronized (producers) {
         producers.remove(producer);
      }
   }

   /**
    * Get the session and ensure that it is open
    *
    * @return The session
    * @throws JMSException          Thrown if an error occurs
    * @throws IllegalStateException The session is closed
    */
   Session getSessionInternal() throws JMSException {
      if (mc == null) {
         throw new IllegalStateException("The session is closed");
      }

      Session session = mc.getSession();

      logger.trace("getSessionInternal {} for {}", session, this);

      return session;
   }

   /**
    * Get the XA resource and ensure that it is open
    *
    * @return The XA Resource
    * @throws JMSException          Thrown if an error occurs
    * @throws IllegalStateException The session is closed
    */
   XAResource getXAResourceInternal() throws JMSException {
      if (mc == null) {
         throw new IllegalStateException("The session is closed");
      }

      try {
         XAResource xares = mc.getXAResource();

         if (logger.isTraceEnabled()) {
            logger.trace("getXAResourceInternal {} for {}", xares, this);
         }

         return xares;
      } catch (ResourceException e) {
         JMSException jmse = new JMSException("Unable to get XA Resource");
         jmse.initCause(e);
         throw jmse;
      }
   }

   QueueSession getQueueSessionInternal() throws JMSException {
      Session s = getSessionInternal();
      if (!(s instanceof QueueSession)) {
         throw new InvalidDestinationException("Attempting to use QueueSession methods on: " + this);
      }
      return (QueueSession) s;
   }

   TopicSession getTopicSessionInternal() throws JMSException {
      Session s = getSessionInternal();
      if (!(s instanceof TopicSession)) {
         throw new InvalidDestinationException("Attempting to use TopicSession methods on: " + this);
      }
      return (TopicSession) s;
   }

   public void checkState() throws JMSException {
      if (mc != null) {
         mc.checkTransactionActive();
      }
   }
}

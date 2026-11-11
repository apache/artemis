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

import javax.jms.BytesMessage;
import javax.jms.ConnectionMetaData;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.IllegalStateRuntimeException;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSProducer;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.XAConnection;
import javax.jms.XASession;
import javax.transaction.xa.XAResource;
import java.io.Serializable;

/**
 * Apache Artemis implementation of a JMSContext.
 */
public class ActiveMQJMSContext implements JMSContext {

   private static final boolean DEFAULT_AUTO_START = true;
   private final int sessionMode;

   private final ThreadAwareContext threadAwareContext;

   /**
    * Client ACK needs to hold last acked messages, so context.ack calls will be respected.
    */
   private volatile Message lastMessagesWaitingAck;

   private final ActiveMQConnectionForContext connection;
   private volatile Session session;
   private boolean autoStart = ActiveMQJMSContext.DEFAULT_AUTO_START;
   private MessageProducer innerProducer;
   private boolean xa;
   private boolean closed;

   ActiveMQJMSContext(final ActiveMQConnectionForContext connection,
                      final int ackMode,
                      final boolean xa,
                      ThreadAwareContext threadAwareContext) {
      this.connection = connection;
      this.sessionMode = ackMode;
      this.xa = xa;
      this.threadAwareContext = threadAwareContext;
   }

   public ActiveMQJMSContext(ActiveMQConnectionForContext connection,
                             int ackMode,
                             ThreadAwareContext threadAwareContext) {
      this(connection, ackMode, false, threadAwareContext);
   }

   public ActiveMQJMSContext(ActiveMQConnectionForContext connection, ThreadAwareContext threadAwareContext) {
      this(connection, SESSION_TRANSACTED, true, threadAwareContext);
   }

   // XAJMSContext implementation -------------------------------------

   public JMSContext getContext() {
      return this;
   }

   public Session getSession() {
      checkSession();
      return session;
   }

   public XAResource getXAResource() {
      checkSession();
      return ((XASession) session).getXAResource();
   }

   // JMSContext implementation -------------------------------------

   @Override
   public JMSContext createContext(int sessionMode) {
      return connection.createContext(sessionMode);
   }

   @Override
   public JMSProducer createProducer() {
      checkSession();
      try {
         return new ActiveMQJMSProducer(this, getInnerProducer());
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   private synchronized MessageProducer getInnerProducer() throws JMSException {
      if (innerProducer == null) {
         innerProducer = session.createProducer(null);
      }

      return innerProducer;
   }

   private void checkSession() {
      if (session == null) {
         synchronized (this) {
            if (closed) {
               throw new IllegalStateRuntimeException("Context is closed");
            }
            if (session == null) {
               try {
                  if (xa) {
                     session = ((XAConnection) connection).createXASession();
                  } else {
                     session = connection.createSession(sessionMode);
                  }
               } catch (JMSException e) {
                  throw JmsExceptionUtils.convertToRuntimeException(e);
               }
            }
         }
      }
   }

   @Override
   public String getClientID() {
      try {
         return connection.getClientID();
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public void setClientID(String clientID) {
      try {
         connection.setClientID(clientID);
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public ConnectionMetaData getMetaData() {
      try {
         return connection.getMetaData();
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public ExceptionListener getExceptionListener() {
      try {
         return connection.getExceptionListener();
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public void setExceptionListener(ExceptionListener listener) {
      try {
         connection.setExceptionListener(listener);
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public void start() {
      try {
         connection.start();
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public void stop() {
      threadAwareContext.assertNotMessageListenerThreadRuntime();
      try {
         connection.stop();
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public void setAutoStart(boolean autoStart) {
      this.autoStart = autoStart;
   }

   @Override
   public boolean getAutoStart() {
      return autoStart;
   }

   @Override
   public void close() {
      threadAwareContext.assertNotCompletionListenerThreadRuntime();
      threadAwareContext.assertNotMessageListenerThreadRuntime();
      try {
         synchronized (this) {
            if (session != null) {
               session.close();
            }
            connection.closeFromContext();
            closed = true;
         }
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public BytesMessage createBytesMessage() {
      checkSession();
      try {
         return session.createBytesMessage();
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public MapMessage createMapMessage() {
      checkSession();
      try {
         return session.createMapMessage();
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public Message createMessage() {
      checkSession();
      try {
         return session.createMessage();
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public ObjectMessage createObjectMessage() {
      checkSession();
      try {
         return session.createObjectMessage();
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public ObjectMessage createObjectMessage(Serializable object) {
      checkSession();
      try {
         return session.createObjectMessage(object);
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public StreamMessage createStreamMessage() {
      checkSession();
      try {
         return session.createStreamMessage();
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public TextMessage createTextMessage() {
      checkSession();
      try {
         return session.createTextMessage();
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public TextMessage createTextMessage(String text) {
      checkSession();
      try {
         return session.createTextMessage(text);
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public boolean getTransacted() {
      checkSession();
      try {
         return session.getTransacted();
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public int getSessionMode() {
      return sessionMode;
   }

   @Override
   public void commit() {
      threadAwareContext.assertNotCompletionListenerThreadRuntime();
      checkSession();
      try {
         session.commit();
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public void rollback() {
      threadAwareContext.assertNotCompletionListenerThreadRuntime();
      checkSession();
      try {
         session.rollback();
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public void recover() {
      checkSession();
      try {
         session.recover();
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public JMSConsumer createConsumer(Destination destination) {
      checkSession();
      try {
         ActiveMQJMSConsumer consumer = new ActiveMQJMSConsumer(this, session.createConsumer(destination));
         checkAutoStart();
         return consumer;
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public JMSConsumer createConsumer(Destination destination, String messageSelector) {
      checkSession();
      try {
         ActiveMQJMSConsumer consumer = new ActiveMQJMSConsumer(this, session.createConsumer(destination, messageSelector));
         checkAutoStart();
         return consumer;
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public JMSConsumer createConsumer(Destination destination, String messageSelector, boolean noLocal) {
      checkSession();
      try {
         ActiveMQJMSConsumer consumer = new ActiveMQJMSConsumer(this, session.createConsumer(destination, messageSelector, noLocal));
         checkAutoStart();
         return consumer;
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public Queue createQueue(String queueName) {
      checkSession();
      try {
         return session.createQueue(queueName);
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public Topic createTopic(String topicName) {
      checkSession();
      try {
         return session.createTopic(topicName);
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public JMSConsumer createDurableConsumer(Topic topic, String name) {
      checkSession();
      try {
         ActiveMQJMSConsumer consumer = new ActiveMQJMSConsumer(this, session.createDurableConsumer(topic, name));
         checkAutoStart();
         return consumer;
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public JMSConsumer createDurableConsumer(Topic topic, String name, String messageSelector, boolean noLocal) {
      checkSession();
      try {
         ActiveMQJMSConsumer consumer = new ActiveMQJMSConsumer(this, session.createDurableConsumer(topic, name, messageSelector, noLocal));
         checkAutoStart();
         return consumer;
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public JMSConsumer createSharedDurableConsumer(Topic topic, String name) {
      checkSession();
      try {
         ActiveMQJMSConsumer consumer = new ActiveMQJMSConsumer(this, session.createSharedDurableConsumer(topic, name));
         checkAutoStart();
         return consumer;
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public JMSConsumer createSharedDurableConsumer(Topic topic, String name, String messageSelector) {
      checkSession();
      try {
         ActiveMQJMSConsumer consumer = new ActiveMQJMSConsumer(this, session.createSharedDurableConsumer(topic, name, messageSelector));
         checkAutoStart();
         return consumer;
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public JMSConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName) {
      checkSession();
      try {
         ActiveMQJMSConsumer consumer = new ActiveMQJMSConsumer(this, session.createSharedConsumer(topic, sharedSubscriptionName));
         checkAutoStart();
         return consumer;
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public JMSConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName, String messageSelector) {
      checkSession();
      try {
         ActiveMQJMSConsumer consumer = new ActiveMQJMSConsumer(this, session.createSharedConsumer(topic, sharedSubscriptionName, messageSelector));
         checkAutoStart();
         return consumer;
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public QueueBrowser createBrowser(Queue queue) {
      checkSession();
      try {
         QueueBrowser browser = session.createBrowser(queue);
         checkAutoStart();
         return browser;
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public QueueBrowser createBrowser(Queue queue, String messageSelector) {
      checkSession();
      try {
         QueueBrowser browser = session.createBrowser(queue, messageSelector);
         checkAutoStart();
         return browser;
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public TemporaryQueue createTemporaryQueue() {
      checkSession();
      try {
         return session.createTemporaryQueue();
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public TemporaryTopic createTemporaryTopic() {
      checkSession();
      try {
         return session.createTemporaryTopic();
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public void unsubscribe(String name) {
      checkSession();
      try {
         session.unsubscribe(name);
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   @Override
   public void acknowledge() {
      checkSession();
      if (closed) {
         throw new IllegalStateRuntimeException("Context is closed");
      }
      try {
         if (lastMessagesWaitingAck != null) {
            lastMessagesWaitingAck.acknowledge();
         }
      } catch (JMSException e) {
         throw JmsExceptionUtils.convertToRuntimeException(e);
      }
   }

   /**
    * This is to be used on tests only. It's not part of the interface and it's not guaranteed to be kept on the API
    * contract.
    */
   public Session getUsedSession() {
      return this.session;
   }

   private synchronized void checkAutoStart() throws JMSException {
      if (closed) {
         throw new IllegalStateRuntimeException("Context is closed");
      }
      if (autoStart) {
         connection.start();
      }
   }

   /**
    * this is to ensure Context.acknowledge would work on ClientACK
    */
   Message setLastMessage(final Message lastMessageReceived) {
      if (sessionMode == CLIENT_ACKNOWLEDGE && lastMessageReceived != null) {
         lastMessagesWaitingAck = lastMessageReceived;
      }
      return lastMessageReceived;
   }

   public ThreadAwareContext getThreadAwareContext() {
      return threadAwareContext;
   }
}

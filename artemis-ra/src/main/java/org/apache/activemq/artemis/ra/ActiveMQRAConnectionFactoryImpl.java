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

import javax.jms.Connection;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.JMSRuntimeException;
import javax.jms.JMSSecurityException;
import javax.jms.JMSSecurityRuntimeException;
import javax.jms.QueueConnection;
import javax.jms.Session;
import javax.jms.TopicConnection;
import javax.jms.XAConnection;
import javax.jms.XAJMSContext;
import javax.jms.XAQueueConnection;
import javax.jms.XATopicConnection;
import javax.naming.NamingException;
import javax.naming.Reference;
import javax.resource.ResourceException;
import javax.resource.spi.ConnectionManager;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.ra.referenceable.ActiveMQRAConnectionFactoryObjectFactory;
import org.apache.activemq.artemis.ra.referenceable.SerializableObjectRefAddr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class ActiveMQRAConnectionFactoryImpl implements ActiveMQRAConnectionFactory {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   static final long serialVersionUID = 7981708919479859360L;

   private final ActiveMQRAManagedConnectionFactory mcf;

   private ConnectionManager cm;

   private Reference reference;

   public ActiveMQRAConnectionFactoryImpl(final ActiveMQRAManagedConnectionFactory mcf, final ConnectionManager cm) {
      logger.trace("constructor({}, {})", mcf, cm);

      this.mcf = mcf;

      if (cm == null) {
         // This is standalone usage, no appserver
         this.cm = new ActiveMQRAConnectionManager();
         logger.trace("Created new ConnectionManager={}", this.cm);
      } else {
         this.cm = cm;
      }

      logger.trace("Using ManagedConnectionFactory={}, ConnectionManager={}", mcf, cm);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public void setReference(final Reference reference) {
      logger.trace("setReference({})", reference);

      this.reference = reference;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Reference getReference() {
      logger.trace("getReference()");

      if (reference == null) {
         try {
            reference = new Reference(this.getClass().getCanonicalName(), new SerializableObjectRefAddr("ActiveMQ-CF", this), ActiveMQRAConnectionFactoryObjectFactory.class.getCanonicalName(), null);
         } catch (NamingException e) {
            ActiveMQRALogger.LOGGER.errorCreatingReference(e);
         }
      }

      return reference;

   }

   /**
    * {@inheritDoc}
    */
   @Override
   public QueueConnection createQueueConnection() throws JMSException {
      logger.trace("createQueueConnection()");

      ActiveMQRASessionFactoryImpl s = new ActiveMQRASessionFactoryImpl(mcf, cm, getResourceAdapter().getTSR(), ActiveMQRAConnectionFactory.QUEUE_CONNECTION);

      logger.trace("Created queue connection: {}", s);

      return s;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public QueueConnection createQueueConnection(final String userName, final String password) throws JMSException {
      logger.trace("createQueueConnection({}, ****)", userName);

      ActiveMQRASessionFactoryImpl s = new ActiveMQRASessionFactoryImpl(mcf, cm, getResourceAdapter().getTSR(), ActiveMQRAConnectionFactory.QUEUE_CONNECTION);
      s.setUserName(userName);
      s.setPassword(password);

      validateUser(s);

      logger.trace("Created queue connection: {}", s);

      return s;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public TopicConnection createTopicConnection() throws JMSException {
      logger.trace("createTopicConnection()");

      ActiveMQRASessionFactoryImpl s = new ActiveMQRASessionFactoryImpl(mcf, cm, getResourceAdapter().getTSR(), ActiveMQRAConnectionFactory.TOPIC_CONNECTION);

      logger.trace("Created topic connection: {}", s);

      return s;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public TopicConnection createTopicConnection(final String userName, final String password) throws JMSException {
      logger.trace("createTopicConnection({}, ****)", userName);

      ActiveMQRASessionFactoryImpl s = new ActiveMQRASessionFactoryImpl(mcf, cm, getResourceAdapter().getTSR(), ActiveMQRAConnectionFactory.TOPIC_CONNECTION);
      s.setUserName(userName);
      s.setPassword(password);
      validateUser(s);

      logger.trace("Created topic connection: {}", s);

      return s;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Connection createConnection() throws JMSException {
      logger.trace("createConnection()");

      ActiveMQRASessionFactoryImpl s = new ActiveMQRASessionFactoryImpl(mcf, cm, getResourceAdapter().getTSR(), ActiveMQRAConnectionFactory.CONNECTION);

      logger.trace("Created connection: {}", s);

      return s;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public Connection createConnection(final String userName, final String password) throws JMSException {
      logger.trace("createConnection({}, ****)", userName);

      ActiveMQRASessionFactoryImpl s = new ActiveMQRASessionFactoryImpl(mcf, cm, getResourceAdapter().getTSR(), ActiveMQRAConnectionFactory.CONNECTION);
      s.setUserName(userName);
      s.setPassword(password);

      validateUser(s);

      logger.trace("Created connection: {}", s);

      return s;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public XAQueueConnection createXAQueueConnection() throws JMSException {
      logger.trace("createXAQueueConnection()");

      ActiveMQRASessionFactoryImpl s = new ActiveMQRASessionFactoryImpl(mcf, cm, getResourceAdapter().getTSR(), ActiveMQRAConnectionFactory.XA_QUEUE_CONNECTION);

      logger.trace("Created queue connection: {}", s);

      return s;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public XAQueueConnection createXAQueueConnection(final String userName, final String password) throws JMSException {
      logger.trace("createXAQueueConnection({}, ****)", userName);

      ActiveMQRASessionFactoryImpl s = new ActiveMQRASessionFactoryImpl(mcf, cm, getResourceAdapter().getTSR(), ActiveMQRAConnectionFactory.XA_QUEUE_CONNECTION);
      s.setUserName(userName);
      s.setPassword(password);
      validateUser(s);

      logger.trace("Created queue connection: {}", s);

      return s;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public XATopicConnection createXATopicConnection() throws JMSException {
      logger.trace("createXATopicConnection()");

      ActiveMQRASessionFactoryImpl s = new ActiveMQRASessionFactoryImpl(mcf, cm, getResourceAdapter().getTSR(), ActiveMQRAConnectionFactory.XA_TOPIC_CONNECTION);

      logger.trace("Created topic connection: {}", s);

      return s;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public XATopicConnection createXATopicConnection(final String userName, final String password) throws JMSException {
      logger.trace("createXATopicConnection({}, ****)", userName);

      ActiveMQRASessionFactoryImpl s = new ActiveMQRASessionFactoryImpl(mcf, cm, getResourceAdapter().getTSR(), ActiveMQRAConnectionFactory.XA_TOPIC_CONNECTION);
      s.setUserName(userName);
      s.setPassword(password);
      validateUser(s);

      logger.trace("Created topic connection: {}", s);

      return s;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public XAConnection createXAConnection() throws JMSException {
      logger.trace("createXAConnection()");

      ActiveMQRASessionFactoryImpl s = new ActiveMQRASessionFactoryImpl(mcf, cm, getResourceAdapter().getTSR(), ActiveMQRAConnectionFactory.XA_CONNECTION);

      logger.trace("Created connection: {}", s);

      return s;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public XAConnection createXAConnection(final String userName, final String password) throws JMSException {
      logger.trace("createXAConnection({}, ****)", userName);

      ActiveMQRASessionFactoryImpl s = new ActiveMQRASessionFactoryImpl(mcf, cm, getResourceAdapter().getTSR(), ActiveMQRAConnectionFactory.XA_CONNECTION);
      s.setUserName(userName);
      s.setPassword(password);
      validateUser(s);

      logger.trace("Created connection: {}", s);

      return s;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public JMSContext createContext() {
      return createContext(null, null);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public JMSContext createContext(String userName, String password) {
      return createContext(userName, password, Session.AUTO_ACKNOWLEDGE);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public JMSContext createContext(String userName, String password, int sessionMode) {
      @SuppressWarnings("resource")
      ActiveMQRASessionFactoryImpl conn = new ActiveMQRASessionFactoryImpl(mcf, cm, getResourceAdapter().getTSR(), ActiveMQRAConnectionFactory.CONNECTION);
      conn.setUserName(userName);
      conn.setPassword(password);
      try {
         validateUser(conn);
      } catch (JMSSecurityException e) {
         JMSSecurityRuntimeException e2 = new JMSSecurityRuntimeException(e.getMessage());
         e2.initCause(e);
         throw e2;
      } catch (JMSException e) {
         JMSRuntimeException e2 = new JMSRuntimeException(e.getMessage());
         e2.initCause(e);
         throw e2;
      }
      return conn.createContext(sessionMode);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public JMSContext createContext(int sessionMode) {
      return createContext(null, null, sessionMode);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public XAJMSContext createXAContext() {
      return createXAContext(null, null);
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public XAJMSContext createXAContext(String userName, String password) {
      ActiveMQRASessionFactoryImpl conn = new ActiveMQRASessionFactoryImpl(mcf, cm, getResourceAdapter().getTSR(), ActiveMQRAConnectionFactory.XA_CONNECTION);
      conn.setUserName(userName);
      conn.setPassword(password);
      try {
         validateUser(conn);
      } catch (JMSSecurityException e) {
         JMSSecurityRuntimeException e2 = new JMSSecurityRuntimeException(e.getMessage());
         e2.initCause(e);
         throw e2;
      } catch (JMSException e) {
         JMSRuntimeException e2 = new JMSRuntimeException(e.getMessage());
         e2.initCause(e);
         throw e2;
      }
      return conn.createXAContext();
   }

   private void validateUser(ActiveMQRASessionFactoryImpl s) throws JMSException {
      Session session = s.createSession();
      session.close();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public ActiveMQConnectionFactory getDefaultFactory() throws ResourceException {
      return ((ActiveMQResourceAdapter) mcf.getResourceAdapter()).getDefaultActiveMQConnectionFactory();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public ActiveMQResourceAdapter getResourceAdapter() {
      return (ActiveMQResourceAdapter) mcf.getResourceAdapter();
   }
}

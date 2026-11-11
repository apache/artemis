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
package org.apache.activemq.artemis.tests.integration.openwire.amq;

import javax.jms.Connection;
import javax.jms.ConnectionConsumer;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ServerSessionPool;
import javax.jms.Session;
import javax.jms.Topic;

import org.apache.activemq.command.ActiveMQDestination;
import org.junit.jupiter.api.Assertions;

/**
 * adapted from: org.apache.activemq.test.JmsResourceProvider
 */
public class JmsResourceProvider {

   private boolean transacted;
   private int ackMode = Session.AUTO_ACKNOWLEDGE;
   private boolean isTopic;
   private int deliveryMode = DeliveryMode.PERSISTENT;
   private String durableName = "DummyName";
   private String clientID = getClass().getName();

   /**
    * Creates a connection.
    *
    * @see org.apache.activemq.test.JmsResourceProvider#afterCreateConnection(javax.jms.ConnectionFactory)
    */
   public Connection createConnection(ConnectionFactory cf) throws JMSException {
      Connection connection = cf.createConnection();
      if (getClientID() != null) {
         connection.setClientID(getClientID());
      }
      return connection;
   }

   /**
    * @see org.apache.activemq.test.JmsResourceProvider#createSession(javax.jms.Connection)
    */
   public Session createSession(Connection conn) throws JMSException {
      return conn.createSession(transacted, ackMode);
   }

   /**
    * @see org.apache.activemq.test.JmsResourceProvider#createConsumer(javax.jms.Session, javax.jms.Destination)
    */
   public MessageConsumer createConsumer(Session session, Destination destination) throws JMSException {
      if (isDurableSubscriber()) {
         return session.createDurableSubscriber((Topic) destination, durableName);
      }
      return session.createConsumer(destination);
   }

   /**
    * Creates a connection for a consumer.
    *
    * @param ssp - ServerSessionPool
    * @return ConnectionConsumer
    */
   public ConnectionConsumer createConnectionConsumer(Connection connection,
                                                      Destination destination,
                                                      ServerSessionPool ssp) throws JMSException {
      return connection.createConnectionConsumer(destination, null, ssp, 1);
   }

   /**
    * Creates a producer.
    *
    * @see org.apache.activemq.test.JmsResourceProvider#createProducer(javax.jms.Session, javax.jms.Destination)
    */
   public MessageProducer createProducer(Session session, Destination destination) throws JMSException {
      MessageProducer producer = session.createProducer(destination);
      producer.setDeliveryMode(deliveryMode);
      return producer;
   }

   /**
    * Creates a destination, which can either a topic or a queue.
    *
    * @see Assertions#createDestination(javax.jms.Session, java.lang.String)
    */
   public Destination createDestination(Session session, JmsTransactionTestSupport support) throws JMSException {
      if (isTopic) {
         return support.createDestination(session, ActiveMQDestination.TOPIC_TYPE);
      } else {
         return support.createDestination(session, ActiveMQDestination.QUEUE_TYPE);
      }
   }

   public boolean isDurableSubscriber() {
      return isTopic && durableName != null;
   }

   public int getAckMode() {
      return ackMode;
   }

   public void setAckMode(int ackMode) {
      this.ackMode = ackMode;
   }

   public boolean isTopic() {
      return isTopic;
   }

   public void setTopic(boolean isTopic) {
      this.isTopic = isTopic;
   }

   public boolean isTransacted() {
      return transacted;
   }

   public void setTransacted(boolean transacted) {
      this.transacted = transacted;
      if (transacted) {
         setAckMode(Session.SESSION_TRANSACTED);
      }
   }

   public int getDeliveryMode() {
      return deliveryMode;
   }

   public void setDeliveryMode(int deliveryMode) {
      this.deliveryMode = deliveryMode;
   }

   public String getClientID() {
      return clientID;
   }

   public void setClientID(String clientID) {
      this.clientID = clientID;
   }

   public String getDurableName() {
      return durableName;
   }

   public void setDurableName(String durableName) {
      this.durableName = durableName;
   }
}

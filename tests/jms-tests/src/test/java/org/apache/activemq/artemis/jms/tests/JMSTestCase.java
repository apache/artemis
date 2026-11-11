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
package org.apache.activemq.artemis.jms.tests;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.QueueConnection;
import javax.jms.TopicConnection;
import javax.jms.XAConnection;
import javax.naming.InitialContext;
import java.util.ArrayList;

import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQQueueConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQTopicConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * @deprecated this infrastructure should not be used for new code. New tests should go into
 * org.apache.activemq.tests.integration.jms at the integration-tests project.
 */
@Deprecated
public class JMSTestCase extends ActiveMQServerTestCase {

   protected static final ArrayList<String> NETTY_CONNECTOR = new ArrayList<>();

   static {
      NETTY_CONNECTOR.add("netty");
   }

   protected ActiveMQJMSConnectionFactory cf;

   protected ActiveMQQueueConnectionFactory queueCf;

   protected ActiveMQTopicConnectionFactory topicCf;

   protected InitialContext ic;

   protected static final String defaultConf = "all";

   protected static String conf;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      ic = getInitialContext();

      // All jms tests should use a specific cg which has blockOnAcknowledge = true and
      // both np and p messages are sent synchronously
      cf = new ActiveMQJMSConnectionFactory("tcp://127.0.0.1:61616?blockOnAcknowledge=true&blockOnDurableSend=true&blockOnNonDurableSend=true");
      queueCf = new ActiveMQQueueConnectionFactory("tcp://127.0.0.1:61616?blockOnAcknowledge=true&blockOnDurableSend=true&blockOnNonDurableSend=true");
      topicCf = new ActiveMQTopicConnectionFactory("tcp://127.0.0.1:61616?blockOnAcknowledge=true&blockOnDurableSend=true&blockOnNonDurableSend=true");
   }

   protected final JMSContext createContext() {
      return addContext(cf.createContext());
   }

   protected final Connection createConnection() throws JMSException {
      Connection c = cf.createConnection();
      return addConnection(c);
   }

   protected final TopicConnection createTopicConnection() throws JMSException {
      TopicConnection c = cf.createTopicConnection();
      addConnection(c);
      return c;
   }

   protected final QueueConnection createQueueConnection() throws JMSException {
      QueueConnection c = cf.createQueueConnection();
      addConnection(c);
      return c;
   }

   protected final XAConnection createXAConnection() throws JMSException {
      XAConnection c = cf.createXAConnection();
      addConnection(c);
      return c;
   }

   protected final Connection createConnection(String user, String password) throws JMSException {
      Connection c = cf.createConnection(user, password);
      addConnection(c);
      return c;
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      super.tearDown();
      if (cf != null) {
         cf.close();
      }

      cf = null;
   }

   protected Connection createConnection(ConnectionFactory cf1) throws JMSException {
      return addConnection(cf1.createConnection());
   }
}

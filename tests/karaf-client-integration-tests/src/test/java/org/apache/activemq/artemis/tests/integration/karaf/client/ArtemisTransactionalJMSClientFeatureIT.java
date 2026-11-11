/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.karaf.client;

import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import java.io.IOException;

import static org.apache.activemq.artemis.tests.integration.karaf.client.PaxExamOptions.ARTEMIS_JMS_CLIENT;
import static org.apache.activemq.artemis.tests.integration.karaf.client.PaxExamOptions.ARTEMIS_TRANSACTION_MANAGER;
import static org.apache.activemq.artemis.tests.integration.karaf.client.PaxExamOptions.KARAF;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.when;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.debugConfiguration;


/**
 * Useful docs about this test: https://ops4j1.jira.com/wiki/display/paxexam/FAQ
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class ArtemisTransactionalJMSClientFeatureIT {

   @Configuration
   public Option[] config() throws IOException {
      return options(
              KARAF.option(),
              ARTEMIS_JMS_CLIENT.option(),
              ARTEMIS_TRANSACTION_MANAGER.option(),
              when(false)
                      .useOptions(
                              debugConfiguration("5005", true))
      );
   }

   @Test
   public void testTransactionalArtemisJMSClient() throws Exception {
      // setup connection
      ConnectionFactory cf = new ActiveMQJMSConnectionFactory("tcp://localhost:61616");
      try (Connection connection = cf.createConnection()) {
         connection.start();
         Queue queue = ActiveMQJMSClient.createQueue("ArtemisTransactionalJMSClientFeatureITQueue");
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer messageProducer = session.createProducer(queue);
         MessageConsumer messageConsumer = session.createConsumer(queue);

         // send messages
         String textMessage1 = "This is a text message1";
         TextMessage message1 = session.createTextMessage(textMessage1);
         String textMessage2 = "This is a text message2";
         TextMessage message2 = session.createTextMessage(textMessage2);
         messageProducer.send(message1);
         messageProducer.send(message2);

         // assert null before commit
         TextMessage receivedMessage = (TextMessage) messageConsumer.receive(10);
         assertNull(receivedMessage);

         // commit and rollback
         session.commit();
         receivedMessage = (TextMessage) messageConsumer.receive(10);
         assertNotNull(receivedMessage);
         session.rollback();

         // assert messages
         receivedMessage = (TextMessage) messageConsumer.receive(100);
         assertEquals(textMessage1, receivedMessage.getText());
         receivedMessage = (TextMessage) messageConsumer.receive(100);
         assertEquals(textMessage2, receivedMessage.getText());
         session.commit();
         receivedMessage = (TextMessage) messageConsumer.receive(10);
         assertNull(receivedMessage);
      }
   }
}

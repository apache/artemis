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

import java.io.IOException;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;

import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

import static org.apache.activemq.artemis.tests.integration.karaf.client.PaxExamOptions.ARTEMIS_JAKARTA_CLIENT;
import static org.apache.activemq.artemis.tests.integration.karaf.client.PaxExamOptions.KARAF;
import static org.junit.Assert.assertEquals;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.when;
import static org.ops4j.pax.exam.karaf.options.KarafDistributionOption.debugConfiguration;

/**
 * Useful docs about this test: https://ops4j1.jira.com/wiki/display/paxexam/FAQ
 */
@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class ArtemisJakartaClientFeatureIT {

   @Configuration
   public Option[] config() throws IOException {
      return options(
              KARAF.option(),
              ARTEMIS_JAKARTA_CLIENT.option(),
              when(false)
                      .useOptions(
                              debugConfiguration("5005", true))
      );
   }

   @Test
   public void testArtemisJakartaClient() throws Exception {
      // setup connection
      ConnectionFactory cf = new ActiveMQJMSConnectionFactory("tcp://localhost:61616");
      try (Connection connection = cf.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         connection.start();
         Queue queue = ActiveMQJMSClient.createQueue("artemisJMSClientFeatureITQueue");
         MessageProducer producer = session.createProducer(queue);
         // send message
         String textMessage = "This is a text message";
         TextMessage message = session.createTextMessage(textMessage);
         producer.send(message);

         // receive message and assert
         MessageConsumer messageConsumer = session.createConsumer(queue);
         TextMessage messageReceived = (TextMessage) messageConsumer.receive(100);
         assertEquals(textMessage, messageReceived.getText());
      }
   }
}

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

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;

import java.io.IOException;

import static org.apache.activemq.artemis.tests.integration.karaf.client.PaxExamOptions.ARTEMIS_CORE_CLIENT;
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
public class ArtemisCoreClientFeatureIT {

   @Configuration
   public Option[] config() throws IOException {
      return options(
              KARAF.option(),
              ARTEMIS_CORE_CLIENT.option(),
              when(false)
                      .useOptions(
                              debugConfiguration("5005", true))
      );
   }

   @Test
   public void testArtemisCoreClient() throws Exception {
      try (ServerLocator locator = ActiveMQClient.createServerLocator("tcp://localhost:61616")) {
         ClientSessionFactory factory = locator.createSessionFactory();
         ClientSession session = factory.createSession();
         String queueName = "artemisCoreClientFeatureITQueue";
         ClientProducer producer = session.createProducer(queueName);
         ClientMessage message = session.createMessage(true);
         // send message
         String textMessage = "Hello";
         message.getBodyBuffer().writeString(textMessage);
         session.createQueue(QueueConfiguration.of(queueName).setRoutingType(RoutingType.ANYCAST));
         producer.send(message);

         // assert
         ClientConsumer consumer = session.createConsumer(queueName);
         session.start();
         ClientMessage msgReceived = consumer.receive();
         assertEquals(textMessage, msgReceived.getBodyBuffer().readString());
      }
   }
}

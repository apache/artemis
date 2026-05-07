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

package org.apache.activemq.artemis.tests.compatibility;

import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.ARTEMIS_2_44_0;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.SNAPSHOT;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.compatibility.base.ClasspathBase;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.utils.FileUtil;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(ParameterizedTestExtension.class)
public class MultiVersionClusterTest extends ClasspathBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String QUEUE_NAME = "MultiVersionClusterTestQueue";

   private final String broker1Version;
   private final ClassLoader broker1Classloader;

   private final String broker2Version;
   private final ClassLoader broker2Classloader;

   @Parameters(name = "broker1={0}, broker2={1}")
   public static Collection getParameters() {
      List<Object[]> combinations = new ArrayList<>();

      // Test clustering with mixed versions
      combinations.add(new Object[]{ARTEMIS_2_44_0, SNAPSHOT});
      combinations.add(new Object[]{SNAPSHOT, ARTEMIS_2_44_0});

      // The SNAPSHOT/SNAPSHOT is here as a test validation only
      combinations.add(new Object[]{SNAPSHOT, SNAPSHOT});

      return combinations;
   }

   public MultiVersionClusterTest(String broker1Version, String broker2Version) throws Exception {
      this.broker1Version = broker1Version;
      this.broker1Classloader = getClasspath(broker1Version);

      this.broker2Version = broker2Version;
      this.broker2Classloader = getClasspath(broker2Version);
   }

   @AfterEach
   public void cleanupServers() {
      try {
         evaluate(broker1Classloader, "multiVersionCluster/broker1Stop.groovy");
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
      }
      try {
         evaluate(broker2Classloader, "multiVersionCluster/broker2Stop.groovy");
      } catch (Exception e) {
         logger.warn(e.getMessage(), e);
      }
   }

   @TestTemplate
   public void testCluster() throws Throwable {
      FileUtil.deleteDirectory(serverFolder.getAbsoluteFile());
      System.out.println("Starting broker1 with version " + broker1Version);
      evaluate(broker1Classloader, "multiVersionCluster/broker1.groovy", serverFolder.getAbsolutePath(), "broker1", "61000", "61001");

      System.out.println("Starting broker2 with version " + broker2Version);
      evaluate(broker2Classloader, "multiVersionCluster/broker2.groovy", serverFolder.getAbsolutePath(), "broker2", "61001", "61000");

      // Wait for cluster to form
      evaluate(broker1Classloader, "multiVersionCluster/broker1WaitForTopology.groovy");
      evaluate(broker2Classloader, "multiVersionCluster/broker2WaitForTopology.groovy");

      // Send messages on broker0
      send(new ActiveMQConnectionFactory("tcp://localhost:61000"), 100, 1024);
      // Receive messages on broker1
      receive(new ActiveMQConnectionFactory("tcp://localhost:61001"), 100, 1024);

      // Send large messages on broker0
      send(new ActiveMQConnectionFactory("tcp://localhost:61000"), 100, 1024);
      // Receive large messages on broker1
      receive(new ActiveMQConnectionFactory("tcp://localhost:61001"), 100, 1024);

      // send amqp messages on broker 0
      send(new JmsConnectionFactory("amqp://localhost:61000"), 100, 1024);
      // receive amqp messages on broker 1
      receive(new JmsConnectionFactory("amqp://localhost:61001"), 100, 1024);

      // send amqp large messages on broker 0
      send(new JmsConnectionFactory("amqp://localhost:61000"), 10, 300 * 1024);
      // receive amqp large messages on broker 1
      receive(new JmsConnectionFactory("amqp://localhost:61001"), 10, 300 * 1024);

      evaluate(broker1Classloader, "multiVersionCluster/broker1Stop.groovy");
      evaluate(broker2Classloader, "multiVersionCluster/broker2Stop.groovy");
   }

   private void send(ConnectionFactory factory, int numberOfMessages, int textSize) throws Throwable {
      try (Connection connection = factory.createConnection()) {
         Queue queue;

         {
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            queue = session.createQueue(QUEUE_NAME);
            MessageProducer producer = session.createProducer(queue);
            boolean pending = false;
            for (int i = 0; i < numberOfMessages; i++) {
               producer.send(session.createTextMessage("A".repeat(textSize)));
               pending = true;
               if (i > 0 && i % 100 == 0) {
                  session.commit();
                  pending = false;
               }
            }
            if (pending) {
               session.commit();
            }
            session.close();
         }
      }
   }

   private void receive(ConnectionFactory factory, int numberOfMessages, int textSize) throws Throwable {
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(QUEUE_NAME);
         connection.start();
         MessageConsumer consumer = session.createConsumer(queue);
         for (int i = 0; i < numberOfMessages; i++) {
            TextMessage message = (TextMessage) consumer.receive(5000);
            assertNotNull(message, "Message " + i + " was not received");
            assertEquals("A".repeat(textSize), message.getText());
         }
      }
   }
}

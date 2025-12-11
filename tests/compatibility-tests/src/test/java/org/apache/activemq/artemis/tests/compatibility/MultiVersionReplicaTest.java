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

import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.SNAPSHOT;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.TWO_FOURTY_FOUR_ZERO;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.TWO_SEVENTEEN_ZERO;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.TWO_TWENTYTWO_ZERO;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.compatibility.base.ClasspathBase;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class MultiVersionReplicaTest extends ClasspathBase {

   private static final String QUEUE_NAME = "MultiVersionReplicaTestQueue";

   private final String main;
   private final ClassLoader mainClassloader;

   private final String backup;
   private final ClassLoader backupClassLoader;



   @Parameters(name = "main={0}, backup={1}")
   public static Collection getParameters() {
      List<Object[]> combinations = new ArrayList<>();

      if (getJavaVersion() <= 22) {
         // Old 2.x servers fail on JDK23+ without workarounds.
         combinations.add(new Object[]{TWO_TWENTYTWO_ZERO, SNAPSHOT});
         combinations.add(new Object[]{SNAPSHOT, TWO_TWENTYTWO_ZERO});
         combinations.add(new Object[]{TWO_SEVENTEEN_ZERO, SNAPSHOT});
         combinations.add(new Object[]{SNAPSHOT, TWO_SEVENTEEN_ZERO});
      }

      combinations.add(new Object[]{TWO_FOURTY_FOUR_ZERO, SNAPSHOT});
      combinations.add(new Object[]{SNAPSHOT, TWO_FOURTY_FOUR_ZERO});

      // The SNAPSHOT/SNAPSHOT is here as a test validation only, like in other cases where SNAPSHOT/SNAPSHOT is used.
      combinations.add(new Object[]{SNAPSHOT, SNAPSHOT});
      return combinations;
   }

   public MultiVersionReplicaTest(String main, String backup) throws Exception {
      this.main = main;
      this.mainClassloader = getClasspath(main);

      this.backup = backup;
      this.backupClassLoader = getClasspath(backup);
   }

   @AfterEach
   public void cleanupServers() {
      try {
         evaluate(mainClassloader, "multiVersionReplica/mainServerStop.groovy");
      } catch (Exception ignored) {
      }
      try {
         evaluate(backupClassLoader, "multiVersionReplica/backupServerStop.groovy");
      } catch (Exception ignored) {
      }
   }


   @TestTemplate
   public void testReplica() throws Throwable {
      System.out.println("Starting live");
      evaluate(mainClassloader, "multiVersionReplica/mainServer.groovy", serverFolder.getAbsolutePath(), "1", "61000", "61001");
      System.out.println("Starting backup");
      evaluate(backupClassLoader, "multiVersionReplica/backupServer.groovy", serverFolder.getAbsolutePath(), "2", "61001", "61000");

      evaluate(mainClassloader, "multiVersionReplica/mainServerIsReplicated.groovy");

      send(new ActiveMQConnectionFactory("tcp://localhost:61000"), 2000, 10);
      send(new JmsConnectionFactory("amqp://localhost:61000"), 2000, 10);

      evaluate(mainClassloader, "multiVersionReplica/mainServerStop.groovy");
      evaluate(backupClassLoader, "multiVersionReplica/backupServerIsActive.groovy");

      receive(new ActiveMQConnectionFactory("tcp://localhost:61001"), 2010);
      receive(new JmsConnectionFactory("amqp://localhost:61001"), 2010);

      evaluate(backupClassLoader, "multiVersionReplica/backupServerStop.groovy");
   }


   private void send(ConnectionFactory factory, int numberOfMessagesTx, int numberOfMessagesNonTx) throws Throwable {
      try (Connection connection = factory.createConnection()) {
         Queue queue;

         {
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
            queue = session.createQueue(QUEUE_NAME);
            MessageProducer producer = session.createProducer(queue);
            boolean pending = false;
            for (int i = 0; i < numberOfMessagesTx; i++) {
               producer.send(session.createTextMessage("Hello world!!!!!"));
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

         {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageProducer producer = session.createProducer(queue);
            for (int i = 0; i < numberOfMessagesNonTx; i++) {
               producer.send(session.createTextMessage("Hello world!!!!!"));
            }
         }
      }
   }

   private void receive(ConnectionFactory factory, int numberOfMessages) throws Throwable {
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         Queue queue = session.createQueue(QUEUE_NAME);
         connection.start();
         MessageConsumer consumer = session.createConsumer(queue);
         boolean pending = false;
         for (int i = 0; i < numberOfMessages; i++) {
            TextMessage message = (TextMessage) consumer.receive(1000);
            assertNotNull(message);
            pending = true;
            if (i > 0 && i % 100 == 0) {
               session.commit();
               pending = false;
            }
         }
         if (pending) {
            session.commit();
         }
      }
   }

}




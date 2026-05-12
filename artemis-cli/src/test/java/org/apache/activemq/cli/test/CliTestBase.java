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
package org.apache.activemq.cli.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.cli.Artemis;
import org.apache.activemq.artemis.cli.commands.Run;
import org.apache.activemq.artemis.cli.commands.queue.CreateQueue;
import org.apache.activemq.artemis.cli.commands.tools.LockAbstract;
import org.apache.activemq.artemis.nativo.jlibaio.LibaioContext;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQDestination;
import org.apache.activemq.artemis.spi.core.security.jaas.PropertiesLoader;
import org.apache.activemq.artemis.tests.extensions.TargetTempDirFactory;
import org.apache.activemq.artemis.tests.util.ArtemisTestCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class CliTestBase extends ArtemisTestCase {

   // Temp folder at ./target/tmp/<TestClassName>/<generated>
   @TempDir(factory = TargetTempDirFactory.class)
   public File temporaryFolder;

   private String original = System.getProperty("java.security.auth.login.config");

   @BeforeEach
   public void setup() throws Exception {
      Run.setEmbedded(true);
      PropertiesLoader.resetUsersAndGroupsCache();
   }

   @AfterEach
   public void tearDown() throws Exception {
      ActiveMQClient.clearThreadPools();
      System.clearProperty("artemis.instance");
      System.clearProperty("artemis.instance.etc");
      Run.setEmbedded(false);

      if (original == null) {
         System.clearProperty("java.security.auth.login.config");
      } else {
         System.setProperty("java.security.auth.login.config", original);
      }

      LockAbstract.unlock();
   }

   protected Object startServer() throws Exception {
      File rootDirectory = new File(temporaryFolder, "broker");
      setupAuth(rootDirectory);
      Run.setEmbedded(true);
      Artemis.main("create", rootDirectory.getAbsolutePath(), "--silent", "--no-fsync", "--no-autotune", "--no-web", "--require-login", "--disable-persistence", "--user", "admin", "--password", "admin");
      System.setProperty("artemis.instance", rootDirectory.getAbsolutePath());
      return Artemis.internalExecute("run");
   }

   void setupAuth() {
      setupAuth(temporaryFolder);
   }

   void setupAuth(File folder) {
      System.setProperty("java.security.auth.login.config", folder.getAbsolutePath() + "/etc/login.config");
   }

   protected void stopServer() throws Exception {
      Artemis.internalExecute("stop");
      assertTrue(Run.latchRunning.await(5, TimeUnit.SECONDS));
      assertEquals(0, LibaioContext.getTotalMaxIO());
   }

   protected ActiveMQConnectionFactory getConnectionFactory(int serverPort) {
      return new ActiveMQConnectionFactory("tcp://localhost:" + String.valueOf(serverPort));
   }

   protected void createQueue(RoutingType routingType, String address, String queueName) throws Exception {
      new CreateQueue()
         .setAddress(address)
         .setName(queueName)
         .setAnycast(RoutingType.ANYCAST.equals(routingType))
         .setMulticast(RoutingType.MULTICAST.equals(routingType))
         .setDurable(true)
         .setPreserveOnNoConsumers(true)
         .setAutoCreateAddress(true)
         .setUser("admin")
         .setPassword("admin")
         .execute(new TestActionContext());
   }

   void closeConnection(ActiveMQConnectionFactory cf, Connection connection) throws Exception {
      try {
         connection.close();
         cf.close();
      } finally {
         stopServer();
      }
   }

   protected Session createSession(Connection connection) throws JMSException {
      Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
      connection.start();
      return session;
   }

   protected List<Message> consumeMessages(Session session, String address, int noMessages, boolean fqqn) throws Exception {
      List<Message> messages = new ArrayList<>();
      Destination destination = fqqn ? session.createQueue(address) : getDestination(address);

      try (MessageConsumer consumer = session.createConsumer(destination)) {
         for (int i = 0; i < noMessages; i++) {
            Message m = consumer.receive(1000);
            assertNotNull(m);
            messages.add(m);
         }
      }

      return messages;
   }

   protected void produceMessages(Session session, String address, int noMessages, boolean fqqn) throws Exception {
      Destination destination = fqqn ? session.createQueue(address) : getDestination(address);

      try (MessageProducer producer = session.createProducer(destination)) {
         for (int i = 0; i < noMessages; i++) {
            producer.send(session.createTextMessage("test message: " + i));
         }
      }
   }

   Destination getDestination(String queueName) {
      return ActiveMQDestination.createDestination("queue://" + queueName, ActiveMQDestination.TYPE.QUEUE);
   }

   Destination getTopicDestination(String queueName) {
      return ActiveMQDestination.createDestination("topic://" + queueName, ActiveMQDestination.TYPE.TOPIC);
   }

}

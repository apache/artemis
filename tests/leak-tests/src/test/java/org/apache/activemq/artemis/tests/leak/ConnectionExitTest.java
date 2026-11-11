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
package org.apache.activemq.artemis.tests.leak;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.checkleak.core.CheckLeak;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.protocol.core.impl.RemotingConnectionImpl;
import org.apache.activemq.artemis.core.remoting.server.impl.RemotingServiceImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.ServerStatus;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.SpawnedVMSupport;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.activemq.artemis.tests.leak.MemoryAssertions.assertMemory;
import static org.apache.activemq.artemis.tests.leak.MemoryAssertions.basicMemoryAsserts;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class ConnectionExitTest extends AbstractLeakTest {

   private static final String EXIT_TEXT = "EXIT";

   private static ConnectionFactory createConnectionFactory(String protocol) {
      return CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");
   }

   private static final String QUEUE_NAME = "QUEUE_DROP";

   ActiveMQServer server;

   Queue serverQueue;

   @BeforeAll
   public static void beforeClass() throws Exception {
      assumeTrue(CheckLeak.isLoaded());
   }

   @AfterEach
   public void validateServer() throws Exception {
      CheckLeak checkLeak = new CheckLeak();

      // I am doing this check here because the test method might hold a client connection
      // so this check has to be done after the test, and before the server is stopped
      assertMemory(checkLeak, 0, RemotingConnectionImpl.class.getName());

      server.stop();

      server = null;
      serverQueue = null;

      clearServers();
      ServerStatus.clear();

      assertMemory(checkLeak, 0, ActiveMQServerImpl.class.getName());
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      server = createServer(false, createDefaultConfig(1, true));
      server.getConfiguration().clearAcceptorConfigurations();
      server.getConfiguration().addAcceptorConfiguration("server", "tcp://localhost:61616?maxInactivityDuration=1000");
      server.start();
      server.addAddressInfo(new AddressInfo(QUEUE_NAME).addRoutingType(RoutingType.ANYCAST));

      serverQueue = server.createQueue(QueueConfiguration.of(QUEUE_NAME).setAddress(QUEUE_NAME).setRoutingType(RoutingType.ANYCAST).setDurable(true));
   }

   @Test
   public void testDropConnectionsOPENWIRE_ExitClient() throws Exception {
      doDropConnections("OPENWIRE", true);
   }

   @Test
   public void testDropConnectionsOPENWIRE_ExceptionRaised() throws Exception {
      doDropConnections("OPENWIRE", true);
   }

   @Test
   public void testDropConnectionsAMQP_ExitClient() throws Exception {
      doDropConnections("AMQP", true);
   }

   @Test
   public void testDropConnectionsAMQP_ExceptionRaised() throws Exception {
      doDropConnections("AMQP", false);
   }

   @Test
   public void testDropConnectionsCORE_ExitClient() throws Exception {
      doDropConnections("CORE", true);
   }

   @Test
   public void testDropConnectionsCORE() throws Exception {
      doDropConnections("CORE", false);
   }

   private void doDropConnections(String protocol, boolean exitClient) throws Exception {

      int numberOfConnections = 1;

      Process process = SpawnedVMSupport.spawnVM(ConnectionExitTest.class.getName(), protocol, String.valueOf(numberOfConnections), String.valueOf(exitClient));
      runAfter(process::destroyForcibly);

      try {

         Wait.assertEquals(numberOfConnections, () -> server.getRemotingService().getConnections().size());

         Executor testFailureExecutor = server.getExecutorFactory().getExecutor();

         if (exitClient) {
            sendExit(protocol);
         } else {
            server.getRemotingService().getConnections().forEach(r -> {
               testFailureExecutor.execute(() -> {
                  ((RemotingServiceImpl) server.getRemotingService()).connectionException(r.getTransportConnection().getID(), new ActiveMQException("Ooops!!!! my Bad!!!! @#$@#$@#$@"));
               });
            });
         }

         Wait.assertEquals(0, () -> server.getRemotingService().getConnections().size(), 60_000);

         basicMemoryAsserts();
      } finally {
         process.destroyForcibly();
      }
   }

   // needs to be a sub-method to clear references and have check-leak not counting these
   private static void sendExit(String protocol) throws JMSException {
      ConnectionFactory factory = createConnectionFactory(protocol);
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = session.createProducer(session.createQueue(QUEUE_NAME));
         producer.send(session.createTextMessage(EXIT_TEXT));
      }
   }

   public static void main(String[] arg) {

      String protocol = arg[0];
      int numberOfConnections = Integer.parseInt(arg[1]);

      class ClientThread implements Runnable {

         Connection connection;
         Session session;
         MessageConsumer consumer;

         ClientThread(ConnectionFactory factory) throws Exception {
            connection = factory.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            consumer = session.createConsumer(session.createQueue(QUEUE_NAME));
            connection.start();
         }

         @Override
         public void run() {
            try {
               while (true) {
                  TextMessage message = (TextMessage) consumer.receive(5000);
                  if (message != null && message.getText().equals(EXIT_TEXT)) {
                     System.exit(-1);
                  }
               }
            } catch (Exception e) {
               e.printStackTrace(System.out);
            }
         }

      }

      List<ClientThread> clients = new ArrayList<>(numberOfConnections);

      ConnectionFactory connectionFactory = createConnectionFactory(protocol);

      ExecutorService service = Executors.newFixedThreadPool(numberOfConnections);

      try {
         for (int i = 0; i < numberOfConnections; i++) {
            ClientThread cThread = new ClientThread(connectionFactory);
            clients.add(cThread);
         }

         for (ClientThread t : clients) {
            service.execute(t);
         }
      } catch (Throwable e) {
         e.printStackTrace();
         System.exit(-1);
      }
   }
}
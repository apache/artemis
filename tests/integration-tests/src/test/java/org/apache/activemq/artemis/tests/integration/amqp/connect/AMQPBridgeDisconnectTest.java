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
package org.apache.activemq.artemis.tests.integration.amqp.connect;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectConfiguration;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionAddressType;
import org.apache.activemq.artemis.core.config.amqpBrokerConnectivity.AMQPBrokerConnectionElement;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.tests.integration.amqp.AmqpClientTestSupport;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.ExecuteUtil;
import org.apache.activemq.artemis.utils.SpawnedVMSupport;
import org.junit.jupiter.api.Test;

public class AMQPBridgeDisconnectTest extends AmqpClientTestSupport {

   protected static final int AMQP_PORT_2 = 5673;
   private static String DESTINATION_NAME = "AMQPBridgeReconnectTest";

   public AMQPBridgeDisconnectTest() {
   }

   public static void main(String[] arg) {
      try {
         AMQPBridgeDisconnectTest reconnect = new AMQPBridgeDisconnectTest();
         reconnect.setTestDir(arg[1]);
         if (arg[0].equals("client")) {
            reconnect.runExternal(true);
         } else {
            reconnect.runExternal(false);
         }
      } catch (Throwable var2) {
         var2.printStackTrace();
         System.exit(-1);
      }

      System.exit(0);
   }

   public void runExternal(boolean startClient) throws Exception {
      ActiveMQServer externalServer = this.createServer(AMQP_PORT_2, false);
      externalServer.getConfiguration().setPersistenceEnabled(false);
      if (startClient) {
         AMQPBrokerConnectConfiguration connectConfiguration = new AMQPBrokerConnectConfiguration("bridgeTest", "tcp://localhost:" + AMQP_PORT).setRetryInterval(100).setReconnectAttempts(-1);
         connectConfiguration.addElement((new AMQPBrokerConnectionElement()).setType(AMQPBrokerConnectionAddressType.RECEIVER).setQueueName(DESTINATION_NAME));
         externalServer.getConfiguration().addAMQPConnection(connectConfiguration);
      }
      externalServer.start();

      while (true) {
         System.out.println(AMQPBridgeDisconnectTest.class.getName() + " is running a server until someone kills it");
         Thread.sleep(5000L);
      }
   }

   @Override
   protected TransportConfiguration addAcceptorConfiguration(ActiveMQServer server, int port) {
      TransportConfiguration configuration = super.addAcceptorConfiguration(server, port);
      configuration.getExtraParams().put("amqpIdleTimeout", "1000");
      return configuration;
   }

   @Override
   protected ActiveMQServer createServer() throws Exception {
      ActiveMQServer server = this.createServer(AMQP_PORT, false);
      return server;
   }

   @Override
   protected ActiveMQServer createServer(int port, boolean start) throws Exception {
      ActiveMQServer server = super.createServer(port, start);
      server.getConfiguration().setPersistenceEnabled(false);
      server.getConfiguration().addAddressConfiguration((new CoreAddressConfiguration()).setName(DESTINATION_NAME).addRoutingType(RoutingType.ANYCAST));
      server.getConfiguration().addQueueConfiguration((QueueConfiguration.of(DESTINATION_NAME)).setAddress(DESTINATION_NAME).setRoutingType(RoutingType.ANYCAST).setDurable(true));
      return server;
   }

   @Test
   public void testClientDisconnectAfterKill() throws Exception {
      this.testDisconnect(false, false);
   }

   @Test
   public void testClientDisconnectAfterPausedProcess() throws Exception {
      this.testDisconnect(true, false);
   }

   @Test
   public void testServerDisconnectAfterKill() throws Exception {
      this.testDisconnect(false, true);
   }

   @Test
   public void testServerDisconnectAfterPausedProcess() throws Exception {
      this.testDisconnect(true, true);
   }

   public void testDisconnect(boolean pause, boolean startClient) throws Exception {
      if (startClient) {
         AMQPBrokerConnectConfiguration connectConfiguration = new AMQPBrokerConnectConfiguration("bridgeTest", "tcp://localhost:" + AMQP_PORT_2 + "?amqpIdleTimeout=1000");
         connectConfiguration.addElement((new AMQPBrokerConnectionElement()).setType(AMQPBrokerConnectionAddressType.SENDER).setQueueName(DESTINATION_NAME)).setRetryInterval(100).setReconnectAttempts(-1);
         this.server.getConfiguration().addAMQPConnection(connectConfiguration);
      }
      this.server.start();
      Process process = SpawnedVMSupport.spawnVM(AMQPBridgeDisconnectTest.class.getName(), true, startClient ? "server" : "client", getTestDir());

      try {
         ActiveMQServer var10000 = this.server;
         Wait.assertTrue(var10000::isActive);
         Queue queue = this.server.locateQueue(DESTINATION_NAME);
         assertNotNull(queue);
         Wait.assertEquals(1, queue::getConsumerCount);
         Wait.assertEquals(1, () -> {
            return this.server.getRemotingService().getConnections().size();
         });
         if (pause) {
            long pid = process.pid();
            ExecuteUtil.runCommand(true, new String[]{"kill", "-STOP", Long.toString(pid)});
         } else {
            process.destroy();
         }

         Wait.assertEquals(0, () -> {
            return this.server.getRemotingService().getConnections().size();
         }, 5000L);
         Wait.assertEquals(0, queue::getConsumerCount, 5000L);
      } finally {
         try {
            process.destroyForcibly();
         } catch (Exception var11) {
         }

      }

   }
}

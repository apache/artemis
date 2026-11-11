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
package org.apache.activemq.artemis.tests.integration.client;

import static org.apache.activemq.artemis.utils.RandomUtil.randomUUIDString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQIncompatibleClientServerException;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.protocol.core.Channel;
import org.apache.activemq.artemis.core.protocol.core.CoreRemotingConnection;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateSessionMessage;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.CreateSessionResponseMessage;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.version.impl.VersionImpl;
import org.apache.activemq.artemis.tests.util.SpawnedTestBase;
import org.apache.activemq.artemis.utils.SpawnedVMSupport;
import org.apache.activemq.artemis.utils.VersionLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IncompatibleVersionTest extends SpawnedTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final String WORD_START = "&*STARTED&*";


   private ActiveMQServer server;

   private CoreRemotingConnection connection;
   private ServerLocator locator;



   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = createServer(false, false);
      server.getConfiguration().setConnectionTTLOverride(500);
      server.start();

      locator = createInVMNonHALocator();
      ClientSessionFactory csf = createSessionFactory(locator);

      connection = (CoreRemotingConnection) csf.getConnection();
   }

   @Override
   @AfterEach
   public void tearDown() throws Exception {
      connection.destroy();

      closeServerLocator(locator);
      stopComponent(server);
      SpawnedVMSupport.forceKill();
      super.tearDown();
   }

   @Test
   public void testCompatibleClientVersion() throws Exception {
      doTestClientVersionCompatibility(true);
   }

   @Test
   public void testIncompatibleClientVersion() throws Exception {
      doTestClientVersionCompatibility(false);
   }

   @Test
   public void testCompatibleClientVersionWithRealConnection1() throws Throwable {
      assertTrue(doTestClientVersionCompatibilityWithRealConnection("1-3,5,7-10", 1));
   }

   @Test
   public void testCompatibleClientVersionWithRealConnection2() throws Throwable {
      assertTrue(doTestClientVersionCompatibilityWithRealConnection("1-3,5,7-10", 5));
   }

   @Test
   public void testCompatibleClientVersionWithRealConnection3() throws Throwable {
      assertTrue(doTestClientVersionCompatibilityWithRealConnection("1-3,5,7-10", 10));
   }

   @Test
   public void testIncompatibleClientVersionWithRealConnection1() throws Throwable {
      assertFalse(doTestClientVersionCompatibilityWithRealConnection("1-3,5,7-10", 0));
   }

   @Test
   public void testIncompatibleClientVersionWithRealConnection2() throws Throwable {
      assertFalse(doTestClientVersionCompatibilityWithRealConnection("1-3,5,7-10", 4));
   }

   @Test
   public void testIncompatibleClientVersionWithRealConnection3() throws Throwable {
      assertFalse(doTestClientVersionCompatibilityWithRealConnection("1-3,5,7-10", 100));
   }

   private void doTestClientVersionCompatibility(boolean compatible) throws Exception {
      Channel channel1 = connection.getChannel(1, -1);
      long sessionChannelID = connection.generateChannelID();
      int version = VersionLoader.getVersion().getIncrementingVersion();
      if (!compatible) {
         version = -1;
      }
      Packet request = new CreateSessionMessage(randomUUIDString(), sessionChannelID, version, null, null, ActiveMQClient.DEFAULT_MIN_LARGE_MESSAGE_SIZE, false, true, true, false, ActiveMQClient.DEFAULT_CONFIRMATION_WINDOW_SIZE, null);

      if (compatible) {
         CreateSessionResponseMessage packet = (CreateSessionResponseMessage) channel1.sendBlocking(request, PacketImpl.CREATESESSION_RESP);
         assertNotNull(packet);
         // 1 connection on the server
         assertEquals(1, server.getConnectionCount());
      } else {
         try {
            channel1.sendBlocking(request, PacketImpl.CREATESESSION_RESP);
            fail();
         } catch (ActiveMQIncompatibleClientServerException icsv) {
            //ok
         } catch (ActiveMQException e) {
            fail("Invalid Exception type:" + e.getType());
         }
         long start = System.currentTimeMillis();
         while (System.currentTimeMillis() < start + 3 * server.getConfiguration().getConnectionTtlCheckInterval()) {
            if (server.getConnectionCount() == 0) {
               break;
            }
         }
         // no connection on the server
         assertEquals(0, server.getConnectionCount());
      }
   }

   private boolean doTestClientVersionCompatibilityWithRealConnection(String verList, int ver) throws Throwable {
      String propFileName = "compatibility-test-activemq-version.properties";
      String serverStartedString = "IncompatibleVersionTest---server---started";

      Properties prop = new Properties();
      InputStream in = VersionImpl.class.getClassLoader().getResourceAsStream("activemq-version.properties");
      prop.load(in);
      prop.setProperty("activemq.version.compatibleVersionList", verList);
      prop.setProperty("activemq.version.incrementingVersion", Integer.toString(ver));
      prop.store(new FileOutputStream("target/test-classes/" + propFileName), null);

      Process serverProcess = null;
      Process client = null;
      boolean result = false;
      try {
         final CountDownLatch latch = new CountDownLatch(1);

         serverProcess = SpawnedVMSupport.spawnVMWithLogMacher(WORD_START, latch::countDown, "org.apache.activemq.artemis.tests.integration.client.IncompatibleVersionTest", new String[]{"-D" + VersionLoader.VERSION_PROP_FILE_KEY + "=" + propFileName}, true, "server", serverStartedString);
         assertTrue(latch.await(30, TimeUnit.SECONDS));
         client = SpawnedVMSupport.spawnVM("org.apache.activemq.artemis.tests.integration.client.IncompatibleVersionTest", new String[]{"-D" + VersionLoader.VERSION_PROP_FILE_KEY + "=" + propFileName}, "client");

         if (client.waitFor() == 0) {
            result = true;
         }
      } finally {
         if (serverProcess != null) {
            try {
               serverProcess.destroy();
            } catch (Throwable t) {
               // ignore
            }
         }
         if (client != null) {
            try {
               client.destroy();
            } catch (Throwable t) {
               // ignore
            }
         }
      }

      return result;
   }

   private static class ServerStarter {

      public void perform(String startedString) throws Exception {
         Configuration config = new ConfigurationImpl().setSecurityEnabled(false).addAcceptorConfiguration(new TransportConfiguration(NETTY_ACCEPTOR_FACTORY));
         ActiveMQServer server = ActiveMQServers.newActiveMQServer(config, false);
         server.start();

         while (!server.isStarted()) {
            System.out.println("Still starting");
            Thread.sleep(100);
         }

         System.out.println(WORD_START);

         logger.debug("### server: {}", startedString);
      }
   }

   private class ClientStarter {

      public void perform() throws Exception {
         ServerLocator locator = null;
         ClientSessionFactory sf = null;
         try {
            locator = createNettyNonHALocator();
            sf = locator.createSessionFactory();
            ClientSession session = sf.createSession(false, true, true);
            logger.debug("### client: connected. server incrementingVersion = {}", session.getVersion());
            session.close();
         } finally {
            closeSessionFactory(sf);
            closeServerLocator(locator);
         }
      }
   }

   public static void main(String[] args) throws Exception {
      IncompatibleVersionTest incompatibleVersionTest = new IncompatibleVersionTest();
      incompatibleVersionTest.execute(args);
   }

   private void execute(String[] args) throws Exception {
      if (args[0].equals("server")) {
         ServerStarter ss = new ServerStarter();
         ss.perform(args[1]);
      } else if (args[0].equals("client")) {
         ClientStarter cs = new ClientStarter();
         cs.perform();
      } else {
         throw new Exception("args[0] must be \"server\" or \"client\"");
      }
   }
}

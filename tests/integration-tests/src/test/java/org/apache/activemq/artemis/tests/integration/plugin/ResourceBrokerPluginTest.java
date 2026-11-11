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
package org.apache.activemq.artemis.tests.integration.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerResourcePlugin;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.core.transaction.impl.XidImpl;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class ResourceBrokerPluginTest extends ActiveMQTestBase {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final Map<String, AddressSettings> addressSettings = new HashMap<>();

   private ActiveMQServer server;

   private ClientSession clientSession;

   private ClientSessionFactory sessionFactory;

   private Configuration configuration;

   private ServerLocator locator;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      addressSettings.clear();

      configuration = createDefaultNettyConfig();

      server = createServer(true, configuration, -1, -1, addressSettings);

      // start the server
      server.start();

      locator = createNettyNonHALocator();
      sessionFactory = createSessionFactory(locator);

      clientSession = addClientSession(sessionFactory.createSession(true, false, false));
   }

   @Test
   public void testXATransaction() throws Exception {
      final CountDownLatch latch = new CountDownLatch(4);
      final Xid clientXid = new XidImpl("XA_TEST".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      server.registerBrokerPlugin(new ActiveMQServerResourcePlugin() {
         @Override
         public void beforePutTransaction(Xid xid,
                                          Transaction tx,
                                          RemotingConnection remotingConnection) throws ActiveMQException {
            assertEquals(clientXid, xid);
            latch.countDown();
         }

         @Override
         public void afterPutTransaction(Xid xid,
                                         Transaction tx,
                                         RemotingConnection remotingConnection) throws ActiveMQException {
            assertEquals(clientXid, xid);
            latch.countDown();
         }

         @Override
         public void beforeRemoveTransaction(Xid xid, RemotingConnection remotingConnection) throws ActiveMQException {
            assertEquals(clientXid, xid);
            latch.countDown();
         }

         @Override
         public void afterRemoveTransaction(Xid xid, RemotingConnection remotingConnection) throws ActiveMQException {
            assertEquals(clientXid, xid);
            latch.countDown();
         }
      });

      clientSession.start(clientXid, XAResource.TMNOFLAGS);
      clientSession.end(clientXid, XAResource.TMSUCCESS);
      clientSession.commit(clientXid, true);
      assertTrue(latch.await(100, TimeUnit.MILLISECONDS));
   }

   @Test
   public void testXAClientsMisconfiguration() throws Exception {
      // https://github.com/jbosstm/narayana/blob/5.10.5.Final/ArjunaCore/arjuna/classes/com/arjuna/ats/internal/arjuna/FormatConstants.java#L30
      final int JTA_FORMAT_ID = 131077;
      final CountDownLatch latch = new CountDownLatch(1);
      ClientSessionFactory sessionFactoryEx = createSessionFactory(locator);
      ClientSession clientSessionEx = sessionFactoryEx.createSession(true, false, false);

      server.registerBrokerPlugin(new ActiveMQServerResourcePlugin() {
         private final ConcurrentMap<String, String> clients = new ConcurrentHashMap<>();

         @Override
         public void afterPutTransaction(Xid xid,
                                         Transaction tx,
                                         RemotingConnection remotingConnection) throws ActiveMQException {
            if (xid.getFormatId() == JTA_FORMAT_ID) {
               // https://github.com/jbosstm/narayana/blob/5.10.5.Final/ArjunaJTA/jta/classes/com/arjuna/ats/jta/xa/XATxConverter.java#L188
               String nodeName = new String(Arrays.copyOfRange(xid.getGlobalTransactionId(), 28, xid.getGlobalTransactionId().length), StandardCharsets.UTF_8);

               String clientAddress = clients.putIfAbsent(nodeName, remotingConnection.getRemoteAddress());

               if (clientAddress != null && !clientAddress.equals(remotingConnection.getRemoteAddress())) {
                  latch.countDown();

                  logger.warn("Possible XA client misconfiguration. Two addresses with the same node name {}: {}/{}",
                                 nodeName, clientAddress, remotingConnection.getRemoteAddress());
               }
            }
         }
      });

      Xid xid = new XidImpl("XA_TEST".getBytes(), JTA_FORMAT_ID, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      clientSession.start(xid, XAResource.TMNOFLAGS);

      byte[] getGlobalTransactionIdEx = xid.getGlobalTransactionId().clone();
      getGlobalTransactionIdEx[0] = (byte)(getGlobalTransactionIdEx[0] + 1);
      Xid xidEx = new XidImpl(xid.getBranchQualifier(), JTA_FORMAT_ID, getGlobalTransactionIdEx);
      clientSessionEx.start(xidEx, XAResource.TMNOFLAGS);

      assertTrue(latch.await(100, TimeUnit.MILLISECONDS));
   }
}

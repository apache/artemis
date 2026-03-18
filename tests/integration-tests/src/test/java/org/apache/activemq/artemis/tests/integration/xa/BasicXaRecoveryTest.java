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
package org.apache.activemq.artemis.tests.integration.xa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import javax.management.MBeanServer;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.StoreConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.transaction.impl.XidImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQBytesMessage;
import org.apache.activemq.artemis.jms.client.ActiveMQTextMessage;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.integration.management.ManagementControlHelper;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

@ExtendWith(ParameterizedTestExtension.class)
public class BasicXaRecoveryTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final Map<String, AddressSettings> addressSettings = new HashMap<>();

   private ActiveMQServer server;

   private ClientSession clientSession;

   private ClientProducer clientProducer;

   private ClientConsumer clientConsumer;

   private ClientSessionFactory sessionFactory;

   private Configuration configuration;

   private final SimpleString atestq = SimpleString.of("atestq");

   private ServerLocator locator;

   private MBeanServer mbeanServer;

   protected StoreConfiguration.StoreType storeType;

   public BasicXaRecoveryTest(StoreConfiguration.StoreType storeType) {
      this.storeType = storeType;
   }

   @Parameters(name = "storeType={0}")
   public static Collection<Object[]> data() {
      Object[][] params = new Object[][]{{StoreConfiguration.StoreType.FILE}, {StoreConfiguration.StoreType.DATABASE}};
      return Arrays.asList(params);
   }

   @Override
   @BeforeEach
   @SuppressWarnings("unused")
   public void setUp() throws Exception {
      super.setUp();

      if (storeType == StoreConfiguration.StoreType.DATABASE) {
         Object unused = Class.forName("org.apache.derby.jdbc.EmbeddedDriver").getDeclaredConstructor().newInstance();
      }

      addressSettings.clear();

      if (storeType == StoreConfiguration.StoreType.DATABASE) {
         configuration = createDefaultJDBCConfig(true).setJMXManagementEnabled(true);
      } else {
         configuration = createDefaultInVMConfig().setJMXManagementEnabled(true);
      }

      mbeanServer = createMBeanServer();

      server = createServer(true, configuration, -1, -1, addressSettings);
      server.setMBeanServer(mbeanServer);

      // start the server
      server.start();

      // then we create a client as normal
      createClients(true, false);
   }

   @TestTemplate
   public void testBasicSendWithCommit() throws Exception {

      testBasicSendWithCommit(false);
   }

   @TestTemplate
   public void testBasicSendWithCommitWithServerStopped() throws Exception {
      testBasicSendWithCommit(true);
   }

   @TestTemplate
   public void testBasicSendWithRollback() throws Exception {
      testBasicSendWithRollback(false);
   }

   @TestTemplate
   public void testBasicSendWithRollbackWithServerStopped() throws Exception {
      testBasicSendWithRollback(true);
   }

   @TestTemplate
   public void testMultipleBeforeSendWithCommit() throws Exception {
      testMultipleBeforeSendWithCommit(false);
   }

   @TestTemplate
   public void testMultipleBeforeSendWithCommitWithServerStopped() throws Exception {
      testMultipleBeforeSendWithCommit(true);
   }

   @TestTemplate
   public void testMultipleTxSendWithCommit() throws Exception {
      testMultipleTxSendWithCommit(false);
   }

   @TestTemplate
   public void testMultipleTxSendWithCommitWithServerStopped() throws Exception {
      testMultipleTxSendWithCommit(true);
   }

   @TestTemplate
   public void testMultipleTxSendWithRollback() throws Exception {
      testMultipleTxSendWithRollback(false);
   }

   @TestTemplate
   public void testMultipleTxSendWithRollbackWithServerStopped() throws Exception {
      testMultipleTxSendWithRollback(true);
   }

   @TestTemplate
   public void testMultipleTxSendWithCommitAndRollback() throws Exception {
      testMultipleTxSendWithCommitAndRollback(false);
   }

   @TestTemplate
   public void testMultipleTxSendWithCommitAndRollbackWithServerStopped() throws Exception {
      testMultipleTxSendWithCommitAndRollback(true);
   }

   @TestTemplate
   public void testMultipleTxSameXidSendWithCommit() throws Exception {
      testMultipleTxSameXidSendWithCommit(false);
   }

   @TestTemplate
   public void testMultipleTxSameXidSendWithCommitWithServerStopped() throws Exception {
      testMultipleTxSameXidSendWithCommit(true);
   }

   @TestTemplate
   public void testBasicReceiveWithCommit() throws Exception {
      testBasicReceiveWithCommit(false);
   }

   @TestTemplate
   public void testBasicReceiveWithCommitWithServerStopped() throws Exception {
      testBasicReceiveWithCommit(true);
   }

   @TestTemplate
   public void testBasicReceiveWithRollback() throws Exception {
      testBasicReceiveWithRollback(false);
   }

   @TestTemplate
   public void testBasicReceiveWithRollbackWithServerStopped() throws Exception {
      testBasicReceiveWithRollback(true);
   }

   @TestTemplate
   public void testMultipleTxReceiveWithCommit() throws Exception {
      testMultipleTxReceiveWithCommit(false);
   }

   @TestTemplate
   public void testMultipleTxReceiveWithCommitWithServerStopped() throws Exception {
      testMultipleTxReceiveWithCommit(true);
   }

   @TestTemplate
   public void testMultipleTxReceiveWithRollback() throws Exception {
      testMultipleTxReceiveWithRollback(false);
   }

   @TestTemplate
   public void testMultipleTxReceiveWithRollbackWithServerStopped() throws Exception {
      testMultipleTxReceiveWithRollback(true);
   }

   @TestTemplate
   public void testPagingServerRestarted() throws Exception {
      if (storeType == StoreConfiguration.StoreType.DATABASE) {
         return;
      }
      verifyPaging(true);
   }

   @TestTemplate
   public void testPaging() throws Exception {
      if (storeType == StoreConfiguration.StoreType.DATABASE) {
         return;
      }
      verifyPaging(false);
   }

   public void verifyPaging(final boolean restartServer) throws Exception {
      if (storeType == StoreConfiguration.StoreType.DATABASE) {
         return;
      }
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      SimpleString pageQueue = SimpleString.of("pagequeue");

      AddressSettings pageAddressSettings = new AddressSettings().setMaxSizeBytes(100 * 1024).setPageSizeBytes(10 * 1024);

      addressSettings.put(pageQueue.toString(), pageAddressSettings);

      addSettings();

      clientSession.createQueue(QueueConfiguration.of(pageQueue));

      clientSession.start(xid, XAResource.TMNOFLAGS);

      ClientProducer pageProducer = clientSession.createProducer(pageQueue);

      for (int i = 0; i < 1000; i++) {
         ClientMessage m = createBytesMessage(new byte[512], true);
         pageProducer.send(m);
      }

      pageProducer.close();

      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      logger.debug("*** stopping and restarting");

      if (restartServer) {
         stopAndRestartServer();
      } else {
         recreateClients();
      }

      Xid[] xids = clientSession.recover(XAResource.TMSTARTRSCAN);
      assertEquals(1, xids.length);
      assertEquals(xids[0].getFormatId(), xid.getFormatId());
      ActiveMQTestBase.assertEqualsByteArrays(xids[0].getBranchQualifier(), xid.getBranchQualifier());
      ActiveMQTestBase.assertEqualsByteArrays(xids[0].getGlobalTransactionId(), xid.getGlobalTransactionId());

      clientSession.commit(xid, false);

      clientSession.close();

      clientSession = sessionFactory.createSession(false, false, false);

      clientSession.start();

      ClientConsumer pageConsumer = clientSession.createConsumer(pageQueue);

      for (int i = 0; i < 1000; i++) {
         ClientMessage m = pageConsumer.receive(10000);

         assertNotNull(m);
         m.acknowledge();
         clientSession.commit();
      }

      assertNull(pageConsumer.receiveImmediate());

   }

   @TestTemplate
   public void testRollbackPaging() throws Exception {
      if (storeType == StoreConfiguration.StoreType.DATABASE) {
         return;
      }
      testRollbackPaging(false);
   }

   @TestTemplate
   public void testRollbackPagingServerRestarted() throws Exception {
      if (storeType == StoreConfiguration.StoreType.DATABASE) {
         return;
      }
      testRollbackPaging(true);
   }

   public void testRollbackPaging(final boolean restartServer) throws Exception {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      SimpleString pageQueue = SimpleString.of("pagequeue");

      AddressSettings pageAddressSettings = new AddressSettings().setMaxSizeBytes(100 * 1024).setPageSizeBytes(10 * 1024);

      addressSettings.put(pageQueue.toString(), pageAddressSettings);

      addSettings();

      clientSession.createQueue(QueueConfiguration.of(pageQueue));

      clientSession.start(xid, XAResource.TMNOFLAGS);

      ClientProducer pageProducer = clientSession.createProducer(pageQueue);

      for (int i = 0; i < 1000; i++) {
         ClientMessage m = createBytesMessage(new byte[512], true);
         pageProducer.send(m);
      }

      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      if (restartServer) {
         stopAndRestartServer();
      } else {
         recreateClients();
      }

      Xid[] xids = clientSession.recover(XAResource.TMSTARTRSCAN);
      assertEquals(1, xids.length);
      assertEquals(xids[0].getFormatId(), xid.getFormatId());
      ActiveMQTestBase.assertEqualsByteArrays(xids[0].getBranchQualifier(), xid.getBranchQualifier());
      ActiveMQTestBase.assertEqualsByteArrays(xids[0].getGlobalTransactionId(), xid.getGlobalTransactionId());

      clientSession.rollback(xid);

      clientSession.start();

      ClientConsumer pageConsumer = clientSession.createConsumer(pageQueue);

      assertNull(pageConsumer.receiveImmediate());

      // Management message (from createQueue) will not be taken into account again as it is nonPersistent

   }

   @TestTemplate
   public void testNonPersistent() throws Exception {
      testNonPersistent(true);
      testNonPersistent(false);
   }

   public void testNonPersistent(final boolean commit) throws Exception {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      ClientMessage m1 = createTextMessage("m1", false);
      ClientMessage m2 = createTextMessage("m2", false);
      ClientMessage m3 = createTextMessage("m3", false);
      ClientMessage m4 = createTextMessage("m4", false);

      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientProducer.send(m1);
      clientProducer.send(m2);
      clientProducer.send(m3);
      clientProducer.send(m4);
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      stopAndRestartServer();

      Xid[] xids = clientSession.recover(XAResource.TMSTARTRSCAN);

      assertEquals(1, xids.length);
      assertEquals(xids[0].getFormatId(), xid.getFormatId());
      ActiveMQTestBase.assertEqualsByteArrays(xids[0].getBranchQualifier(), xid.getBranchQualifier());
      ActiveMQTestBase.assertEqualsByteArrays(xids[0].getGlobalTransactionId(), xid.getGlobalTransactionId());
      xids = clientSession.recover(XAResource.TMENDRSCAN);
      assertEquals(0, xids.length);
      if (commit) {
         clientSession.commit(xid, false);
      } else {
         clientSession.rollback(xid);
      }

      xids = clientSession.recover(XAResource.TMSTARTRSCAN);
      assertEquals(0, xids.length);
   }

   @TestTemplate
   public void testNonPersistentMultipleIDs() throws Exception {
      for (int i = 0; i < 10; i++) {
         Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

         ClientMessage m1 = createTextMessage("m1", false);
         ClientMessage m2 = createTextMessage("m2", false);
         ClientMessage m3 = createTextMessage("m3", false);
         ClientMessage m4 = createTextMessage("m4", false);

         clientSession.start(xid, XAResource.TMNOFLAGS);
         clientProducer.send(m1);
         clientProducer.send(m2);
         clientProducer.send(m3);
         clientProducer.send(m4);
         clientSession.end(xid, XAResource.TMSUCCESS);
         clientSession.prepare(xid);

         if (i == 2) {
            clientSession.commit(xid, false);
         }

         recreateClients();

      }

      stopAndRestartServer();

      Xid[] xids = clientSession.recover(XAResource.TMSTARTRSCAN);

      assertEquals(9, xids.length);
   }

   public void testBasicSendWithCommit(final boolean stopServer) throws Exception {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      ClientMessage m1 = createTextMessage("m1");
      ClientMessage m2 = createTextMessage("m2");
      ClientMessage m3 = createTextMessage("m3");
      ClientMessage m4 = createTextMessage("m4");

      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientProducer.send(m1);
      clientProducer.send(m2);
      clientProducer.send(m3);
      clientProducer.send(m4);
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      if (stopServer) {
         stopAndRestartServer();
      } else {
         recreateClients();
      }

      Xid[] xids = clientSession.recover(XAResource.TMSTARTRSCAN);
      assertEquals(1, xids.length);
      assertEquals(xids[0].getFormatId(), xid.getFormatId());
      ActiveMQTestBase.assertEqualsByteArrays(xids[0].getBranchQualifier(), xid.getBranchQualifier());
      ActiveMQTestBase.assertEqualsByteArrays(xids[0].getGlobalTransactionId(), xid.getGlobalTransactionId());

      xids = clientSession.recover(XAResource.TMENDRSCAN);
      assertEquals(0, xids.length);

      clientSession.commit(xid, false);
      clientSession.start();
      ClientMessage m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m2", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m3", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m4", m.getBodyBuffer().readString());
   }

   public void testBasicSendWithRollback(final boolean stopServer) throws Exception {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());

      ClientMessage m1 = createTextMessage("m1");
      ClientMessage m2 = createTextMessage("m2");
      ClientMessage m3 = createTextMessage("m3");
      ClientMessage m4 = createTextMessage("m4");

      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientProducer.send(m1);
      clientProducer.send(m2);
      clientProducer.send(m3);
      clientProducer.send(m4);
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      logger.debug("shutting down server");

      if (stopServer) {
         stopAndRestartServer();
      } else {
         recreateClients();
      }

      logger.debug("restarted");

      Xid[] xids = clientSession.recover(XAResource.TMSTARTRSCAN);

      assertEquals(1, xids.length);
      assertEquals(xids[0].getFormatId(), xid.getFormatId());
      ActiveMQTestBase.assertEqualsByteArrays(xids[0].getBranchQualifier(), xid.getBranchQualifier());
      ActiveMQTestBase.assertEqualsByteArrays(xids[0].getGlobalTransactionId(), xid.getGlobalTransactionId());
      xids = clientSession.recover(XAResource.TMENDRSCAN);
      assertEquals(0, xids.length);
      clientSession.rollback(xid);
      clientSession.start();
      ClientMessage m = clientConsumer.receiveImmediate();
      assertNull(m);
   }

   public void testMultipleBeforeSendWithCommit(final boolean stopServer) throws Exception {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      ClientMessage m1 = createTextMessage("m1");
      ClientMessage m2 = createTextMessage("m2");
      ClientMessage m3 = createTextMessage("m3");
      ClientMessage m4 = createTextMessage("m4");
      ClientMessage m5 = createTextMessage("m5");
      ClientMessage m6 = createTextMessage("m6");
      ClientMessage m7 = createTextMessage("m7");
      ClientMessage m8 = createTextMessage("m8");
      ClientSession clientSession2 = sessionFactory.createSession(false, false, true);
      ClientProducer clientProducer2 = clientSession2.createProducer(atestq);
      clientProducer2.send(m1);
      clientProducer2.send(m2);
      clientProducer2.send(m3);
      clientProducer2.send(m4);
      clientSession2.close();
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientProducer.send(m5);
      clientProducer.send(m6);
      clientProducer.send(m7);
      clientProducer.send(m8);
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      if (stopServer) {
         stopAndRestartServer();
      } else {
         recreateClients();
      }

      Xid[] xids = clientSession.recover(XAResource.TMSTARTRSCAN);

      assertEquals(1, xids.length);
      assertEquals(xids[0].getFormatId(), xid.getFormatId());
      ActiveMQTestBase.assertEqualsByteArrays(xids[0].getBranchQualifier(), xid.getBranchQualifier());
      ActiveMQTestBase.assertEqualsByteArrays(xids[0].getGlobalTransactionId(), xid.getGlobalTransactionId());
      xids = clientSession.recover(XAResource.TMENDRSCAN);
      assertEquals(0, xids.length);
      clientSession.commit(xid, false);
      clientSession.start();
      ClientMessage m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m5", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m6", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m7", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m8", m.getBodyBuffer().readString());
   }

   public void testMultipleTxSendWithCommit(final boolean stopServer) throws Exception {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      Xid xid2 = new XidImpl("xa2".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      ClientMessage m1 = createTextMessage("m1");
      ClientMessage m2 = createTextMessage("m2");
      ClientMessage m3 = createTextMessage("m3");
      ClientMessage m4 = createTextMessage("m4");
      ClientMessage m5 = createTextMessage("m5");
      ClientMessage m6 = createTextMessage("m6");
      ClientMessage m7 = createTextMessage("m7");
      ClientMessage m8 = createTextMessage("m8");
      ClientSession clientSession2 = sessionFactory.createSession(true, false, true);
      ClientProducer clientProducer2 = clientSession2.createProducer(atestq);
      clientSession2.start(xid2, XAResource.TMNOFLAGS);
      clientProducer2.send(m1);
      clientProducer2.send(m2);
      clientProducer2.send(m3);
      clientProducer2.send(m4);
      clientSession2.end(xid2, XAResource.TMSUCCESS);
      clientSession2.prepare(xid2);
      clientSession2.close();
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientProducer.send(m5);
      clientProducer.send(m6);
      clientProducer.send(m7);
      clientProducer.send(m8);
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      if (stopServer) {
         stopAndRestartServer();
      } else {
         recreateClients();
      }

      Xid[] xids = clientSession.recover(XAResource.TMSTARTRSCAN);

      assertEquals(2, xids.length);
      assertEqualXids(xids, xid, xid2);
      xids = clientSession.recover(XAResource.TMENDRSCAN);
      assertEquals(0, xids.length);
      clientSession.commit(xid, false);
      clientSession.commit(xid2, false);
      clientSession.start();
      ClientMessage m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m5", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m6", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m7", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m8", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m2", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m3", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m4", m.getBodyBuffer().readString());
   }

   public void testMultipleTxSendWithRollback(final boolean stopServer) throws Exception {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      Xid xid2 = new XidImpl("xa2".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      ClientMessage m1 = createTextMessage("m1");
      ClientMessage m2 = createTextMessage("m2");
      ClientMessage m3 = createTextMessage("m3");
      ClientMessage m4 = createTextMessage("m4");
      ClientMessage m5 = createTextMessage("m5");
      ClientMessage m6 = createTextMessage("m6");
      ClientMessage m7 = createTextMessage("m7");
      ClientMessage m8 = createTextMessage("m8");
      ClientSession clientSession2 = sessionFactory.createSession(true, false, true);
      ClientProducer clientProducer2 = clientSession2.createProducer(atestq);
      clientSession2.start(xid2, XAResource.TMNOFLAGS);
      clientProducer2.send(m1);
      clientProducer2.send(m2);
      clientProducer2.send(m3);
      clientProducer2.send(m4);
      clientSession2.end(xid2, XAResource.TMSUCCESS);
      clientSession2.prepare(xid2);
      clientSession2.close();
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientProducer.send(m5);
      clientProducer.send(m6);
      clientProducer.send(m7);
      clientProducer.send(m8);
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      if (stopServer) {
         stopAndRestartServer();
      } else {
         recreateClients();
      }

      Xid[] xids = clientSession.recover(XAResource.TMSTARTRSCAN);

      assertEquals(2, xids.length);
      assertEqualXids(xids, xid, xid2);
      xids = clientSession.recover(XAResource.TMENDRSCAN);
      assertEquals(0, xids.length);
      clientSession.rollback(xid);
      clientSession.rollback(xid2);
      clientSession.start();
      ClientMessage m = clientConsumer.receiveImmediate();
      assertNull(m);
   }

   public void testMultipleTxSendWithCommitAndRollback(final boolean stopServer) throws Exception {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      Xid xid2 = new XidImpl("xa2".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      ClientMessage m1 = createTextMessage("m1");
      ClientMessage m2 = createTextMessage("m2");
      ClientMessage m3 = createTextMessage("m3");
      ClientMessage m4 = createTextMessage("m4");
      ClientMessage m5 = createTextMessage("m5");
      ClientMessage m6 = createTextMessage("m6");
      ClientMessage m7 = createTextMessage("m7");
      ClientMessage m8 = createTextMessage("m8");
      ClientSession clientSession2 = sessionFactory.createSession(true, false, true);
      ClientProducer clientProducer2 = clientSession2.createProducer(atestq);
      clientSession2.start(xid2, XAResource.TMNOFLAGS);
      clientProducer2.send(m1);
      clientProducer2.send(m2);
      clientProducer2.send(m3);
      clientProducer2.send(m4);
      clientSession2.end(xid2, XAResource.TMSUCCESS);
      clientSession2.prepare(xid2);
      clientSession2.close();
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientProducer.send(m5);
      clientProducer.send(m6);
      clientProducer.send(m7);
      clientProducer.send(m8);
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      if (stopServer) {
         stopAndRestartServer();
      } else {
         recreateClients();
      }

      Xid[] xids = clientSession.recover(XAResource.TMSTARTRSCAN);

      assertEquals(2, xids.length);
      assertEqualXids(xids, xid, xid2);
      xids = clientSession.recover(XAResource.TMENDRSCAN);
      assertEquals(0, xids.length);
      clientSession.rollback(xid);
      clientSession.commit(xid2, false);
      clientSession.start();
      ClientMessage m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m2", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m3", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m4", m.getBodyBuffer().readString());
      m = clientConsumer.receiveImmediate();
      assertNull(m);
   }

   public void testMultipleTxSameXidSendWithCommit(final boolean stopServer) throws Exception {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      ClientMessage m1 = createTextMessage("m1");
      ClientMessage m2 = createTextMessage("m2");
      ClientMessage m3 = createTextMessage("m3");
      ClientMessage m4 = createTextMessage("m4");
      ClientMessage m5 = createTextMessage("m5");
      ClientMessage m6 = createTextMessage("m6");
      ClientMessage m7 = createTextMessage("m7");
      ClientMessage m8 = createTextMessage("m8");
      ClientSession clientSession2 = sessionFactory.createSession(true, false, true);
      ClientProducer clientProducer2 = clientSession2.createProducer(atestq);
      clientSession2.start(xid, XAResource.TMNOFLAGS);
      clientProducer2.send(m1);
      clientProducer2.send(m2);
      clientProducer2.send(m3);
      clientProducer2.send(m4);
      clientSession2.end(xid, XAResource.TMSUCCESS);
      clientSession2.close();
      clientSession.start(xid, XAResource.TMJOIN);
      clientProducer.send(m5);
      clientProducer.send(m6);
      clientProducer.send(m7);
      clientProducer.send(m8);
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      if (stopServer) {
         stopAndRestartServer();
      } else {
         recreateClients();
      }

      Xid[] xids = clientSession.recover(XAResource.TMSTARTRSCAN);

      assertEquals(1, xids.length);
      assertEquals(xids[0].getFormatId(), xid.getFormatId());
      ActiveMQTestBase.assertEqualsByteArrays(xids[0].getBranchQualifier(), xid.getBranchQualifier());
      ActiveMQTestBase.assertEqualsByteArrays(xids[0].getGlobalTransactionId(), xid.getGlobalTransactionId());
      xids = clientSession.recover(XAResource.TMENDRSCAN);
      assertEquals(0, xids.length);
      clientSession.commit(xid, false);
      clientSession.start();
      ClientMessage m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m2", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m3", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m4", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m5", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m6", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m7", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m8", m.getBodyBuffer().readString());
   }

   public void testBasicReceiveWithCommit(final boolean stopServer) throws Exception {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      ClientMessage m1 = createTextMessage("m1");
      ClientMessage m2 = createTextMessage("m2");
      ClientMessage m3 = createTextMessage("m3");
      ClientMessage m4 = createTextMessage("m4");
      ClientSession clientSession2 = sessionFactory.createSession(false, true, true);
      ClientProducer clientProducer2 = clientSession2.createProducer(atestq);
      clientProducer2.send(m1);
      clientProducer2.send(m2);
      clientProducer2.send(m3);
      clientProducer2.send(m4);
      clientSession2.close();
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientSession.start();
      ClientMessage m = clientConsumer.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m2", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m3", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m4", m.getBodyBuffer().readString());
      clientSession.end(xid, XAResource.TMSUCCESS);
      assertEquals(XAResource.XA_OK, clientSession.prepare(xid), "Expected XA_OK");

      if (stopServer) {
         stopAndRestartServer();
      } else {
         recreateClients();
      }

      Xid[] xids = clientSession.recover(XAResource.TMSTARTRSCAN);

      assertEquals(1, xids.length);
      assertEquals(xids[0].getFormatId(), xid.getFormatId());
      ActiveMQTestBase.assertEqualsByteArrays(xids[0].getBranchQualifier(), xid.getBranchQualifier());
      ActiveMQTestBase.assertEqualsByteArrays(xids[0].getGlobalTransactionId(), xid.getGlobalTransactionId());
      xids = clientSession.recover(XAResource.TMENDRSCAN);
      assertEquals(0, xids.length);
      clientSession.commit(xid, false);
      clientSession.start();
      m = clientConsumer.receiveImmediate();
      assertNull(m);

      //check deliveringCount Zero
      checkQueueDeliveryCount(atestq, 0);
   }

   private void checkQueueDeliveryCount(SimpleString thequeue, int expectedCount) throws Exception {
      QueueControl queueControl = ManagementControlHelper.createQueueControl(thequeue, thequeue, mbeanServer);

      int actualCount = queueControl.getDeliveringCount();

      assertEquals(expectedCount, actualCount);
   }

   public void testBasicReceiveWithRollback(final boolean stopServer) throws Exception {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      ClientMessage m1 = createTextMessage("m1");
      ClientMessage m2 = createTextMessage("m2");
      ClientMessage m3 = createTextMessage("m3");
      ClientMessage m4 = createTextMessage("m4");
      ClientSession clientSession2 = sessionFactory.createSession(false, true, true);
      ClientProducer clientProducer2 = clientSession2.createProducer(atestq);
      clientProducer2.send(m1);
      clientProducer2.send(m2);
      clientProducer2.send(m3);
      clientProducer2.send(m4);
      clientSession2.close();
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientSession.start();
      ClientMessage m = clientConsumer.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m2", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m3", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m4", m.getBodyBuffer().readString());
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      logger.debug("stopping and restarting");

      if (stopServer) {
         stopAndRestartServer();
      } else {
         recreateClients();
      }

      logger.debug("Restarted");

      Xid[] xids = clientSession.recover(XAResource.TMSTARTRSCAN);

      assertEquals(1, xids.length);
      assertEquals(xids[0].getFormatId(), xid.getFormatId());
      ActiveMQTestBase.assertEqualsByteArrays(xids[0].getBranchQualifier(), xid.getBranchQualifier());
      ActiveMQTestBase.assertEqualsByteArrays(xids[0].getGlobalTransactionId(), xid.getGlobalTransactionId());
      xids = clientSession.recover(XAResource.TMENDRSCAN);
      assertEquals(0, xids.length);
      clientSession.rollback(xid);
      clientSession.start();
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m2", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m3", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      assertEquals("m4", m.getBodyBuffer().readString());
   }

   public void testMultipleTxReceiveWithCommit(final boolean stopServer) throws Exception {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      Xid xid2 = new XidImpl("xa2".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      ClientMessage m1 = createTextMessage("m1");
      ClientMessage m2 = createTextMessage("m2");
      ClientMessage m3 = createTextMessage("m3");
      ClientMessage m4 = createTextMessage("m4");
      ClientMessage m5 = createTextMessage("m5");
      ClientMessage m6 = createTextMessage("m6");
      ClientMessage m7 = createTextMessage("m7");
      ClientMessage m8 = createTextMessage("m8");
      ClientSession clientSession2 = sessionFactory.createSession(false, true, true);
      ClientProducer clientProducer2 = clientSession2.createProducer(atestq);
      SimpleString anewtestq = SimpleString.of("anewtestq");
      clientSession.createQueue(QueueConfiguration.of(anewtestq));
      ClientProducer clientProducer3 = clientSession2.createProducer(anewtestq);
      clientProducer2.send(m1);
      clientProducer2.send(m2);
      clientProducer2.send(m3);
      clientProducer2.send(m4);
      clientProducer3.send(m5);
      clientProducer3.send(m6);
      clientProducer3.send(m7);
      clientProducer3.send(m8);
      clientSession2.close();
      clientSession2 = sessionFactory.createSession(true, false, false);
      ClientConsumer clientConsumer2 = clientSession2.createConsumer(anewtestq);
      clientSession2.start(xid2, XAResource.TMNOFLAGS);
      clientSession2.start();
      ClientMessage m = clientConsumer2.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m5", m.getBodyBuffer().readString());
      m = clientConsumer2.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m6", m.getBodyBuffer().readString());
      m = clientConsumer2.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m7", m.getBodyBuffer().readString());
      m = clientConsumer2.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m8", m.getBodyBuffer().readString());
      clientSession2.end(xid2, XAResource.TMSUCCESS);
      clientSession2.prepare(xid2);
      clientSession2.close();
      clientSession2 = null;
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientSession.start();
      m = clientConsumer.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m2", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m3", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m4", m.getBodyBuffer().readString());
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      if (stopServer) {
         stopAndRestartServer();
      } else {
         recreateClients();
      }

      Xid[] xids = clientSession.recover(XAResource.TMSTARTRSCAN);
      assertEqualXids(xids, xid, xid2);
      xids = clientSession.recover(XAResource.TMENDRSCAN);
      assertEquals(0, xids.length);
      clientSession.commit(xid, false);
      clientSession.start();
      m = clientConsumer.receiveImmediate();
      assertNull(m);
   }

   public void testMultipleTxReceiveWithRollback(final boolean stopServer) throws Exception {
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      Xid xid2 = new XidImpl("xa2".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      ClientMessage m1 = createTextMessage("m1");
      ClientMessage m2 = createTextMessage("m2");
      ClientMessage m3 = createTextMessage("m3");
      ClientMessage m4 = createTextMessage("m4");
      ClientMessage m5 = createTextMessage("m5");
      ClientMessage m6 = createTextMessage("m6");
      ClientMessage m7 = createTextMessage("m7");
      ClientMessage m8 = createTextMessage("m8");
      ClientSession clientSession2 = sessionFactory.createSession(false, true, true);
      ClientProducer clientProducer2 = clientSession2.createProducer(atestq);
      SimpleString anewtestq = SimpleString.of("anewtestq");
      clientSession.createQueue(QueueConfiguration.of(anewtestq));
      ClientProducer clientProducer3 = clientSession2.createProducer(anewtestq);
      clientProducer2.send(m1);
      clientProducer2.send(m2);
      clientProducer2.send(m3);
      clientProducer2.send(m4);
      clientProducer3.send(m5);
      clientProducer3.send(m6);
      clientProducer3.send(m7);
      clientProducer3.send(m8);
      clientSession2.close();
      clientSession2 = sessionFactory.createSession(true, false, false);
      ClientConsumer clientConsumer2 = clientSession2.createConsumer(anewtestq);
      clientSession2.start(xid2, XAResource.TMNOFLAGS);
      clientSession2.start();
      ClientMessage m = clientConsumer2.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m5", m.getBodyBuffer().readString());
      m = clientConsumer2.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m6", m.getBodyBuffer().readString());
      m = clientConsumer2.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m7", m.getBodyBuffer().readString());
      m = clientConsumer2.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m8", m.getBodyBuffer().readString());
      clientSession2.end(xid2, XAResource.TMSUCCESS);
      clientSession2.prepare(xid2);
      clientSession2.close();
      clientSession2 = null;
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientSession.start();
      m = clientConsumer.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m2", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m3", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m4", m.getBodyBuffer().readString());
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      if (stopServer) {
         stopAndRestartServer();
      } else {
         recreateClients();
      }

      Xid[] xids = clientSession.recover(XAResource.TMSTARTRSCAN);
      assertEqualXids(xids, xid, xid2);
      xids = clientSession.recover(XAResource.TMENDRSCAN);
      assertEquals(0, xids.length);
      clientSession.rollback(xid);
      clientSession.start();
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m1", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m2", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m3", m.getBodyBuffer().readString());
      m = clientConsumer.receive(1000);
      m.acknowledge();
      assertNotNull(m);
      assertEquals("m4", m.getBodyBuffer().readString());
   }

   protected void stopAndRestartServer() throws Exception {
      // now stop and start the server
      clientSession.close();
      clientSession = null;
      server.stop();
      server = null;
      server = createServer(true, configuration, -1, -1, addressSettings);
      server.setMBeanServer(mbeanServer);

      server.start();
      createClients();
   }

   private void addSettings() {
      for (Map.Entry<String, AddressSettings> setting : addressSettings.entrySet()) {
         server.getAddressSettingsRepository().addMatch(setting.getKey(), setting.getValue());
      }
   }

   protected void recreateClients() throws Exception {
      clientSession.close();
      clientSession = null;
      createClients();
   }

   private ClientMessage createTextMessage(final String s) {
      return createTextMessage(s, true);
   }

   private ClientMessage createTextMessage(final String s, final boolean durable) {
      ClientMessage message = clientSession.createMessage(ActiveMQTextMessage.TYPE, durable, 0, System.currentTimeMillis(), (byte) 1);
      message.getBodyBuffer().writeString(s);
      return message;
   }

   private ClientMessage createBytesMessage(final byte[] b, final boolean durable) {
      ClientMessage message = clientSession.createMessage(ActiveMQBytesMessage.TYPE, durable, 0, System.currentTimeMillis(), (byte) 1);
      message.getBodyBuffer().writeBytes(b);
      return message;
   }

   private void createClients() throws Exception {
      createClients(false, true);
   }

   private void createClients(final boolean createQueue, final boolean commitACKs) throws Exception {
      locator = createInVMNonHALocator();
      sessionFactory = createSessionFactory(locator);
      clientSession = sessionFactory.createSession(true, false, commitACKs);
      if (createQueue) {
         clientSession.createQueue(QueueConfiguration.of(atestq));
      }
      clientProducer = clientSession.createProducer(atestq);
      clientConsumer = clientSession.createConsumer(atestq);
   }

   private void assertEqualXids(final Xid[] xids, final Xid... origXids) {
      assertEquals(xids.length, origXids.length);
      for (Xid xid : xids) {
         boolean found = false;
         for (Xid origXid : origXids) {
            found = Arrays.equals(origXid.getBranchQualifier(), xid.getBranchQualifier());
            if (found) {
               assertEquals(xid.getFormatId(), origXid.getFormatId());
               ActiveMQTestBase.assertEqualsByteArrays(xid.getBranchQualifier(), origXid.getBranchQualifier());
               ActiveMQTestBase.assertEqualsByteArrays(xid.getGlobalTransactionId(), origXid.getGlobalTransactionId());
               break;
            }
         }
         if (!found) {
            fail("correct xid not found: " + xid);
         }
      }
   }
}

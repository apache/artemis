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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.management.MBeanServer;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.transaction.impl.XidImpl;
import org.apache.activemq.artemis.tests.integration.management.ManagementControlHelper;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HeuristicXATest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   final SimpleString ADDRESS = SimpleString.of("ADDRESS");

   final String body = "this is the body";

   private MBeanServer mbeanServer;

   private ServerLocator locator;


   @Test
   public void testInvalidCall() throws Exception {
      Configuration configuration = createDefaultInVMConfig().setJMXManagementEnabled(true);

      ActiveMQServer server = createServer(false, configuration);
      server.setMBeanServer(mbeanServer);
      server.start();

      ActiveMQServerControl jmxServer = ManagementControlHelper.createActiveMQServerControl(mbeanServer);

      assertFalse(jmxServer.commitPreparedTransaction("Nananananana"));
   }

   @Test
   public void testHeuristicCommit() throws Exception {
      internalTest(true);
   }

   @Test
   public void testHeuristicRollback() throws Exception {
      internalTest(false);
   }

   private void internalTest(final boolean isCommit) throws Exception {
      Configuration configuration = createDefaultInVMConfig().setJMXManagementEnabled(true);

      ActiveMQServer server = createServer(false, configuration);
      server.setMBeanServer(mbeanServer);
      server.start();
      Xid xid = newXID();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(true, false, false);

      session.createQueue(QueueConfiguration.of(ADDRESS));

      session.start(xid, XAResource.TMNOFLAGS);

      ClientProducer producer = session.createProducer(ADDRESS);

      ClientMessage msg = session.createMessage(true);

      msg.getBodyBuffer().writeString(body);

      producer.send(msg);

      session.end(xid, XAResource.TMSUCCESS);

      session.prepare(xid);

      session.close();

      ActiveMQServerControl jmxServer = ManagementControlHelper.createActiveMQServerControl(mbeanServer);

      String[] preparedTransactions = jmxServer.listPreparedTransactions();

      assertEquals(1, preparedTransactions.length);

      logger.debug(preparedTransactions[0]);

      assertEquals(0, jmxServer.listHeuristicCommittedTransactions().length);
      assertEquals(0, jmxServer.listHeuristicRolledBackTransactions().length);

      if (isCommit) {
         jmxServer.commitPreparedTransaction(XidImpl.toBase64String(xid));
      } else {
         jmxServer.rollbackPreparedTransaction(XidImpl.toBase64String(xid));
      }

      assertEquals(0, jmxServer.listPreparedTransactions().length);
      if (isCommit) {
         assertEquals(1, jmxServer.listHeuristicCommittedTransactions().length);
         assertEquals(0, jmxServer.listHeuristicRolledBackTransactions().length);
      } else {
         assertEquals(0, jmxServer.listHeuristicCommittedTransactions().length);
         assertEquals(1, jmxServer.listHeuristicRolledBackTransactions().length);
      }

      if (isCommit) {
         assertMessageInQueueThenReceiveAndCheckContent(server, sf);
      }

      assertEquals(0, getMessageCount(((Queue) server.getPostOffice().getBinding(ADDRESS).getBindable())));
   }

   @Test
   public void testHeuristicCommitWithRestart() throws Exception {
      doHeuristicCompletionWithRestart(true);
   }

   @Test
   public void testHeuristicRollbackWithRestart() throws Exception {
      doHeuristicCompletionWithRestart(false);
   }

   private void doHeuristicCompletionWithRestart(final boolean isCommit) throws Exception {
      Configuration configuration = createDefaultInVMConfig().setJMXManagementEnabled(true);

      ActiveMQServer server = createServer(true, configuration);
      server.setMBeanServer(mbeanServer);
      server.start();
      Xid xid = newXID();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(true, false, false);

      session.createQueue(QueueConfiguration.of(ADDRESS));

      session.start(xid, XAResource.TMNOFLAGS);

      ClientProducer producer = session.createProducer(ADDRESS);

      ClientMessage msg = session.createMessage(true);

      msg.getBodyBuffer().writeString(body);

      producer.send(msg);

      session.end(xid, XAResource.TMSUCCESS);

      session.prepare(xid);

      session.close();

      ActiveMQServerControl jmxServer = ManagementControlHelper.createActiveMQServerControl(mbeanServer);

      String[] preparedTransactions = jmxServer.listPreparedTransactions();

      assertEquals(1, preparedTransactions.length);
      logger.debug(preparedTransactions[0]);

      if (isCommit) {
         jmxServer.commitPreparedTransaction(XidImpl.toBase64String(xid));
      } else {
         jmxServer.rollbackPreparedTransaction(XidImpl.toBase64String(xid));
      }

      preparedTransactions = jmxServer.listPreparedTransactions();
      assertEquals(0, preparedTransactions.length);

      if (isCommit) {
         assertMessageInQueueThenReceiveAndCheckContent(server, sf);
      }

      assertEquals(0, getMessageCount(((Queue) server.getPostOffice().getBinding(ADDRESS).getBindable())));

      server.stop();

      server.start();

      jmxServer = ManagementControlHelper.createActiveMQServerControl(mbeanServer);
      if (isCommit) {
         String[] listHeuristicCommittedTransactions = jmxServer.listHeuristicCommittedTransactions();
         assertEquals(1, listHeuristicCommittedTransactions.length);
         logger.debug(listHeuristicCommittedTransactions[0]);
      } else {
         String[] listHeuristicRolledBackTransactions = jmxServer.listHeuristicRolledBackTransactions();
         assertEquals(1, listHeuristicRolledBackTransactions.length);
         logger.debug(listHeuristicRolledBackTransactions[0]);
      }
   }

   @Test
   public void testRecoverHeuristicCommitWithRestart() throws Exception {
      doRecoverHeuristicCompletedTxWithRestart(true);
   }

   @Test
   public void testRecoverHeuristicRollbackWithRestart() throws Exception {
      doRecoverHeuristicCompletedTxWithRestart(false);
   }

   private void doRecoverHeuristicCompletedTxWithRestart(final boolean heuristicCommit) throws Exception {
      Configuration configuration = createDefaultInVMConfig().setJMXManagementEnabled(true);

      ActiveMQServer server = createServer(true, configuration);
      server.setMBeanServer(mbeanServer);
      server.start();
      Xid xid = newXID();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(true, false, false);

      session.createQueue(QueueConfiguration.of(ADDRESS));

      session.start(xid, XAResource.TMNOFLAGS);

      ClientProducer producer = session.createProducer(ADDRESS);

      ClientMessage msg = session.createMessage(true);

      msg.getBodyBuffer().writeString(body);

      producer.send(msg);

      session.end(xid, XAResource.TMSUCCESS);

      session.prepare(xid);

      session.close();

      ActiveMQServerControl jmxServer = ManagementControlHelper.createActiveMQServerControl(mbeanServer);

      String[] preparedTransactions = jmxServer.listPreparedTransactions();

      assertEquals(1, preparedTransactions.length);
      logger.debug(preparedTransactions[0]);

      if (heuristicCommit) {
         jmxServer.commitPreparedTransaction(XidImpl.toBase64String(xid));
      } else {
         jmxServer.rollbackPreparedTransaction(XidImpl.toBase64String(xid));
      }

      preparedTransactions = jmxServer.listPreparedTransactions();
      assertEquals(0, preparedTransactions.length);

      if (heuristicCommit) {
         assertMessageInQueueThenReceiveAndCheckContent(server, sf);
      }

      assertEquals(0, getMessageCount(((Queue) server.getPostOffice().getBinding(ADDRESS).getBindable())));

      server.stop();

      server.start();
      // we need to recreate the locator and session sf
      sf = createSessionFactory(locator);
      jmxServer = ManagementControlHelper.createActiveMQServerControl(mbeanServer);
      if (heuristicCommit) {
         String[] listHeuristicCommittedTransactions = jmxServer.listHeuristicCommittedTransactions();
         assertEquals(1, listHeuristicCommittedTransactions.length);
         logger.debug(listHeuristicCommittedTransactions[0]);
      } else {
         String[] listHeuristicRolledBackTransactions = jmxServer.listHeuristicRolledBackTransactions();
         assertEquals(1, listHeuristicRolledBackTransactions.length);
         logger.debug(listHeuristicRolledBackTransactions[0]);
      }

      session = sf.createSession(true, false, false);
      Xid[] recoveredXids = session.recover(XAResource.TMSTARTRSCAN);
      assertEquals(1, recoveredXids.length);
      assertEquals(xid, recoveredXids[0]);
      assertEquals(0, session.recover(XAResource.TMENDRSCAN).length);

      session.close();
   }

   private void assertMessageInQueueThenReceiveAndCheckContent(ActiveMQServer server, ClientSessionFactory sf) throws Exception {
      Wait.assertEquals(1, () -> getMessageCount(((Queue) server.getPostOffice().getBinding(ADDRESS).getBindable())), 5 * 1000, 100);

      ClientSession session = sf.createSession(false, false, false);

      session.start();
      ClientConsumer consumer = session.createConsumer(ADDRESS);
      ClientMessage msg = consumer.receive(1000);
      assertNotNull(msg);
      msg.acknowledge();
      assertEquals(body, msg.getBodyBuffer().readString());

      session.commit();
      session.close();
   }

   @Test
   public void testForgetHeuristicCommitAndRestart() throws Exception {
      doForgetHeuristicCompletedTxAndRestart(true);
   }

   @Test
   public void testForgetHeuristicRollbackAndRestart() throws Exception {
      doForgetHeuristicCompletedTxAndRestart(false);
   }

   private void doForgetHeuristicCompletedTxAndRestart(final boolean heuristicCommit) throws Exception {
      Configuration configuration = createDefaultInVMConfig().setJMXManagementEnabled(true);

      ActiveMQServer server = createServer(true, configuration);
      server.setMBeanServer(mbeanServer);
      server.start();
      Xid xid = newXID();

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(true, false, false);

      session.createQueue(QueueConfiguration.of(ADDRESS));

      session.start(xid, XAResource.TMNOFLAGS);

      ClientProducer producer = session.createProducer(ADDRESS);

      ClientMessage msg = session.createMessage(true);

      msg.getBodyBuffer().writeBytes(new byte[123]);

      producer.send(msg);

      session.end(xid, XAResource.TMSUCCESS);

      session.prepare(xid);

      ActiveMQServerControl jmxServer = ManagementControlHelper.createActiveMQServerControl(mbeanServer);

      String[] preparedTransactions = jmxServer.listPreparedTransactions();

      assertEquals(1, preparedTransactions.length);
      logger.debug(preparedTransactions[0]);

      if (heuristicCommit) {
         jmxServer.commitPreparedTransaction(XidImpl.toBase64String(xid));
      } else {
         jmxServer.rollbackPreparedTransaction(XidImpl.toBase64String(xid));
      }

      preparedTransactions = jmxServer.listPreparedTransactions();
      assertEquals(0, preparedTransactions.length);

      session.forget(xid);

      session.close();

      if (heuristicCommit) {
         assertEquals(0, jmxServer.listHeuristicCommittedTransactions().length);
      } else {
         assertEquals(0, jmxServer.listHeuristicRolledBackTransactions().length);
      }

      server.stop();

      server.start();
      // we need to recreate the sf
      sf = createSessionFactory(locator);
      session = sf.createSession(true, false, false);
      Xid[] recoveredXids = session.recover(XAResource.TMSTARTRSCAN);
      assertEquals(0, recoveredXids.length);
      jmxServer = ManagementControlHelper.createActiveMQServerControl(mbeanServer);
      if (heuristicCommit) {
         assertEquals(0, jmxServer.listHeuristicCommittedTransactions().length);
      } else {
         assertEquals(0, jmxServer.listHeuristicRolledBackTransactions().length);
      }

      session.close();
   }



   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      mbeanServer = createMBeanServer();
      locator = createInVMNonHALocator();
   }



}

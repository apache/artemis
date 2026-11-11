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
package org.apache.activemq.artemis.tests.integration.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.transaction.impl.XidImpl;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LVQRecoveryTest extends ActiveMQTestBase {

   private ActiveMQServer server;

   private ClientSession clientSession;

   private final SimpleString address = SimpleString.of("LVQTestAddress");

   private final SimpleString qName1 = SimpleString.of("LVQTestQ1");

   private ClientSession clientSessionXa;

   private Configuration configuration;

   private AddressSettings qs;
   private ServerLocator locator;

   @Test
   public void testMultipleMessagesAfterRecovery() throws Exception {
      Xid xid = new XidImpl("bq1".getBytes(), 4, "gtid1".getBytes());
      ClientProducer producer = clientSessionXa.createProducer(address);
      SimpleString messageId1 = SimpleString.of("SMID1");
      SimpleString messageId2 = SimpleString.of("SMID2");
      clientSessionXa.start(xid, XAResource.TMNOFLAGS);
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, messageId1);
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, messageId2);
      ClientMessage m3 = createTextMessage(clientSession, "m3");
      m3.putStringProperty(Message.HDR_LAST_VALUE_NAME, messageId1);
      ClientMessage m4 = createTextMessage(clientSession, "m4");
      m4.putStringProperty(Message.HDR_LAST_VALUE_NAME, messageId2);
      producer.send(m1);
      producer.send(m2);
      producer.send(m3);
      producer.send(m4);
      clientSessionXa.end(xid, XAResource.TMSUCCESS);
      clientSessionXa.prepare(xid);

      clientSession.close();
      clientSessionXa.close();
      restartServer();

      clientSessionXa.commit(xid, false);
      ClientConsumer consumer = clientSession.createConsumer(qName1);
      clientSession.start();
      ClientMessage m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m3", m.getBodyBuffer().readString());
      m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m4", m.getBodyBuffer().readString());
   }

   @Test
   public void testManyMessagesReceivedWithRollback() throws Exception {
      Xid xid = new XidImpl("bq1".getBytes(), 4, "gtid1".getBytes());
      ClientProducer producer = clientSession.createProducer(address);
      ClientConsumer consumer = clientSessionXa.createConsumer(qName1);

      SimpleString rh = SimpleString.of("SMID1");
      ClientMessage m1 = createTextMessage(clientSession, "m1");
      m1.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m1.setDurable(true);
      ClientMessage m2 = createTextMessage(clientSession, "m2");
      m2.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m2.setDurable(true);
      ClientMessage m3 = createTextMessage(clientSession, "m3");
      m3.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m3.setDurable(true);
      ClientMessage m4 = createTextMessage(clientSession, "m4");
      m4.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m4.setDurable(true);
      ClientMessage m5 = createTextMessage(clientSession, "m5");
      m5.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m5.setDurable(true);
      ClientMessage m6 = createTextMessage(clientSession, "m6");
      m6.putStringProperty(Message.HDR_LAST_VALUE_NAME, rh);
      m6.setDurable(true);
      clientSessionXa.start(xid, XAResource.TMNOFLAGS);
      clientSessionXa.start();
      producer.send(m1);
      ClientMessage m = consumer.receive(1000);
      assertNotNull(m);
      assertEquals("m1", m.getBodyBuffer().readString());
      producer.send(m2);
      m = consumer.receive(1000);
      assertNotNull(m);
      assertEquals("m2", m.getBodyBuffer().readString());
      producer.send(m3);
      m = consumer.receive(1000);
      assertNotNull(m);
      assertEquals("m3", m.getBodyBuffer().readString());
      producer.send(m4);
      m = consumer.receive(1000);
      assertNotNull(m);
      assertEquals("m4", m.getBodyBuffer().readString());
      producer.send(m5);
      m = consumer.receive(1000);
      assertNotNull(m);
      assertEquals("m5", m.getBodyBuffer().readString());
      producer.send(m6);
      m = consumer.receive(1000);
      assertNotNull(m);
      assertEquals("m6", m.getBodyBuffer().readString());
      clientSessionXa.end(xid, XAResource.TMSUCCESS);
      clientSessionXa.prepare(xid);

      clientSession.close();
      clientSessionXa.close();
      restartServer();

      clientSessionXa.rollback(xid);
      consumer = clientSession.createConsumer(qName1);
      clientSession.start();
      m = consumer.receive(1000);
      assertNotNull(m);
      m.acknowledge();
      assertEquals("m6", m.getBodyBuffer().readString());
      m = consumer.receiveImmediate();
      assertNull(m);
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      configuration = createDefaultInVMConfig();
      server = createServer(true, configuration);
      server.start();

      qs = new AddressSettings().setDefaultLastValueQueue(true);
      server.getAddressSettingsRepository().addMatch(address.toString(), qs);
      // then we create a client as normal
      locator = createInVMNonHALocator().setBlockOnAcknowledge(true).setAckBatchSize(0);
      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      clientSession = sessionFactory.createSession(false, true, true);
      clientSessionXa = sessionFactory.createSession(true, false, false);
      clientSession.createQueue(QueueConfiguration.of(qName1).setAddress(address));
   }

   private void restartServer() throws Exception {
      server.stop();
      server = null;
      server = createServer(true, configuration);
      server.getAddressSettingsRepository().addMatch(address.toString(), qs);
      // start the server
      server.start();

      server.getAddressSettingsRepository().addMatch(address.toString(), new AddressSettings().setDefaultLastValueQueue(true));
      // then we create a client as normal
      locator.close();
      locator = createInVMNonHALocator().setBlockOnAcknowledge(true).setAckBatchSize(0);

      ClientSessionFactory sessionFactory = createSessionFactory(locator);
      clientSession = sessionFactory.createSession(false, true, true);
      clientSessionXa = sessionFactory.createSession(true, false, false);
   }
}

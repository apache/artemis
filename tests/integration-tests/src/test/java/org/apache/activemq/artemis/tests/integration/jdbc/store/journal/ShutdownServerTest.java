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
package org.apache.activemq.artemis.tests.integration.jdbc.store.journal;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jdbc.store.journal.JDBCJournalImpl;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ShutdownServerTest extends ActiveMQTestBase {

   private ActiveMQServer server;

   private final SimpleString QUEUE = SimpleString.of("ConsumerTestQueue");

   private ServerLocator locator;

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();

      server = createServer(true, createDefaultJDBCConfig(false), AddressSettings.DEFAULT_PAGE_SIZE, AddressSettings.DEFAULT_MAX_SIZE_BYTES);
      server.start();

      locator = createFactory(false);
   }

   @Test
   public void testShutdownServer() throws Throwable {
      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, true, true, false);

      session.createQueue(QueueConfiguration.of(QUEUE));

      ClientConsumer consumer = session.createConsumer(QUEUE);

      ClientProducer producer = session.createProducer(QUEUE);
      ClientMessage message = session.createMessage(Message.TEXT_TYPE, true, 0, System.currentTimeMillis(), (byte) 4);
      message.getBodyBuffer().writeString("hi");
      message.putStringProperty("hello", "elo");
      producer.send(message);

      ActiveMQServerImpl impl = (ActiveMQServerImpl) server;
      JournalStorageManager journal = (JournalStorageManager) impl.getStorageManager();
      JDBCJournalImpl journalimpl = (JDBCJournalImpl) journal.getMessageJournal();
      journalimpl.handleException(null, new Exception("failure"));

      Wait.waitFor(() -> !server.isStarted());

      assertFalse(server.isStarted());

   }

}

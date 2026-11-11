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

package org.apache.activemq.artemis.tests.integration.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.cli.commands.tools.RecoverMessages;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.nativo.jlibaio.LibaioContext;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.JMSTestBase;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class RecoverTest extends JMSTestBase {

   boolean useTX;
   String protocol;
   boolean paging;
   boolean large;
   String journalType;

   public RecoverTest(boolean useTX, String protocol, boolean paging, boolean large, String journalType) {
      this.useTX = useTX;
      this.protocol = protocol;
      this.paging = paging;
      this.large = large;
      this.journalType = journalType;
   }

   @Parameters(name = "useTX={0}, protocol={1}, paging={2}, largeMessage={3}, journal-type={4}")
   public static Collection<Object[]> data() {
      Object[] journalType;
      if (LibaioContext.isLoaded()) {
         journalType = new Object[]{"AIO", "NIO", "MAPPED"};
      } else {
         journalType = new Object[]{"NIO", "MAPPED"};
      }
      return combine(new Object[]{true, false}, new Object[]{"AMQP", "CORE", "OPENWIRE"}, new Object[]{true, false}, new Object[]{true, false}, journalType);
   }

   protected static Collection<Object[]> combine(Object[] one, Object[] two, Object[] three, Object[] four, Object[] five) {
      List<Object[]> combinations = new ArrayList<>();
      for (Object o1 : one) {
         for (Object o2 : two) {
            for (Object o3 : three) {
               for (Object o4 : four) {
                  for (Object o5 : five) {
                     combinations.add(new Object[]{o1, o2, o3, o4, o5});
                  }
               }
            }
         }
      }

      return combinations;
   }

   @Override
   protected Configuration createDefaultConfig(boolean netty) throws Exception {
      Configuration configuration = super.createDefaultConfig(netty).setJMXManagementEnabled(true);
      configuration.setJournalRetentionDirectory(getTestDir() + "/historyJournal");
      switch (journalType) {
         case "NIO":
            configuration.setJournalType(JournalType.NIO);
            break;
         case "MAPPED":
            configuration.setJournalType(JournalType.MAPPED);
            break;
         case "AIO":
            configuration.setJournalType(JournalType.ASYNCIO);
            break;
      }
      return configuration;
   }

   @Override
   protected boolean usePersistence() {
      return true;
   }

   @TestTemplate
   public void testRecover() throws Exception {

      createQueue(true, "TestQueue");
      org.apache.activemq.artemis.core.server.Queue serverQueue = server.locateQueue("TestQueue");
      if (paging) {
         serverQueue.getPagingStore().startPaging();
      }
      ConnectionFactory factory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");
      Connection connection = factory.createConnection();
      addConnection(connection);
      Session session = connection.createSession(useTX, useTX ? Session.SESSION_TRANSACTED : Session.AUTO_ACKNOWLEDGE);
      Queue queue = session.createQueue("TestQueue");
      MessageProducer producer = session.createProducer(queue);
      String messageBody = large ? " ".repeat(110 * 1024) : "hello";
      int maxMessage = large ? 10 : 1000;
      for (int i = 0; i < maxMessage; i++) {
         producer.send(session.createTextMessage(i + messageBody));
      }

      if (useTX) {
         session.commit();
      }

      // Using compacting here, will kind of duplicate all the records into reclaimed files
      // this might cause extra challenges on recovering the data
      server.getStorageManager().getMessageJournal().scheduleCompactAndBlock(60_000);

      MessageConsumer consumer = session.createConsumer(queue);
      connection.start();

      for (int i = 0; i < maxMessage; i++) {
         TextMessage message = (TextMessage) consumer.receive(5000);
         assertNotNull(message);
         if (!protocol.equals("OPENWIRE")) {
            // openwire won't support large message or its conversions
            assertEquals(i + messageBody, message.getText());
         }
      }

      if (useTX) {
         session.commit();
      }

      connection.close();

      // need to wait no paging, otherwise an eventual page cleanup would remove large message bodies from the recovery
      Wait.assertFalse(serverQueue.getPagingStore()::isPaging);

      server.stop();

      File newJournalLocation = new File(server.getConfiguration().getJournalLocation().getParentFile(), "recovered");

      RecoverMessages.recover(new ActionContext(), server.getConfiguration(), server.getConfiguration().getJournalRetentionDirectory(), newJournalLocation, server.getConfiguration().getLargeMessagesLocation(), false);

      if (large) {
         File[] largeMessageFiles = server.getConfiguration().getLargeMessagesLocation().listFiles();
         assertEquals(maxMessage, largeMessageFiles.length);
         for (File f : largeMessageFiles) {
            assertTrue(f.length() > 0, "File length was " + f.length());
         }
      }

      server.getConfiguration().setJournalDirectory(newJournalLocation.getAbsolutePath());

      server.start();

      factory = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");
      connection = factory.createConnection();
      addConnection(connection);
      session = connection.createSession(useTX, useTX ? Session.SESSION_TRANSACTED : Session.AUTO_ACKNOWLEDGE);

      connection.start();

      consumer = session.createConsumer(queue);

      for (int i = 0; i < maxMessage; i++) {
         TextMessage message = (TextMessage) consumer.receive(5000);
         assertNotNull(message);
         if (!protocol.equals("OPENWIRE")) {
            // openwire won't support large message or its conversions
            assertEquals(i + messageBody, message.getText());
         }
      }

      assertNull(consumer.receiveNoWait());

      if (useTX) {
         session.commit();
      }

      connection.close();
   }

}

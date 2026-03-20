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

package org.apache.activemq.artemis.tests.db.paging;

import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.cli.commands.tools.PrintData;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.StoreConfiguration;
import org.apache.activemq.artemis.core.config.storage.DatabaseStorageConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.tests.db.common.Database;
import org.apache.activemq.artemis.tests.db.common.ParameterDBTestBase;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class PrintDataTest extends ParameterDBTestBase {

   @Parameters(name = "db={0}")
   public static Collection<Object[]> parameters() {
      return convertParameters(Database.selectedList());
   }

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();
   }

   private void testData(boolean newDatabase) throws Exception {
      Configuration configuration = createDefaultConfig(0, true);
      if (newDatabase) {
         DatabaseStorageConfiguration dbConf = (DatabaseStorageConfiguration) configuration.getStoreConfiguration();
         dbConf.setStoreType(StoreConfiguration.StoreType.NEW_DATABASE);
      }

      ActiveMQServer server = createServer(configuration);
      server.start();

      String queueName = RandomUtil.randomUUIDString();
      server.addAddressInfo(new AddressInfo(queueName).addRoutingType(RoutingType.ANYCAST));
      Queue queue = server.createQueue(QueueConfiguration.of(queueName).setAddress(queueName).setDurable(true).setRoutingType(RoutingType.ANYCAST));
      queue.getPagingStore().startPaging();

      int numberOfMessages = 10;

      ConnectionFactory cf = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
      try (Connection connection = cf.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createQueue(queueName));

         for (int i = 0; i < numberOfMessages; i++) {
            TextMessage message = session.createTextMessage("message " + i);
            message.setStringProperty("i", "message " + i);
            producer.send(message);
         }
         session.commit();
      }
      server.stop();

      PrintData printData = new PrintData().setAscii(true);

      ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
      PrintStream printStream = new PrintStream(byteArrayOutputStream, true, StandardCharsets.UTF_8.name());

      printData.printData(server.getConfiguration(), printStream);

      String printDataOutput = byteArrayOutputStream.toString();

      for (int i = 0; i < numberOfMessages; i++) {
         assertTrue(printDataOutput.lastIndexOf("message " + i) >= 0);
      }

      if (newDatabase) {
         assertTrue(printDataOutput.contains("A D D R E S S E S"), "New database format should contain ADDRESSES section");
      } else {
         // I know this is a bit fragile, but the queues routed portion of the report was not working.
         // if the report ever changes, so the test will need to be changed.
         assertTrue(printDataOutput.contains("queues routed"), "Old JDBC format should contain queues routed");
      }
   }

   @TestTemplate
   public void testData() throws Exception {
      testData(false);
   }

   @TestTemplate
   public void testDataNewDatabase() throws Exception {
      testData(true);
   }

}

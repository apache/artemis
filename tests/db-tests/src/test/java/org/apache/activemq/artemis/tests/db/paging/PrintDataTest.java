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

   ActiveMQServer server;

   @Parameters(name = "db={0}")
   public static Collection<Object[]> parameters() {
      return convertParameters(Database.selectedList());
   }

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();
      server = createServer(createDefaultConfig(0, true));
      server.start();

   }

   @TestTemplate
   public void testData() throws Exception {

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

      printData.printDataJDBC(server.getConfiguration(), printStream);

      String printDataOutput = byteArrayOutputStream.toString();

      for (int i = 0; i < numberOfMessages; i++) {
         assertTrue(printDataOutput.lastIndexOf("message " + i) >= 0);
      }
      // I know this is a bit fragile, but the queues routed portion of the report was not working.
      // if the report ever changes, so the test will need to be changed.
      assertTrue(printDataOutput.lastIndexOf("queues routed") >= 0);

   }

}

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

package org.apache.activemq.artemis.tests.soak.paging;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;

import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.apache.activemq.artemis.cli.commands.tools.xml.XmlDataExporter;
import org.apache.activemq.artemis.cli.commands.tools.xml.XmlDataImporter;
import org.apache.activemq.artemis.tests.soak.SoakTestBase;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ValidateExportSpeedTest extends SoakTestBase {

   public static final String SERVER_NAME_0 = "paging-export";

   @BeforeAll
   public static void createServers() throws Exception {
      {
         File serverLocation = getFileServerLocation(SERVER_NAME_0);
         deleteDirectory(serverLocation);

         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setRole("amq").setUser("admin").setPassword("admin").setAllowAnonymous(true).setNoWeb(false).setArtemisInstance(serverLocation);
         cliCreateServer.setArgs("--global-max-messages", "10000", "--java-options", "-ea", "--queues", "TEST");
         cliCreateServer.createServer();
      }
   }

   public static final String TARGET_EXPORTER_DMP = "./target/exporter.dmp";
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   Process serverProcess;

   @BeforeEach
   public void before() throws Exception {
      cleanupData(SERVER_NAME_0);

      serverProcess = startServer(SERVER_NAME_0, 0, 10_000);
   }

   @AfterEach
   public void cleanup() throws Exception {
      File dmp = new File(TARGET_EXPORTER_DMP);
      if (dmp.exists()) {
         try {
            dmp.delete();
         } catch (Exception ignored) {
         }
      }
   }

   private void send(Session session, MessageProducer producer, int i) throws Exception {
      Message message = null;
      switch (i % 5) {
         case 0: {
            message = session.createTextMessage("hello" + i);
            break;
         }
         case 1: {
            MapMessage mapMessage = session.createMapMessage();
            mapMessage.setString("hello", "hello" + i);
            message = mapMessage;
            break;
         }
         case 2: {
            StreamMessage streamMessage = session.createStreamMessage();
            streamMessage.writeString("string" + i);
            streamMessage.writeLong((long) i);
            streamMessage.writeInt(i);
            message = streamMessage;
            break;
         }
         case 3: {
            BytesMessage bytesMessage = session.createBytesMessage();
            bytesMessage.writeBytes(("hello " + i).getBytes(StandardCharsets.UTF_8));
            message = bytesMessage;
            break;
         }
         case 4: {
            message = session.createObjectMessage("hello " + i);
            break;
         }

      }

      message.setIntProperty("i", i);
      message.setStringProperty("stri", "string" + i);
      producer.send(message);
   }

   private void receive(MessageConsumer consumer, int i) throws Exception {
      Message message = consumer.receive(10_000);
      assertNotNull(message);
      switch (i % 5) {
         case 0: {
            assertInstanceOf(TextMessage.class, message);
            TextMessage textMessage = (TextMessage) message;
            assertEquals("hello" + i, textMessage.getText());
            break;
         }
         case 1: {
            assertInstanceOf(MapMessage.class, message);
            MapMessage mapMessage = (MapMessage) message;
            assertEquals("hello" + i, mapMessage.getString("hello"));
            break;
         }
         case 2: {
            assertInstanceOf(StreamMessage.class, message);
            StreamMessage streamMessage = (StreamMessage) message;
            assertEquals("string" + i, streamMessage.readString());
            assertEquals((long) i, streamMessage.readLong());
            assertEquals(i, streamMessage.readInt());
            break;
         }
         case 3: {
            assertInstanceOf(BytesMessage.class, message);
            BytesMessage bytesMessage = (BytesMessage) message;
            int length = (int) bytesMessage.getBodyLength();
            byte[] bytes = new byte[length];
            bytesMessage.readBytes(bytes);
            String str = new String(bytes, StandardCharsets.UTF_8);
            assertEquals("hello " + i, str);
            break;
         }
         case 4: {
            assertInstanceOf(ObjectMessage.class, message);
            ObjectMessage objectMessage = (ObjectMessage) message;
            String result = (String)objectMessage.getObject();
            assertEquals("hello " + i, result);
            break;
         }
      }

      assertEquals(i, message.getIntProperty("i"));
      assertEquals("string" + i, message.getStringProperty("stri"));
   }

   String largeString;

   private void sendLargeMessages(Session session, MessageProducer producer, int numberOfMessages) throws Exception {

      largeString = RandomUtil.randomAlphaNumericString(200 * 1024);

      for (int i = 0; i < numberOfMessages; i++) {
         logger.info("Sending {} large message", i);
         TextMessage message = session.createTextMessage(largeString + " Hello " + i);
         producer.send(message);
      }

      session.commit();


   }

   private void receiveLargeMessage(Session session, MessageConsumer consumer, int numberOfMessages) throws Exception {
      for (int i = 0; i < numberOfMessages; i++) {
         logger.info("Receiving {} large message", i);
         Message message = consumer.receive(5000);
         assertInstanceOf(TextMessage.class, message);
         TextMessage textMessage = (TextMessage) message;
         assertEquals(largeString + " Hello " + i, textMessage.getText());
      }

      session.commit();

   }

   @Test
   @Timeout(120)
   public void testExportAMQP() throws Exception {
      testExport("AMQP", false, 100, 1); // small sample of data to validate the test itself
      testExport("AMQP", true, 50_000, 100);
   }

   @Test
   @Timeout(120)
   public void testExportCORE() throws Exception {
      testExport("CORE", false, 100, 1); // small sample of data to validate the test itself
      testExport("CORE", true, 50_000, 100);
   }

   private void testExport(String protocol, boolean exportData, int numberOfMessages, int numberOfLargeMessages) throws Exception {
      ConnectionFactory cf = CFUtil.createConnectionFactory(protocol, "tcp://localhost:61616");

      try (Connection connection = cf.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageProducer producer = session.createProducer(session.createQueue("TEST"));
         sendLargeMessages(session, producer, numberOfLargeMessages);
         for (int i = 0; i < numberOfMessages; i++) {
            send(session, producer, i);
            if (i % 10_000 == 0 && i > 0) {
               logger.info("sent {}", i);
               session.commit();
            }
         }
         session.commit();
      }

      if (exportData) {
         serverProcess.destroyForcibly();
         Wait.waitFor(() -> !serverProcess.isAlive());
         XmlDataExporter xmlDataExporter = new XmlDataExporter();
         xmlDataExporter.setHomeValues(null, new File(getServerLocation(SERVER_NAME_0)), null);
         xmlDataExporter.setOutput(new File(TARGET_EXPORTER_DMP));
         xmlDataExporter.setIgnoreLock(true);
         xmlDataExporter.execute(new ActionContext());

         cleanupData(SERVER_NAME_0);

         serverProcess = startServer(SERVER_NAME_0, 0, 10_000);

         XmlDataImporter dataImporter = new XmlDataImporter();
         dataImporter.setHomeValues(null, new File(getServerLocation(SERVER_NAME_0)), null);

         dataImporter.input = xmlDataExporter.getOutput().getAbsolutePath();
         dataImporter.execute(new ActionContext());
      }

      try (Connection connection = cf.createConnection()) {
         Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
         MessageConsumer consumer = session.createConsumer(session.createQueue("TEST"));
         connection.start();
         receiveLargeMessage(session, consumer, numberOfLargeMessages);
         for (int i = 0; i < numberOfMessages; i++) {
            receive(consumer, i);
            if (i % 10_000 == 0 && i > 0) {
               logger.info("received {}", i);
               session.commit();
            }
         }
         session.commit();
      }
   }
}
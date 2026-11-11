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
package org.apache.activemq.artemis.tests.stress.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.cli.commands.tools.xml.XmlDataExporter;
import org.apache.activemq.artemis.cli.commands.tools.xml.XmlDataImporter;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.Test;

public class XmlImportExportStressTest extends ActiveMQTestBase {

   public static final int CONSUMER_TIMEOUT = 5000;

   @Test
   public void testHighVolume() throws Exception {
      final String FILE_NAME = getTestDir() + "/export.out";

      final String QUEUE_NAME = "A1";
      ActiveMQServer server = createServer(true);
      server.start();
      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory factory = locator.createSessionFactory();
      ClientSession session = factory.createSession(false, false, false);

      session.createQueue(QueueConfiguration.of(QUEUE_NAME));

      ClientProducer producer = session.createProducer(QUEUE_NAME);

      ClientMessage msg = session.createMessage(true);
      final int SIZE = 10240;
      final int COUNT = 20000;
      byte[] bodyTst = new byte[SIZE];
      for (int i = 0; i < SIZE; i++) {
         bodyTst[i] = (byte) (i + 1);
      }

      msg.getBodyBuffer().writeBytes(bodyTst);
      assertEquals(bodyTst.length, msg.getBodySize());

      for (int i = 0; i < COUNT; i++) {
         producer.send(msg);
         if (i % 500 == 0) {
            System.out.println("Sent " + i);
            session.commit();
         }
      }

      session.commit();

      session.close();
      locator.close();
      server.stop();

      System.out.println("Writing XML...");
      FileOutputStream xmlOutputStream = new FileOutputStream(FILE_NAME);
      BufferedOutputStream bufferOut = new BufferedOutputStream(xmlOutputStream);
      XmlDataExporter xmlDataExporter = new XmlDataExporter();
      xmlDataExporter.process(bufferOut, getBindingsDir(), getJournalDir(), getPageDir(), getLargeMessagesDir());
      bufferOut.close();
      System.out.println("Done writing XML.");

      deleteDirectory(new File(getJournalDir()));
      deleteDirectory(new File(getBindingsDir()));
      deleteDirectory(new File(getPageDir()));
      deleteDirectory(new File(getLargeMessagesDir()));
      server.start();
      locator = createInVMNonHALocator();
      factory = locator.createSessionFactory();
      session = factory.createSession(false, false, true);
      ClientSession managementSession = factory.createSession(false, true, true);

      System.out.println("Reading XML...");
      FileInputStream xmlInputStream = new FileInputStream(FILE_NAME);
      XmlDataImporter xmlDataImporter = new XmlDataImporter();
      xmlDataImporter.process(xmlInputStream, session, managementSession);
      xmlInputStream.close();
      System.out.println("Done reading XML.");

      ClientConsumer consumer = session.createConsumer(QUEUE_NAME);
      session.start();

      for (int i = 0; i < COUNT; i++) {
         msg = consumer.receive(CONSUMER_TIMEOUT);
         assertNotNull(msg);

         msg.acknowledge();
         if (i % 500 == 0) {
            System.out.println("Received " + i);
            session.commit();
         }

         assertEquals(msg.getBodySize(), bodyTst.length);
         byte[] bodyRead = new byte[bodyTst.length];
         msg.getBodyBuffer().readBytes(bodyRead);
         assertEqualsByteArrays(bodyTst, bodyRead);
      }

      session.close();
      locator.close();
      server.stop();
      File temp = new File(FILE_NAME);
      temp.delete();
   }
}

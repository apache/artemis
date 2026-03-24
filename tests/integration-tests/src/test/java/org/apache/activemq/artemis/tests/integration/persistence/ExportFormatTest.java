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
package org.apache.activemq.artemis.tests.integration.persistence;

import java.io.StringReader;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.cli.commands.tools.journal.DecodeJournal;
import org.apache.activemq.artemis.cli.commands.tools.journal.EncodeJournal;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.Ignore;
import org.junit.Test;

public class ExportFormatTest extends ActiveMQTestBase {


   // Case the format was changed, and the change was agreed, use _testCreateFormat to recreate this field
   String bindingsFile = "#File,JournalFileImpl: (activemq-bindings-1.bindings id = 1, recordID = 1)\n" +
      "operation@AddRecord,id@1,userRecordType@24,length@8,isUpdate@false,compactCount@0,data@AAAAAH____8=\n" +
      "operation@AddRecordTX,txID@2,id@3,userRecordType@21,length@43,isUpdate@false,compactCount@0,data@AAAABEEAMQAAAAAEQQAxAAABAAAAFHUAcwBlAHIAPQBuAHUAbABsADsAAA==\n" +
      "operation@Commit,txID@2,numberOfRecords@1\n" +
      "operation@AddRecord,id@20,userRecordType@24,length@8,isUpdate@false,compactCount@0,data@AAAAAAAAABQ=\n" +
      "#File,JournalFileImpl: (activemq-bindings-2.bindings id = 2, recordID = 2)";

   // Case the format was changed, and the change was agreed, use _testCreateFormat to recreate this field
   String journalFile = "#File,JournalFileImpl: (activemq-data-1.amq id = 1, recordID = 1)\n" +
      "operation@AddRecordTX,txID@0,id@7,userRecordType@45,length@83,isUpdate@false,compactCount@0,data@AQAAAAAAAAAHAQAAAARBADEAAAAAPQAAAA0AAAAAAAAABwEAAAAEQQAxAAAAAQAAAAAAAAAAAAABWpf6WFoEAQAAAAEAAAAGawBlAHkABgAAAAA=\n" +
      "operation@UpdateTX,txID@0,id@7,userRecordType@32,length@8,isUpdate@true,compactCount@0,data@AAAAAAAAAAM=\n" +
      "operation@AddRecordTX,txID@0,id@8,userRecordType@45,length@83,isUpdate@false,compactCount@0,data@AQAAAAAAAAAIAQAAAARBADEAAAAAPQAAAA0AAAAAAAAACAEAAAAEQQAxAAAAAQAAAAAAAAAAAAABWpf6WF4EAQAAAAEAAAAGawBlAHkABgAAAAE=\n" +
      "operation@UpdateTX,txID@0,id@8,userRecordType@32,length@8,isUpdate@true,compactCount@0,data@AAAAAAAAAAM=\n" +
      "operation@AddRecordTX,txID@0,id@9,userRecordType@45,length@83,isUpdate@false,compactCount@0,data@AQAAAAAAAAAJAQAAAARBADEAAAAAPQAAAA0AAAAAAAAACQEAAAAEQQAxAAAAAQAAAAAAAAAAAAABWpf6WF4EAQAAAAEAAAAGawBlAHkABgAAAAI=\n" +
      "operation@UpdateTX,txID@0,id@9,userRecordType@32,length@8,isUpdate@true,compactCount@0,data@AAAAAAAAAAM=\n" +
      "operation@AddRecordTX,txID@0,id@10,userRecordType@45,length@83,isUpdate@false,compactCount@0,data@AQAAAAAAAAAKAQAAAARBADEAAAAAPQAAAA0AAAAAAAAACgEAAAAEQQAxAAAAAQAAAAAAAAAAAAABWpf6WF8EAQAAAAEAAAAGawBlAHkABgAAAAM=\n" +
      "operation@UpdateTX,txID@0,id@10,userRecordType@32,length@8,isUpdate@true,compactCount@0,data@AAAAAAAAAAM=\n" +
      "operation@AddRecordTX,txID@0,id@11,userRecordType@45,length@83,isUpdate@false,compactCount@0,data@AQAAAAAAAAALAQAAAARBADEAAAAAPQAAAA0AAAAAAAAACwEAAAAEQQAxAAAAAQAAAAAAAAAAAAABWpf6WF8EAQAAAAEAAAAGawBlAHkABgAAAAQ=\n" +
      "operation@UpdateTX,txID@0,id@11,userRecordType@32,length@8,isUpdate@true,compactCount@0,data@AAAAAAAAAAM=\n" +
      "operation@Commit,txID@0,numberOfRecords@10\n" +
      "operation@AddRecord,id@15,userRecordType@45,length@83,isUpdate@false,compactCount@0,data@AQAAAAAAAAAPAQAAAARBADEAAAAAPQAAAA0AAAAAAAAADwEAAAAEQQAxAAAAAQAAAAAAAAAAAAABWpf6WLAEAQAAAAEAAAAGawBlAHkABgAAAAU=\n" +
      "operation@Update,id@15,userRecordType@32,length@8,isUpdate@true,compactCount@0,data@AAAAAAAAAAM=\n" +
      "operation@AddRecord,id@16,userRecordType@45,length@83,isUpdate@false,compactCount@0,data@AQAAAAAAAAAQAQAAAARBADEAAAAAPQAAAA0AAAAAAAAAEAEAAAAEQQAxAAAAAQAAAAAAAAAAAAABWpf6WLIEAQAAAAEAAAAGawBlAHkABgAAAAY=\n" +
      "operation@Update,id@16,userRecordType@32,length@8,isUpdate@true,compactCount@0,data@AAAAAAAAAAM=\n" +
      "operation@AddRecord,id@17,userRecordType@45,length@83,isUpdate@false,compactCount@0,data@AQAAAAAAAAARAQAAAARBADEAAAAAPQAAAA0AAAAAAAAAEQEAAAAEQQAxAAAAAQAAAAAAAAAAAAABWpf6WLgEAQAAAAEAAAAGawBlAHkABgAAAAc=\n" +
      "operation@Update,id@17,userRecordType@32,length@8,isUpdate@true,compactCount@0,data@AAAAAAAAAAM=\n" +
      "operation@AddRecord,id@18,userRecordType@45,length@83,isUpdate@false,compactCount@0,data@AQAAAAAAAAASAQAAAARBADEAAAAAPQAAAA0AAAAAAAAAEgEAAAAEQQAxAAAAAQAAAAAAAAAAAAABWpf6WLwEAQAAAAEAAAAGawBlAHkABgAAAAg=\n" +
      "operation@Update,id@18,userRecordType@32,length@8,isUpdate@true,compactCount@0,data@AAAAAAAAAAM=\n" +
      "operation@AddRecord,id@19,userRecordType@45,length@83,isUpdate@false,compactCount@0,data@AQAAAAAAAAATAQAAAARBADEAAAAAPQAAAA0AAAAAAAAAEwEAAAAEQQAxAAAAAQAAAAAAAAAAAAABWpf6WL4EAQAAAAEAAAAGawBlAHkABgAAAAk=\n" +
      "operation@Update,id@19,userRecordType@32,length@8,isUpdate@true,compactCount@0,data@AAAAAAAAAAM=\n" +
      "#File,JournalFileImpl: (activemq-data-2.amq id = 2, recordID = 2)";

   @Test
   @Ignore
   // Used to update the format, if you need to use this it means the data format was broken, Be careful on updating the format!
   public void testCreateFormat() throws Exception {
      ActiveMQServer server = createServer(true);
      server.start();

      ServerLocator locator = createInVMNonHALocator();

      ClientSessionFactory factory = createSessionFactory(locator);
      ClientSession session = factory.createSession(false, false, false);
      session.createQueue(new QueueConfiguration("A1"));

      ClientProducer producer = session.createProducer("A1");
      for (int i = 0; i < 5; i++) {
         ClientMessage msg = session.createMessage(true);
         msg.putIntProperty("key", i);
         producer.send(msg);
      }
      session.commit();

      session.close();

      session = factory.createSession(false, true, true);

      producer = session.createProducer("A1");

      for (int i = 5; i < 10; i++) {
         ClientMessage msg = session.createMessage(true);
         msg.putIntProperty("key", i);
         producer.send(msg);
      }

      locator.close();
      server.stop();

      System.out.println();
      System.out.println("copy & paste the following as bindingsFile:");

      EncodeJournal.exportJournal(server.getConfiguration().getBindingsLocation().getAbsolutePath(), "activemq-bindings", "bindings", 2, 1048576, System.out);

      System.out.println();
      System.out.println("copy & paste the following as dataFile:");

      EncodeJournal.exportJournal(server.getConfiguration().getJournalLocation().getAbsolutePath(), "activemq-data", "amq", 2, 102400, System.out);
   }

   @Test
   public void testConsumeFromFormat() throws Exception {
      ActiveMQServer server = createServer(true);

      DecodeJournal.importJournal(server.getConfiguration().getJournalLocation().getAbsolutePath(), "activemq-data", "amq", 2, 102400, new StringReader(journalFile));
      DecodeJournal.importJournal(server.getConfiguration().getBindingsLocation().getAbsolutePath(), "activemq-bindings", "bindings", 2, 1048576, new StringReader(bindingsFile));
      server.start();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory factory = createSessionFactory(locator);
      ClientSession session = factory.createSession();
      session.start();

      ClientConsumer consumer = session.createConsumer("A1");
      for (int i = 0; i < 10; i++) {
         ClientMessage msg = consumer.receive(5000);
         assertNotNull(msg);
         msg.acknowledge();
         assertEquals(i, msg.getIntProperty("key").intValue());
      }

      session.commit();
   }

}

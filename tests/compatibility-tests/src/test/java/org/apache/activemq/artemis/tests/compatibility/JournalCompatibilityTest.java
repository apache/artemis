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

package org.apache.activemq.artemis.tests.compatibility;

import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.SNAPSHOT;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.ARTEMIS_2_4_0;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.ARTEMIS_2_1_0;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.activemq.artemis.tests.compatibility.base.VersionedBase;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.utils.FileUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class JournalCompatibilityTest extends VersionedBase {

   // this will ensure that all tests in this class are run twice,
   // once with "true" passed to the class' constructor and once with "false"
   @Parameters(name = "sender={0}, receiver={1}")
   public static Collection getParameters() {
      // we don't need every single version ever released..
      // if we keep testing current one against 2.4 and 1.4.. we are sure the wire and API won't change over time
      List<Object[]> combinations = new ArrayList<>();

      combinations.add(new Object[]{ARTEMIS_2_1_0, SNAPSHOT});
      combinations.add(new Object[]{ARTEMIS_2_4_0, SNAPSHOT});
      // the purpose on this one is just to validate the test itself.
      /// if it can't run against itself it won't work at all
      combinations.add(new Object[]{SNAPSHOT, SNAPSHOT});
      return combinations;
   }

   public JournalCompatibilityTest(String sender, String receiver) throws Exception {
      super(sender, receiver);
   }

   @BeforeEach
   public void removeFolder() throws Throwable {
      FileUtil.deleteDirectory(serverFolder);
      serverFolder.mkdirs();
   }

   @AfterEach
   public void tearDown() {
      try {
         stopServer(serverClassloader);
      } catch (Throwable ignored) {
      }
      try {
         stopServer(receiverClassloader);
      } catch (Throwable ignored) {
      }
   }

   @TestTemplate
   public void testSendReceive() throws Throwable {
      setVariable(senderClassloader, "persistent", true);
      startServer(serverFolder, sender, senderClassloader, "journalTest", null, true);
      evaluate(senderClassloader, "meshTest/sendMessages.groovy", sender, sender, "sendAckMessages");
      stopServer(senderClassloader);

      setVariable(receiverClassloader, "persistent", true);
      startServer(serverFolder, receiver, receiverClassloader, "journalTest", null, false);

      setVariable(receiverClassloader, "latch", null);
      evaluate(receiverClassloader, "meshTest/sendMessages.groovy", receiver, receiver, "receiveMessages");
   }

   @TestTemplate
   public void testSendReceivePaging() throws Throwable {
      setVariable(senderClassloader, "persistent", true);
      startServer(serverFolder, sender, senderClassloader, "journalTest", null, true);
      evaluate(senderClassloader, "journalcompatibility/forcepaging.groovy");
      evaluate(senderClassloader, "meshTest/sendMessages.groovy", sender, sender, "sendAckMessages");
      evaluate(senderClassloader, "journalcompatibility/ispaging.groovy");
      stopServer(senderClassloader);

      setVariable(receiverClassloader, "persistent", true);
      startServer(serverFolder, receiver, receiverClassloader, "journalTest", null, false);
      evaluate(receiverClassloader, "journalcompatibility/ispaging.groovy");

      setVariable(receiverClassloader, "latch", null);
      evaluate(receiverClassloader, "meshTest/sendMessages.groovy", receiver, receiver, "receiveMessages");
   }

   @TestTemplate
   public void testSendReceiveAMQPPaging() throws Throwable {
      setVariable(senderClassloader, "persistent", true);
      startServer(serverFolder, sender, senderClassloader, "journalTest", null, true);
      evaluate(senderClassloader, "journalcompatibility/forcepaging.groovy");
      evaluate(senderClassloader, "meshTest/sendMessages.groovy", sender, sender, "sendAckMessages", "AMQP");
      evaluate(senderClassloader, "journalcompatibility/ispaging.groovy");
      stopServer(senderClassloader);

      setVariable(receiverClassloader, "persistent", true);
      startServer(serverFolder, receiver, receiverClassloader, "journalTest", null, false);
      evaluate(receiverClassloader, "journalcompatibility/ispaging.groovy");

      setVariable(receiverClassloader, "latch", null);
      evaluate(receiverClassloader, "meshTest/sendMessages.groovy", receiver, receiver, "receiveMessages", "AMQP");
   }

   /**
    * Test that the server starts properly using an old journal even though persistent size metrics were not originaly
    * stored
    */
   @TestTemplate
   public void testSendReceiveQueueMetrics() throws Throwable {
      setVariable(senderClassloader, "persistent", true);
      startServer(serverFolder, sender, senderClassloader, "journalTest", null, true);
      evaluate(senderClassloader, "meshTest/sendMessages.groovy", sender, sender, "sendAckMessages");
      stopServer(senderClassloader);

      setVariable(receiverClassloader, "persistent", true);
      startServer(serverFolder, receiver, receiverClassloader, "journalTest", null, false);

      setVariable(receiverClassloader, "latch", null);
      evaluate(receiverClassloader, "metrics/queueMetrics.groovy", receiver, receiver, "receiveMessages");
   }

   /**
    * Test that the metrics are recovered when paging.  Even though the paging counts won't be persisted the journal the
    * server should still start properly.  The persistent sizes will be recovered when the messages are depaged
    */
   @TestTemplate
   public void testSendReceiveSizeQueueMetricsPaging() throws Throwable {
      setVariable(senderClassloader, "persistent", true);
      //Set max size to 1 to cause messages to immediately go to the paging store
      startServer(serverFolder, sender, senderClassloader, "journalTest", Long.toString(1), true);
      evaluate(senderClassloader, "journalcompatibility/forcepaging.groovy");
      evaluate(senderClassloader, "meshTest/sendMessages.groovy", sender, sender, "sendAckMessages");
      evaluate(senderClassloader, "journalcompatibility/ispaging.groovy");
      stopServer(senderClassloader);

      setVariable(receiverClassloader, "persistent", true);
      startServer(serverFolder, receiver, receiverClassloader, "journalTest", Long.toString(1), false);
      evaluate(receiverClassloader, "journalcompatibility/ispaging.groovy");


      evaluate(receiverClassloader, "metrics/queueMetrics.groovy", receiver, receiver, "receiveMessages");
   }
}
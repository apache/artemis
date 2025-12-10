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

import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.ONE_FOUR;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.SNAPSHOT;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

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
public class ExportImportTest extends VersionedBase {
   private String serverScriptToUse;
   private boolean skipTearDownCleanup = false;

   // this will ensure that all tests in this class are run twice,
   // once with "true" passed to the class' constructor and once with "false"
   @Parameters(name = "sender={0}, consumer={1}")
   public static Collection getParameters() {
      // we don't need every single version ever released..
      // if we keep testing current one against 2.4 and 1.4.. we are sure the wire and API won't change over time
      List<Object[]> combinations = new ArrayList<>();

      combinations.add(new Object[]{ONE_FOUR, SNAPSHOT});
      combinations.add(new Object[]{SNAPSHOT, SNAPSHOT});
      return combinations;
   }

   public ExportImportTest(String sender, String receiver) throws Exception {
      super(sender, receiver);
   }

   @BeforeEach
   public void removeFolder() throws Throwable {
      FileUtil.deleteDirectory(serverFolder);
      serverFolder.mkdirs();
   }

   @AfterEach
   public void tearDown() {
      if (skipTearDownCleanup) {
         // Skip server teardown when test is no-op, avoids a chunk of stacktrace
         return;
      }

      try {
         stopServer(serverClassloader);
      } catch (Throwable ignored) {
         ignored.printStackTrace();
      }
      try {
         stopServer(receiverClassloader);
      } catch (Throwable ignored) {
         ignored.printStackTrace();
      }
   }

   @TestTemplate
   public void testSendReceive() throws Throwable {
      internalSendReceive(false);
   }

   @TestTemplate
   public void testSendReceivelegacy() throws Throwable {
      // makes no sense on snapshot
      boolean isSenderSnapshot = SNAPSHOT.equals(sender);
      skipTearDownCleanup = isSenderSnapshot;
      assumeFalse(isSenderSnapshot, "This test only applies to old version senders");

      internalSendReceive(true);
   }

   public void internalSendReceive(boolean legacyPrefixes) throws Throwable {
      setVariable(senderClassloader, "legacy", false);
      setVariable(senderClassloader, "persistent", true);
      if (legacyPrefixes) {
         serverScriptToUse = "exportimport/artemisServer.groovy";
      }
      startServer(serverFolder, sender, senderClassloader, "sender");
      evaluate(senderClassloader, "meshTest/sendMessages.groovy", sender, sender, "sendAckMessages");
      stopServer(senderClassloader);

      if (sender.startsWith("ARTEMIS-1")) {
         evaluate(senderClassloader, "exportimport/export1X.groovy", serverFolder.getAbsolutePath());
      } else {
         evaluate(senderClassloader, "exportimport/export.groovy", serverFolder.getAbsolutePath());
      }

      setVariable(receiverClassloader, "legacy", legacyPrefixes);
      try {
         setVariable(receiverClassloader, "persistent", true);
         startServer(serverFolder, receiver, receiverClassloader, "receiver");

         setVariable(receiverClassloader, "sort", sender.startsWith("ARTEMIS-1"));

         evaluate(receiverClassloader, "exportimport/import.groovy", serverFolder.getAbsolutePath());

         setVariable(receiverClassloader, "latch", null);
         evaluate(receiverClassloader, "meshTest/sendMessages.groovy", receiver, receiver, "receiveMessages");
      } finally {
         setVariable(receiverClassloader, "legacy", false);
      }
   }

   @Override
   public String getServerScriptToUse() {
      return serverScriptToUse;
   }
}


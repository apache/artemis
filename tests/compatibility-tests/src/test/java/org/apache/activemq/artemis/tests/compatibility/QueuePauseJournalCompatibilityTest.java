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
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.ARTEMIS_2_6_3;

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
public class QueuePauseJournalCompatibilityTest extends VersionedBase {

   // this will ensure that all tests in this class are run twice,
   // once with "true" passed to the class' constructor and once with "false"
   @Parameters(name = "producer={0}, consumer={1}")
   public static Collection getParameters() {
      // we don't need every single version ever released..
      // if we keep testing current one against 2.4 and 1.4.. we are sure the wire and API won't change over time
      List<Object[]> combinations = new ArrayList<>();

      combinations.add(new Object[]{ARTEMIS_2_6_3, SNAPSHOT});
      // the purpose on this one is just to validate the test itself.
      /// if it can't run against itself it won't work at all
      combinations.add(new Object[]{SNAPSHOT, SNAPSHOT});
      return combinations;
   }

   public QueuePauseJournalCompatibilityTest(String sender, String receiver) throws Exception {
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
   public void testSendReceiveQueue() throws Throwable {
      internal("queue");
   }

   public void internal(String destinationName) throws Throwable {
      setVariable(senderClassloader, "persistent", true);
      startServer(serverFolder, sender, senderClassloader, "journalTest", null, true);
      evaluate(senderClassloader, "queuepause/beforestop.groovy", destinationName);
      stopServer(senderClassloader);

      setVariable(receiverClassloader, "persistent", true);
      startServer(serverFolder, receiver, receiverClassloader, "journalTest", null, false);
      evaluate(receiverClassloader, "queuepause/afterstop.groovy", destinationName);
      stopServer(receiverClassloader);

      // on a third try, we run the beforestop again, as the address should been in regular conditions when aftertop.groovy is finished
      setVariable(receiverClassloader, "persistent", true);
      startServer(serverFolder, receiver, receiverClassloader, "journalTest", null, false);
      evaluate(receiverClassloader, "queuepause/beforestop.groovy", destinationName);
   }
}


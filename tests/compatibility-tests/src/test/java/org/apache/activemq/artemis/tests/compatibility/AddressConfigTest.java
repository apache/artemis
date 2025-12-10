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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.tests.compatibility.base.VersionedBase;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.utils.FileUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class AddressConfigTest extends VersionedBase {

   @Parameters(name = "server={0}, producer={1}, consumer={2}")
   public static Collection getParameters() {
      List<Object[]> combinations = new ArrayList<>();
      combinations.addAll(combinatory(SNAPSHOT, new Object[]{SNAPSHOT}, new Object[]{ONE_FOUR, SNAPSHOT}, new Object[]{ONE_FOUR, SNAPSHOT}));
      return combinations;
   }

   public AddressConfigTest(String server, String sender, String receiver) throws Exception {
      super(server, sender, receiver);
   }


   @BeforeEach
   public void setUp() throws Throwable {
      FileUtil.deleteDirectory(serverFolder);
   }

   @AfterEach
   public void stopTest() throws Exception {
      execute(serverClassloader, "server.stop()");
   }

   @TestTemplate
   public void testClientSenderServerAddressSettings() throws Throwable {
      evaluate(serverClassloader, "addressConfig/artemisServer.groovy", serverFolder.getAbsolutePath());


      CountDownLatch latch = new CountDownLatch(1);

      setVariable(receiverClassloader, "latch", latch);

      AtomicInteger errors = new AtomicInteger(0);

      Thread t = new Thread(() -> {
         try {
            evaluate(receiverClassloader, "addressConfig/receiveMessages.groovy", "receive");
         } catch (Throwable e) {
            errors.incrementAndGet();
         }
      });
      t.start();


      Thread t2 = new Thread(() -> {
         try {
            evaluate(senderClassloader, "addressConfig/sendMessagesAddress.groovy", "send");
         } catch (Throwable e) {
            errors.incrementAndGet();
         }
      });
      t2.start();


      try {
         assertTrue(latch.await(10, TimeUnit.SECONDS), "Sender is blocking by mistake");
      } finally {

         t.join(TimeUnit.SECONDS.toMillis(1));
         t2.join(TimeUnit.SECONDS.toMillis(1));

         if (t.isAlive()) {
            t.interrupt();
         }

         if (t2.isAlive()) {
            t2.interrupt();
         }
      }

   }

}

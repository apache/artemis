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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.tests.compatibility.base.ServerBase;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class Mesh2Test extends ServerBase {

   // this will ensure that all tests in this class are run twice,
   // once with "true" passed to the class' constructor and once with "false"
   @Parameters(name = "server={0}, producer={1}, consumer={2}")
   public static Collection getParameters() {
      // we don't need every single version ever released..
      // if we keep testing current one against 2.4 and 1.4.. we are sure the wire and API won't change over time
      List<Object[]> combinations = new ArrayList<>();
      combinations.add(new Object[]{SNAPSHOT, SNAPSHOT, SNAPSHOT});
      combinations.add(new Object[]{SNAPSHOT, ARTEMIS_2_4_0, ARTEMIS_2_4_0});
      combinations.add(new Object[]{ARTEMIS_2_4_0, SNAPSHOT, SNAPSHOT});
      return combinations;
   }

   public Mesh2Test(String server, String sender, String receiver) throws Exception {
      super(server, sender, receiver);
   }

   @TestTemplate
   public void testSendReceiveTopicShared() throws Throwable {
      CountDownLatch latch = new CountDownLatch(1);

      setVariable(receiverClassloader, "latch", latch);
      AtomicInteger errors = new AtomicInteger(0);
      Thread t = new Thread(() -> {
         try {
            evaluate(receiverClassloader, "meshTest/sendMessages.groovy", server, receiver, "receiveShared");
         } catch (Exception e) {
            e.printStackTrace();
            errors.incrementAndGet();
         }
      });

      t.start();
      assertTrue(latch.await(10, TimeUnit.SECONDS));
      evaluate(senderClassloader, "meshTest/sendMessages.groovy", server, sender, "sendTopic");

      t.join();

      assertEquals(0, errors.get());
   }

}


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

import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.HORNETQ_2_3_5;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.ARTEMIS_1_4_0;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.SNAPSHOT;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.tests.compatibility.base.ClasspathBase;
import org.apache.activemq.artemis.utils.FileUtil;
import org.apache.activemq.artemis.utils.ReusableLatch;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class HornetQSoakTest extends ClasspathBase {

   ClassLoader artemisClassLoader;
   ClassLoader artemis1XClassLoader;
   ClassLoader hornetqClassLoader;

   @BeforeEach
   public void setUp() throws Throwable {

      this.artemisClassLoader = getClasspath(SNAPSHOT);
      this.artemis1XClassLoader = getClasspath(ARTEMIS_1_4_0);
      this.hornetqClassLoader = getClasspath(HORNETQ_2_3_5);

      FileUtil.deleteDirectory(serverFolder);
      setVariable(artemisClassLoader, "persistent", Boolean.FALSE);
      startServer(serverFolder, artemisClassLoader, "live", null, true, "hqsoak/artemisServer.groovy", SNAPSHOT, SNAPSHOT, SNAPSHOT);
   }

   @AfterEach
   public void tearDown() throws Throwable {
      if (artemisClassLoader != null && hornetqClassLoader != null) {
         stopServer(artemisClassLoader);
      }
   }

   @Test
   public void testSoakHornetQ() throws Throwable {
      ReusableLatch reusableLatch = new ReusableLatch(0);

      int threadsProducerArtemis = 2;
      int numberOfMessagesArtemis = 5;
      int threadsProducerHornetQ = 2;
      int numberOfMessagesHornetQ = 5;

      int numberOfConsumersArtemis = 10;
      int numberOfConsumersArtemis1X = 5;
      int numberOfConsumersHornetQ = 10;

      int multiplyFactor = numberOfConsumersArtemis + numberOfConsumersArtemis1X + numberOfConsumersHornetQ;

      setVariable(artemisClassLoader, "reusableLatch", reusableLatch);
      setVariable(artemisClassLoader, "multiplyFactor", multiplyFactor);
      setVariable(artemis1XClassLoader, "reusableLatch", reusableLatch);
      setVariable(artemis1XClassLoader, "multiplyFactor", multiplyFactor);
      setVariable(hornetqClassLoader, "reusableLatch", reusableLatch);
      setVariable(hornetqClassLoader, "multiplyFactor", multiplyFactor);

      int totalMessagePerQueue = threadsProducerArtemis * numberOfMessagesArtemis + threadsProducerHornetQ * numberOfMessagesHornetQ;

      AtomicInteger runningConsumerArtemis = (AtomicInteger) evaluate(artemisClassLoader, "hqsoak/receiveMessages.groovy", "ARTEMIS", "" + numberOfConsumersArtemis, "" + totalMessagePerQueue);
      AtomicInteger runningConsumer1XArtemis = (AtomicInteger) evaluate(artemis1XClassLoader, "hqsoak/receiveMessages.groovy", "ARTEMIS1X", "" + numberOfConsumersArtemis, "" + totalMessagePerQueue);
      AtomicInteger runningConsumerHornetQ = (AtomicInteger) evaluate(hornetqClassLoader, "hqsoak/receiveMessages.groovy", "HORNETQ", "" + numberOfConsumersHornetQ, "" + totalMessagePerQueue);


      AtomicInteger ranArtemisProducer = (AtomicInteger) evaluate(artemisClassLoader, "hqsoak/sendMessages.groovy", "ARTEMIS", "" + threadsProducerArtemis, "" + numberOfMessagesArtemis);
      AtomicInteger ranHornetQProducer = (AtomicInteger) evaluate(hornetqClassLoader, "hqsoak/sendMessages.groovy", "HORNETQ", "" + threadsProducerHornetQ, "" + numberOfMessagesHornetQ);

      Wait.assertEquals(threadsProducerArtemis, ranArtemisProducer::get, TimeUnit.MINUTES.toMillis(5), 100);
      Wait.assertEquals(threadsProducerHornetQ, ranHornetQProducer::get, TimeUnit.MINUTES.toMillis(5), 100);

      Wait.assertEquals(0, runningConsumerArtemis::get);
      Wait.assertEquals(0, runningConsumerHornetQ::get);
      Wait.assertEquals(0, runningConsumer1XArtemis::get);

      checkErrors(artemisClassLoader, "errorsProducer");
      checkErrors(hornetqClassLoader, "errorsProducer");
      checkErrors(artemisClassLoader, "errorsConsumer");
      checkErrors(hornetqClassLoader, "errorsConsumer");
      checkErrors(artemis1XClassLoader, "errorsConsumer");

   }

   protected void checkErrors(ClassLoader loader, String variable) throws Throwable {
      AtomicInteger errors = (AtomicInteger) execute(loader, "return " + variable);
      assertEquals(0, errors.get(), "the script finished with errors");
   }

}

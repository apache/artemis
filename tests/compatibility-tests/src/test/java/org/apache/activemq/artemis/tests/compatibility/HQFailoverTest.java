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
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.SNAPSHOT;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.DeliveryMode;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.client.FailoverEventType;
import org.apache.activemq.artemis.jms.client.ActiveMQConnection;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.compatibility.base.VersionedBase;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.utils.FileUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * This test will run a hornetq server with artemis clients and it will make sure that failover happens without any
 * problems.
 */
@ExtendWith(ParameterizedTestExtension.class)
public class HQFailoverTest extends VersionedBase {

   @Parameters(name = "server={0}, producer={1}, consumer={2}")
   public static Collection getParameters() {
      List<Object[]> combinations = new ArrayList<>();

      combinations.add(new Object[]{HORNETQ_2_3_5, SNAPSHOT, SNAPSHOT});
      return combinations;
   }

   public HQFailoverTest(String server, String sender, String receiver) throws Exception {
      super(server, sender, receiver);
   }

   @BeforeEach
   public void setUp() throws Throwable {
      FileUtil.deleteDirectory(serverFolder);
      evaluate(serverClassloader, "hqfailovertest/hornetqServer.groovy", serverFolder.getAbsolutePath());
   }

   @AfterEach
   public void tearDown() throws Throwable {
      execute(serverClassloader, "backupServer.stop(); liveServer.stop();");

   }

   @TestTemplate
   public void failoverTest() throws Throwable {
      String textBody = "a rapadura e doce mas nao e mole nao";
      String queueName = "queue";
      ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("tcp://localhost:5445?ha=true&reconnectAttempts=10&protocolManagerFactoryStr=org.apache.activemq.artemis.core.protocol.hornetq.client.HornetQClientProtocolManagerFactory&confirmationWindowSize=1048576&blockOnDurableSend=false");

      ActiveMQConnection conn = (ActiveMQConnection) cf.createConnection();
      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      Queue queue = session.createQueue(queueName);

      MessageProducer producer = session.createProducer(queue);
      producer.setDeliveryMode(DeliveryMode.PERSISTENT);
      for (int i = 0; i < 10; i++) {
         producer.send(session.createTextMessage(textBody + i));
      }

      CountDownLatch latch = new CountDownLatch(1);
      conn.setFailoverListener(eventType -> {
         if (eventType == FailoverEventType.FAILOVER_COMPLETED) {
            latch.countDown();
         }
      });

      execute(serverClassloader, "liveServer.stop(true)");

      assertTrue(latch.await(10, TimeUnit.SECONDS));

      // We should still be able to send more after failover.
      // This test is to validate stuff can still work well after failover against hornetq
      for (int i = 0; i < 10; i++) {
         producer.send(session.createTextMessage(textBody + i));
      }
   }
}

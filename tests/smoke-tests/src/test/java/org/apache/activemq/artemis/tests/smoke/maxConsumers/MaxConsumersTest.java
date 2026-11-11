/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.smoke.maxConsumers;

import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;

import java.io.File;

import org.apache.activemq.artemis.tests.smoke.common.SmokeTestBase;
import org.apache.activemq.artemis.cli.commands.helper.HelperCreate;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MaxConsumersTest extends SmokeTestBase {

   public static final String SERVER_NAME_0 = "maxConsumers";

   /*
                 <execution>
                  <phase>test-compile</phase>
                  <id>create-maxConsumers</id>
                  <goals>
                     <goal>create</goal>
                  </goals>
                  <configuration>
                     <!-- this makes it easier in certain envs -->
                     <configuration>${basedir}/target/classes/servers/maxConsumers</configuration>
                     <allowAnonymous>true</allowAnonymous>
                     <user>admin</user>
                     <password>admin</password>
                     <instance>${basedir}/target/maxConsumers</instance>
                  </configuration>
               </execution>

    */

   @BeforeAll
   public static void createServers() throws Exception {

      File server0Location = getFileServerLocation(SERVER_NAME_0);
      deleteDirectory(server0Location);

      {
         HelperCreate cliCreateServer = helperCreate();
         cliCreateServer.setUser("admin").setPassword("admin").setAllowAnonymous(true).setNoWeb(true).setArtemisInstance(server0Location).
            setConfiguration("./src/main/resources/servers/maxConsumers");
         cliCreateServer.createServer();
      }
   }


   @BeforeEach
   public void before() throws Exception {
      cleanupData(SERVER_NAME_0);
      disableCheckThread();
      startServer(SERVER_NAME_0, 0, 30000);
   }

   @Test
   public void testMax() throws Exception {
      for (int i = 0; i < 5; i++) {
         ConnectionFactory factory = new JmsConnectionFactory("amqp://localhost:61616");
         Connection connection = factory.createConnection();
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Queue queue = session.createQueue("myQueue");

         MessageConsumer consumer = session.createConsumer(queue);
         try {
            MessageConsumer consumer2 = session.createConsumer(queue);
            fail("Exception was expected here");
         } catch (JMSException expectedMax) {
         }

         consumer.close();
         connection.close();
      }

   }

}

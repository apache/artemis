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
package org.apache.activemq.artemis.tests.soak.client;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClientNonDivertedSoakTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final SimpleString ADDRESS = SimpleString.of("ADD");

   private static final boolean IS_JOURNAL = false;

   public static final int MIN_MESSAGES_ON_QUEUE = 5000;

   protected boolean isNetty() {
      return false;
   }



   private ActiveMQServer server;

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();

      Configuration config = createDefaultConfig(isNetty()).setJournalFileSize(10 * 1024 * 1024);

      server = createServer(IS_JOURNAL, config, -1, -1, new HashMap<>());

      server.start();

      ServerLocator locator = createFactory(isNetty());

      ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession();

      session.createQueue(QueueConfiguration.of(ClientNonDivertedSoakTest.ADDRESS));

      session.close();

      sf.close();

      locator.close();

   }

   @Test
   public void testSoakClient() throws Exception {
      ServerLocator locator = createFactory(isNetty());

      final ClientSessionFactory sf = createSessionFactory(locator);

      ClientSession session = sf.createSession(false, false);

      ClientProducer producer = session.createProducer(ADDRESS);

      for (int i = 0; i < MIN_MESSAGES_ON_QUEUE; i++) {
         ClientMessage msg = session.createMessage(true);
         msg.putLongProperty("count", i);
         msg.getBodyBuffer().writeBytes(new byte[10 * 1024]);
         producer.send(msg);

         if (i % 1000 == 0) {
            logger.info("Sent {} messages", i);
            session.commit();
         }
      }

      session.commit();

      session.close();
      sf.close();

      Receiver rec1 = new Receiver(createSessionFactory(locator), ADDRESS.toString());

      Sender send = new Sender(createSessionFactory(locator), ADDRESS.toString(), new Receiver[]{rec1});

      send.start();
      rec1.start();

      long timeEnd = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(ClientParameters.TIME_LIMIT_SECONDS);
      while (timeEnd > System.currentTimeMillis()) {
         if (send.getErrorsCount() != 0 || rec1.getErrorsCount() != 0) {
            logger.info("There are sequence errors in some of the clients, please look at the logs");
            break;
         }

         logger.info("count = {}", send.msgs);
         Thread.sleep(10000);
      }

      send.setRunning(false);
      rec1.setRunning(false);

      send.join();
      rec1.join();

      assertEquals(0, send.getErrorsCount());
      assertEquals(0, rec1.getErrorsCount());

      locator.close();

   }

}

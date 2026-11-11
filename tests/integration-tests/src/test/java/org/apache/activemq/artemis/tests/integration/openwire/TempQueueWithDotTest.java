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
package org.apache.activemq.artemis.tests.integration.openwire;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.jupiter.api.Test;

/**
 * This test would fail only if your hostname contains dot on its name. my box name was in the format of xxx-xxx.xxx
 * when it failed.
 */
public class TempQueueWithDotTest extends BasicOpenWireTest {

   @Override
   protected Configuration createDefaultConfig(final int serverID, final boolean netty) throws Exception {
      Configuration configuration = super.createDefaultConfig(serverID, netty);
      configuration.getWildcardConfiguration().setDelimiter('_');
      return configuration;
   }

   /**
    * This fails sometimes on some computers depending on your computer name. It failed for me when I used xxx-xxxx.xxx.
    * As Openwire will use your uname as the temp queue ID.
    */
   @Test
   public void testSimple() throws Exception {
      testSimple("OPENWIRE");
      testSimple("CORE");
   }

   public void testSimple(String protocol) throws Exception {
      ConnectionFactory factory = CFUtil.createConnectionFactory(protocol, getConnectionUrl());
      Connection connection = factory.createConnection();
      try {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue dest = session.createTemporaryQueue();
         String queueName = dest.getQueueName();
         Wait.waitFor(() -> server.locateQueue(queueName) != null);
         org.apache.activemq.artemis.core.server.Queue queue = server.locateQueue(queueName);
         MessageConsumer consumer = null;
         try {
            consumer = session.createConsumer(dest);
         } catch (Exception e) {
            e.printStackTrace();
            // I'm calling fail because openwire sends the stacktrace for the server, not the client in case of a failure
            fail(e.getMessage());
         }

         MessageProducer producer = session.createProducer(dest);
         producer.send(session.createTextMessage("hello"));

         Wait.assertEquals(1, queue::getMessageCount);

         connection.start();

         assertNotNull(consumer.receive(500));
      } finally {
         connection.close();
      }
   }

}

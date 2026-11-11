/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.activemq.artemis.tests.integration.client;

import java.lang.invoke.MethodHandles;

import org.apache.activemq.artemis.api.core.ActiveMQNotConnectedException;
import org.apache.activemq.artemis.api.core.ActiveMQTransactionOutcomeUnknownException;
import org.apache.activemq.artemis.api.core.ActiveMQUnBlockedException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.fail;

public class ConfirmationWindowTest extends ActiveMQTestBase {

   protected ActiveMQServer server;
   private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = createServer(false, createDefaultNettyConfig());
      server.start();
   }

   @Test
   public void testMissingResponse() throws Exception {
      // the test will always succeed if confirmationWindowSize = -1
      final int confirmationWindowSize = 1024 * 1024;

      // use a short callTimeout to speed up the test
      final int callTimeout = 2000;

      final int totalMessagesToSend = 1000;
      final int maxChunkSize = 99;
      String queueName = RandomUtil.randomUUIDString();

      server.createQueue(QueueConfiguration.of(queueName).setAddress(queueName).setRoutingType(RoutingType.ANYCAST));

      /*
       * artificially prevent the broker from responding to the last commit from the client; this will simulate the
       * original error condition
       */
      server.getRemotingService().addIncomingInterceptor(new Interceptor() {
         private int commitCount = 0;
         @Override
         public boolean intercept(Packet packet, RemotingConnection connection) {
            if (packet.getType() == PacketImpl.SESS_COMMIT) {
               if (++commitCount > (totalMessagesToSend / maxChunkSize)) {
                  log.info("Blocking commit");
                  return false;
               }
            }
            return true;
         }
      });

      /*
       * slow down responses for message receipts at the end to help ensure they arrive at the client *after* it sends
       * the last commit packet and begins listening for the commit response; without the fix one of these message
       * receipt responses would be mistaken for the commit response
       */
      server.getRemotingService().addOutgoingInterceptor(new Interceptor() {
         private int responseCount = 0;
         @Override
         public boolean intercept(Packet packet, RemotingConnection connection) {
            if (++responseCount > totalMessagesToSend && packet.getType() == PacketImpl.NULL_RESPONSE && packet.getCorrelationID() > 0) {
               try {
                  log.info("Slowing responses");
                  Thread.sleep(50);
               } catch (InterruptedException e) {
                  throw new RuntimeException(e);
               }
            }
            return true;
         }
      });

      ServerLocator locator = createNonHALocator(true);
      locator.setConfirmationWindowSize(confirmationWindowSize);
      locator.setCallTimeout(callTimeout);

      // send a bunch of messages committing chunks along the way with a small commit at the end for the leftovers
      try (ClientSessionFactory sessionFactory = locator.createSessionFactory();
           ClientSession session = sessionFactory.createSession(false, false, false)) {
         ClientProducer producer = session.createProducer(queueName);
         int chunkSize = 0;
         for (int i = 1; i <= totalMessagesToSend; i++) {
            producer.send(session.createMessage(true));
            chunkSize++;
            if (i % maxChunkSize == 0) {
               log.info("Committing {} messages...", chunkSize);
               session.commit();
               log.info("Commit succeeded.");
               chunkSize = 0;
            }
         }
         if (chunkSize > 0) {
            log.info("Committing {} messages.", chunkSize);
            // this commit will be blocked by the previously defined incoming interceptor
            session.commit();
            fail("Commit should have timed out & failed.");
         }
      } catch (ActiveMQTransactionOutcomeUnknownException | ActiveMQUnBlockedException | ActiveMQNotConnectedException e) {
         // expected
      }
   }
}

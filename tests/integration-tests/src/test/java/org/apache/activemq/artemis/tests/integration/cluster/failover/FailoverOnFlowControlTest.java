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
package org.apache.activemq.artemis.tests.integration.cluster.failover;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Interceptor;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryInternal;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorInternal;
import org.apache.activemq.artemis.core.protocol.core.Packet;
import org.apache.activemq.artemis.core.protocol.core.impl.wireformat.SessionProducerCreditsMessage;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnection;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.tests.util.TransportConfigurationUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class FailoverOnFlowControlTest extends FailoverTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   @Test
   public void testOverflowSend() throws Exception {
      ServerLocator locator = getServerLocator().setBlockOnNonDurableSend(true).setBlockOnDurableSend(true).setReconnectAttempts(300).setProducerWindowSize(1000).setRetryInterval(100);
      final ArrayList<ClientSession> sessionList = new ArrayList<>();
      Interceptor interceptorClient = new Interceptor() {
         AtomicInteger count = new AtomicInteger(0);

         @Override
         public boolean intercept(Packet packet, RemotingConnection connection) throws ActiveMQException {
            logger.debug("Intercept...{}", packet.getClass().getName());

            if (packet instanceof SessionProducerCreditsMessage credit) {

               logger.debug("Credits: {}", credit.getCredits());
               if (count.incrementAndGet() == 2) {
                  logger.debug("### crashing server");
                  try {
                     InVMConnection.setFlushEnabled(false);
                     crash(false, sessionList.get(0));
                  } catch (Exception e) {
                     e.printStackTrace();
                  } finally {
                     InVMConnection.setFlushEnabled(true);
                  }
                  return false;
               }
            }
            return true;
         }
      };

      locator.addIncomingInterceptor(interceptorClient);

      ClientSessionFactoryInternal sf = createSessionFactoryAndWaitForTopology(locator, 2);
      ClientSession session = sf.createSession(true, true);
      sessionList.add(session);

      session.createQueue(QueueConfiguration.of(ADDRESS));

      ClientProducer producer = session.createProducer(ADDRESS);

      final int numMessages = 10;

      for (int i = 0; i < numMessages; i++) {
         ClientMessage message = session.createMessage(true);

         message.getBodyBuffer().writeBytes(new byte[5000]);

         message.putIntProperty("counter", i);

         producer.send(message);
      }

      session.close();
   }

   @Override
   protected void createConfigs() throws Exception {
      super.createConfigs();
      primaryServer.getServer().getConfiguration().setJournalFileSize(1024 * 1024);
      backupServer.getServer().getConfiguration().setJournalFileSize(1024 * 1024);
   }

   @Override
   protected ServerLocatorInternal getServerLocator() throws Exception {
      return (ServerLocatorInternal) super.getServerLocator().setMinLargeMessageSize(1024 * 1024).setProducerWindowSize(10 * 1024);
   }

   @Override
   protected TransportConfiguration getAcceptorTransportConfiguration(final boolean live) {
      return TransportConfigurationUtils.getInVMAcceptor(live);
   }

   @Override
   protected TransportConfiguration getConnectorTransportConfiguration(final boolean live) {
      return TransportConfigurationUtils.getInVMConnector(live);
   }
}

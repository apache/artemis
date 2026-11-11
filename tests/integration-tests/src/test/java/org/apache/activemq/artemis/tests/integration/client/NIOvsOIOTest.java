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
package org.apache.activemq.artemis.tests.integration.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.MessageHandler;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

public class NIOvsOIOTest extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Test
   public void testNIOPerf() throws Exception {
      testPerf(true);
   }

   @Test
   public void testOIOPerf() throws Exception {
      testPerf(false);
   }

   private void doTest(String dest) throws Exception {

      final int numSenders = 1;

      final int numReceivers = 1;

      final int numMessages = 20000;

      Receiver[] receivers = new Receiver[numReceivers];

      Sender[] senders = new Sender[numSenders];

      List<ClientSessionFactory> factories = new ArrayList<>();

      ServerLocator locator = createInVMNonHALocator();

      for (int i = 0; i < numReceivers; i++) {

         ClientSessionFactory sf = createSessionFactory(locator);

         factories.add(sf);

         receivers[i] = new Receiver(i, sf, numMessages * numSenders, dest);

         receivers[i].prepare();

         receivers[i].start();
      }

      for (int i = 0; i < numSenders; i++) {
         ClientSessionFactory sf = createSessionFactory(locator);

         factories.add(sf);

         senders[i] = new Sender(i, sf, numMessages, dest);

         senders[i].prepare();
      }

      long start = System.currentTimeMillis();

      for (int i = 0; i < numSenders; i++) {
         senders[i].start();
      }

      for (int i = 0; i < numSenders; i++) {
         senders[i].join();
      }

      for (int i = 0; i < numReceivers; i++) {
         receivers[i].await();
      }

      long end = System.currentTimeMillis();

      double rate = 1000 * (double) (numMessages * numSenders) / (end - start);

      logAndSystemOut("Rate is " + rate + " msgs sec");

      for (int i = 0; i < numSenders; i++) {
         senders[i].terminate();
      }

      for (int i = 0; i < numReceivers; i++) {
         receivers[i].terminate();
      }

      for (ClientSessionFactory sf : factories) {
         sf.close();
      }

      locator.close();
   }

   private void testPerf(boolean nio) throws Exception {
      Configuration config = createDefaultInVMConfig();

      Map<String, Object> params = new HashMap<>();

      params.put(TransportConstants.USE_NIO_PROP_NAME, nio);

      config.getAcceptorConfigurations().add(new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, params));

      ActiveMQServer server = addServer(ActiveMQServers.newActiveMQServer(config, false));

      AddressSettings addressSettings = new AddressSettings().setAddressFullMessagePolicy(AddressFullMessagePolicy.BLOCK).setMaxSizeBytes(10 * 1024 * 1024);

      final String dest = "test-destination";

      HierarchicalRepository<AddressSettings> repos = server.getAddressSettingsRepository();

      repos.addMatch(dest, addressSettings);

      server.start();

      for (int i = 0; i < 2; i++) {
         doTest(dest);
      }
   }

   private class Sender extends Thread {

      private final ClientSessionFactory sf;

      private final int numMessages;

      private final String dest;

      private ClientSession session;

      private ClientProducer producer;

      private final int id;

      Sender(int id, ClientSessionFactory sf, final int numMessages, final String dest) {
         this.id = id;

         this.sf = sf;

         this.numMessages = numMessages;

         this.dest = dest;
      }

      void prepare() throws Exception {
         session = sf.createSession(true, true);

         producer = session.createProducer(dest);
      }

      @Override
      public void run() {
         ClientMessage msg = session.createMessage(false);

         for (int i = 0; i < numMessages; i++) {
            try {
               producer.send(msg);
            } catch (Exception e) {
               logger.error("Caught exception", e);
            }
         }
      }

      public void terminate() throws Exception {
         session.close();
      }
   }

   private class Receiver implements MessageHandler {

      private final ClientSessionFactory sf;

      private final int numMessages;

      private final String dest;

      private ClientSession session;

      private ClientConsumer consumer;

      private final int id;

      private String queueName;

      Receiver(int id, ClientSessionFactory sf, final int numMessages, final String dest) {
         this.id = id;

         this.sf = sf;

         this.numMessages = numMessages;

         this.dest = dest;
      }

      void prepare() throws Exception {
         session = sf.createSession(true, true, 0);

         queueName = UUIDGenerator.getInstance().generateStringUUID();

         session.createQueue(QueueConfiguration.of(queueName).setAddress(dest).setRoutingType(RoutingType.ANYCAST));

         consumer = session.createConsumer(queueName);

         consumer.setMessageHandler(this);
      }

      void start() throws Exception {
         session.start();
      }

      private final CountDownLatch latch = new CountDownLatch(1);

      void await() throws Exception {
         waitForLatch(latch);
      }

      private int count;

      @Override
      public void onMessage(ClientMessage msg) {
         try {
            msg.acknowledge();
         } catch (Exception e) {
            logger.error("Caught exception", e);
         }

         count++;

         if (count == numMessages) {
            latch.countDown();
         }
      }

      public void terminate() throws Exception {
         consumer.close();

         session.deleteQueue(queueName);

         session.close();
      }
   }

}

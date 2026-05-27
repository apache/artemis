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
package org.apache.activemq.artemis.tests.performance.jmh;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.paging.PagingStore;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 8, time = 1)
public class WildcardHierarchicalMessagingTest {

   private ActiveMQServer server;
   private ServerLocator locator;
   private ClientSessionFactory sessionFactory;

   private int topics = 100;
   private SimpleString[] addresses;
   private List<SimpleString> wildcardLevels = new ArrayList<>();
   private AtomicInteger threadIdCounter = new AtomicInteger(0);

   @Param({"100"})
   int levels;

   @Param({"true", "false"})
   boolean useHierarchy;

   @Setup
   public void init() throws Exception {
      String basicAddress = "a.".repeat(levels - 1);

      Configuration config = new ConfigurationImpl().setSecurityEnabled(false).setPersistenceEnabled(false).setJournalSyncNonTransactional(false).addAcceptorConfiguration(new TransportConfiguration(InVMAcceptorFactory.class.getName()));

      AddressSettings addressSettings = new AddressSettings().setMaxSizeBytes(-1).setPageSizeBytes(10 * 1024 * 1024);

      server = ActiveMQServers.newActiveMQServer(config, false);
      server.getAddressSettingsRepository().addMatch("#", addressSettings);
      server.getConfiguration().getWildcardConfiguration().setAggregateSizes(true).setRoutingEnabled(useHierarchy);
      server.start();

      locator = ActiveMQClient.createServerLocatorWithoutHA(new TransportConfiguration(InVMConnectorFactory.class.getName()));
      sessionFactory = locator.createSessionFactory();

      for (int i = 1; i < levels; i++) {
         StringBuilder levelBuilder = new StringBuilder();
         levelBuilder.append("a.".repeat(i));
         levelBuilder.append("*.".repeat(levels - i - 1) + "*");
         String levelString = levelBuilder.toString();

         wildcardLevels.add(SimpleString.of(levelString));
         server.addAddressInfo(new AddressInfo(SimpleString.of(levelString), RoutingType.ANYCAST));
      }

      addresses = new SimpleString[topics];

      for (int i = 0; i < topics; i++) {
         SimpleString addressName = SimpleString.of(basicAddress + i);
         addresses[i] = addressName;
         server.createQueue(QueueConfiguration.of(addressName).setRoutingType(RoutingType.ANYCAST));
         PagingStore store = server.getPagingManager().getPageStore(addressName);
         if (useHierarchy) {
            if (store.getHierarchy().size() != levels - 1) {
               throw new IllegalStateException("sizes don't match");
            }
         }
      }
   }

   @TearDown
   public void shutdown() throws Exception {
      if (sessionFactory != null) {
         sessionFactory.close();
      }
      if (locator != null) {
         locator.close();
      }
      if (server != null) {
         server.stop();
      }
   }

   @State(value = Scope.Thread)
   public static class ThreadState {

      int threadId;
      long next;
      SimpleString[] addresses;
      ClientSession session;
      ClientProducer producer;
      ClientConsumer consumer;
      SimpleString queueName;

      @Setup
      public void init(WildcardHierarchicalMessagingTest benchmarkState) throws Exception {
         threadId = benchmarkState.threadIdCounter.getAndIncrement();
         addresses = benchmarkState.addresses;
         session = benchmarkState.sessionFactory.createSession(false, true, true);
         queueName = addresses[threadId];
         producer = session.createProducer(queueName);

         // Create a unique queue for this thread
         consumer = session.createConsumer(queueName);
         session.start();
      }

      @TearDown
      public void cleanup() throws Exception {
         if (consumer != null) {
            consumer.close();
         }
         if (producer != null) {
            producer.close();
         }
         if (session != null) {
            session.close();
         }
      }

      public SimpleString nextAddress() {
         final long current = next;
         next = current + 1;
         final int index = (int) (current & (addresses.length - 1));
         return addresses[index];
      }
   }

   @Benchmark
   @Threads(10)
   public ClientMessage testMeasureDepth(ThreadState state) throws Exception {
      ClientMessage message = state.session.createMessage(false);
      message.getBodyBuffer().writeString("test message");

      state.producer.send(message);
      ClientMessage received = state.consumer.receive(1000);

      return received;
   }

}

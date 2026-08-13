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

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.JournalType;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 10)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class TransactionalMessagePerfTest {

   private static final int MESSAGE_SIZE = 100;
   private static final SimpleString QUEUE_NAME = SimpleString.of("perfQueue");
   private static final SimpleString ADDRESS_NAME = SimpleString.of("perfAddress");

   @Param({"NIO", "ASYNCIO", "ASYNCIO_2"})
   private String journalTypeParam;

   private ActiveMQServer server;
   private ServerLocator locator;
   private ClientSessionFactory sessionFactory;
   private ClientSession producerSession;
   private ClientSession consumerSession;
   private ClientProducer producer;
   private ClientConsumer consumer;
   private File dataDir;
   private byte[] messageBody;

   @Setup(Level.Trial)
   public void setup() throws Exception {
      dataDir = new File(System.getProperty("java.io.tmpdir"), "perf-test-" + System.currentTimeMillis());
      dataDir.mkdirs();

      JournalType journalType = JournalType.getType(journalTypeParam);

      Configuration config = new ConfigurationImpl()
         .setJournalDirectory(new File(dataDir, "journal").getAbsolutePath())
         .setBindingsDirectory(new File(dataDir, "bindings").getAbsolutePath())
         .setPagingDirectory(new File(dataDir, "paging").getAbsolutePath())
         .setLargeMessagesDirectory(new File(dataDir, "large-messages").getAbsolutePath())
         .setJournalType(journalType)
         .setJournalSyncNonTransactional(false)
         .setJournalSyncTransactional(true)
         .setPersistenceEnabled(true)
         .setSecurityEnabled(false)
         .addAcceptorConfiguration("invm", "vm://0");

      server = new ActiveMQServerImpl(config);
      server.start();

      locator = ActiveMQClient.createServerLocator("vm://0");
      sessionFactory = locator.createSessionFactory();

      ClientSession setupSession = sessionFactory.createSession(false, false, false);
      setupSession.createQueue(QueueConfiguration.of(QUEUE_NAME).setAddress(ADDRESS_NAME).setRoutingType(RoutingType.ANYCAST));
      setupSession.close();

      producerSession = sessionFactory.createSession(false, false, false);
      consumerSession = sessionFactory.createSession(false, true, true);

      producer = producerSession.createProducer(ADDRESS_NAME);
      consumer = consumerSession.createConsumer(QUEUE_NAME);
      consumerSession.start();

      messageBody = new byte[MESSAGE_SIZE];
   }

   @Benchmark
   public void sendAndConsume() throws Exception {
      ClientMessage message = producerSession.createMessage(true);
      message.getBodyBuffer().writeBytes(messageBody);
      producer.send(message);
      producerSession.commit();

      ClientMessage receivedMessage = consumer.receive(5000);
      if (receivedMessage != null) {
         receivedMessage.acknowledge();
      }
   }

   @TearDown(Level.Trial)
   public void tearDown() throws Exception {
      if (consumer != null) {
         consumer.close();
      }
      if (producer != null) {
         producer.close();
      }
      if (consumerSession != null) {
         consumerSession.close();
      }
      if (producerSession != null) {
         producerSession.close();
      }
      if (sessionFactory != null) {
         sessionFactory.close();
      }
      if (locator != null) {
         locator.close();
      }
      if (server != null) {
         server.stop();
      }
      if (dataDir != null && dataDir.exists()) {
         deleteDirectory(dataDir);
      }
   }

   private void deleteDirectory(File directory) {
      File[] files = directory.listFiles();
      if (files != null) {
         for (File file : files) {
            if (file.isDirectory()) {
               deleteDirectory(file);
            } else {
               file.delete();
            }
         }
      }
      directory.delete();
   }
}

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
package org.apache.activemq.artemis.tests.soak.mqtt.resiliency;

import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import io.reactivex.schedulers.Schedulers;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.persistence.impl.journal.JournalRecordIds;
import org.apache.activemq.artemis.core.postoffice.DuplicateIDCache;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTProtocolManager;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTUtil;
import org.apache.activemq.artemis.core.protocol.mqtt.PacketIdCache;
import org.apache.activemq.artemis.core.remoting.impl.AbstractAcceptor;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManager;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;
import org.apache.activemq.artemis.tests.soak.mqtt.resiliency.client.Mqtt3TestClient;
import org.apache.activemq.artemis.tests.soak.mqtt.resiliency.client.Mqtt5TestClient;
import org.apache.activemq.artemis.tests.soak.mqtt.resiliency.client.MqttTestClient;
import org.apache.activemq.artemis.tests.soak.mqtt.resiliency.client.MqttVersion;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.Wait;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.activemq.artemis.cli.commands.tools.journal.CompactJournal.compactJournal;
import static org.apache.activemq.artemis.core.persistence.impl.journal.JournalStorageManager.ACTIVEMQ_DATA;
import static org.apache.activemq.artemis.core.protocol.mqtt.MQTTProtocolManagerFactory.MQTT_PROTOCOL_NAME;

/**
 * All tests which extend this use the HiveMQ MQTT client because it is the most robust with regard to QoS2 message
 * flows and error handling. The Paho client had issues which made it unacceptable for these tests.
 */
public class QoS2ResiliencySoakTestSupport extends ActiveMQTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   protected static final String TOPIC = "qos2/resiliency";
   protected static final int MQTT_PORT = 1883;

   protected ActiveMQServer server;

   // every client created via createClient() is tracked here so tearDown() can disconnect them
   // while the broker and RxJava schedulers are still alive, preventing auto-reconnect clients
   // from lingering and contaminating the next test (they share port 1883 and client-id prefixes)
   private final List<MqttTestClient> createdClients = new CopyOnWriteArrayList<>();

   @BeforeEach
   @Override
   public void setUp() throws Exception {
      super.setUp();
      Schedulers.start();
      server = createServer(true, createDefaultConfig(true));
      server.getConfiguration().setJournalMinFiles(10).setJournalFileSize(25 * 1024 * 1024);
      server.getConfiguration().addAcceptorConfiguration(MQTT_PROTOCOL_NAME, "tcp://localhost:" + MQTT_PORT + "?protocols=MQTT");
      server.getConfiguration().setMqttSessionScanInterval(200);

      server.start();
      server.waitForActivation(10, TimeUnit.SECONDS);
   }

   @AfterEach
   @Override
   public void tearDown() throws Exception {
      // disconnect all clients first, while the broker and RxJava schedulers are still alive, so
      // auto-reconnect clients don't linger in a reconnect loop and latch onto the next test's broker
      for (MqttTestClient client : createdClients) {
         try {
            client.disconnect();
         } catch (Exception ignored) {
         }
      }
      createdClients.clear();
      if (server != null && server.isStarted()) {
         server.stop();
      }
      Schedulers.shutdown();
      super.tearDown();
   }

   private static void enableProtocolLogging() {
      LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
      org.apache.logging.log4j.core.config.Configuration config = ctx.getConfiguration();
      LoggerConfig loggerConfig = new LoggerConfig(MQTTUtil.class.getName(), Level.TRACE, true);
      config.addLogger(MQTTUtil.class.getName(), loggerConfig);
      ctx.updateLoggers();
   }

   protected static void disableProtocolLogging() {
      LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
      org.apache.logging.log4j.core.config.Configuration config = ctx.getConfiguration();
      config.removeLogger(MQTTUtil.class.getName());
      ctx.updateLoggers();
   }

   protected Set<String> getMissingMessages(Set<String> expected, Set<String> received) {
      Set<String> missing = new HashSet<>(expected);
      missing.removeAll(received);
      return missing;
   }

   protected MqttTestClient createClient(MqttVersion version, String clientId, boolean autoReconnect) {
      MqttTestClient client = switch (version) {
         case MQTT3 -> {
            var builder = Mqtt3Client.builder()
               .identifier(clientId)
               .serverHost("localhost")
               .serverPort(MQTT_PORT);
            if (autoReconnect) {
               builder.automaticReconnect()
                  .initialDelay(500, TimeUnit.MILLISECONDS)
                  .maxDelay(500, TimeUnit.MILLISECONDS)
                  .applyAutomaticReconnect();
            }
            yield new Mqtt3TestClient(builder.buildBlocking());
         }
         case MQTT5 -> {
            var builder = Mqtt5Client.builder()
               .identifier(clientId)
               .serverHost("localhost")
               .serverPort(MQTT_PORT);
            if (autoReconnect) {
               builder.automaticReconnect()
                  .initialDelay(500, TimeUnit.MILLISECONDS)
                  .maxDelay(500, TimeUnit.MILLISECONDS)
                  .applyAutomaticReconnect();
            }
            yield new Mqtt5TestClient(builder.buildBlocking());
         }
      };
      createdClients.add(client);
      return client;
   }

   protected static void waitForClientConnected(MqttTestClient client) {
      Wait.waitFor(client::isConnected, 30_000, 500);
   }

   protected static void cleanDisconnect(MqttTestClient client) {
      logger.info("cleanDisconnect for {}", client.getClientId());
      try {
         if (client.isConnected()) {
            client.disconnect();
         }
         client.connectClean();
         client.disconnect();
      } catch (Exception e) {
         logger.debug("Error disconnecting: {}", e.getMessage());
      }
   }

   protected MQTTProtocolManager getProtocolManager() {
      Acceptor acceptor = server.getRemotingService().getAcceptor(MQTT_PROTOCOL_NAME);
      if (acceptor instanceof AbstractAcceptor abstractAcceptor) {
         ProtocolManager protocolManager = abstractAcceptor.getProtocolMap().get(MQTT_PROTOCOL_NAME);
         if (protocolManager instanceof MQTTProtocolManager mqttProtocolManager) {
            return mqttProtocolManager;
         }
      }
      return null;
   }

   protected DuplicateIDCache getPubCache(String clientId) {
      return getCache(clientId, PacketIdCache.TYPE.PUBLISH);
   }

   protected int getPubCacheSize(String clientId) {
      DuplicateIDCache cache = getPubCache(clientId);
      return cache == null ? 0 : cache.getMap().size();
   }

   protected DuplicateIDCache getSubCache(String clientId) {
      return getCache(clientId, PacketIdCache.TYPE.PUBREC);
   }

   protected int getSubCacheSize(String clientId) {
      DuplicateIDCache cache = getSubCache(clientId);
      return cache == null ? 0 : cache.getMap().size();
   }

   private DuplicateIDCache getCache(String clientId, PacketIdCache.TYPE type) {
      SimpleString cacheName = PacketIdCache.getCacheName(server.getInternalNamingPrefix(), clientId, type);
      if (server.getPostOffice().duplicateIDCacheExists(cacheName)) {
         return server.getPostOffice().getDuplicateIDCache(cacheName);
      }
      return null;
   }

   protected Queue getSubscriptionQueue(String mqttTopicFilter, String clientId) {
      return server.locateQueue(MQTTUtil.getCoreQueueFromMqttTopic(mqttTopicFilter, clientId, server.getConfiguration().getWildcardConfiguration()));
   }

   protected record BrokerRestartTask(ScheduledExecutorService scheduler, ScheduledFuture<?> future) {}

   protected BrokerRestartTask startBrokerRestartTask(long delayMillis, Runnable restartLogger) {
      ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
      ScheduledFuture<?> future = scheduler.scheduleWithFixedDelay(() -> {
         try {
            if (restartLogger != null) {
               restartLogger.run();
            }
            logger.info("Stopping broker");
            server.stop();
            waitForServerToStop(server);

            logger.info("Compacting journal...");
            compactJournal(server.getConfiguration().getJournalLocation(), server.getConfiguration().getJournalRetentionLocation(), ACTIVEMQ_DATA, "amq", server.getConfiguration().getJournalMinFiles(),
                           server.getConfiguration().getJournalPoolFiles(), server.getConfiguration().getJournalFileSize(), null, JournalRecordIds.UPDATE_DELIVERY_COUNT,
                           JournalRecordIds.SET_SCHEDULED_DELIVERY_TIME);
            logger.info("Compacted journal.");

            server.start();
            waitForServerToStart(server);
         } catch (Exception e) {
            logger.warn("Error during broker restart", e);
         }
      }, delayMillis, delayMillis, TimeUnit.MILLISECONDS);
      return new BrokerRestartTask(scheduler, future);
   }

   protected void stopBrokerRestartTask(BrokerRestartTask restartTask) throws Exception {
      restartTask.future().cancel(true);
      restartTask.scheduler().shutdownNow();
      restartTask.scheduler().awaitTermination(10, TimeUnit.SECONDS);
      if (!server.isStarted()) {
         server.start();
         waitForServerToStart(server);
      }
   }

   protected record PublishResult(ExecutorService executor, Set<String> sentMessages, AtomicInteger publishErrors) {}

   protected PublishResult startPublishing(List<MqttTestClient> publishers, int numMessages) {
      final ExecutorService publisherExecutor = Executors.newFixedThreadPool(publishers.size());
      runAfter(() -> {
         publisherExecutor.shutdownNow();
         try {
            publisherExecutor.awaitTermination(10, TimeUnit.SECONDS);
         } catch (InterruptedException ignored) {
         }
      });
      final Set<String> sentMessages = ConcurrentHashMap.newKeySet(publishers.size() * numMessages);
      final AtomicInteger publishErrors = new AtomicInteger(0);
      for (int i = 0; i < publishers.size(); i++) {
         final int pubId = i;
         final MqttTestClient publisher = publishers.get(i);
         publisherExecutor.execute(() -> {
            for (int seq = 0; seq < numMessages; seq++) {
               String payload = pubId + "-" + seq;
               try {
                  waitForClientConnected(publisher);
                  publisher.publish(TOPIC, MqttQos.EXACTLY_ONCE, payload.getBytes(StandardCharsets.UTF_8));
               } catch (Throwable e) {
                  publishErrors.incrementAndGet();
                  logger.info("Pub failed: {}; in-flight QoS 2 state will be resumed on reconnect", payload, e);
               }
               sentMessages.add(payload);
            }
            logger.info("Publisher {} finished sending all {} messages", pubId, numMessages);
         });
      }
      return new PublishResult(publisherExecutor, sentMessages, publishErrors);
   }
}

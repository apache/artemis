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
package org.apache.activemq.artemis.tests.integration.plugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.stream.Collectors;

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.config.MetricsConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.metrics.plugins.SimpleMetricsPlugin;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MetricsPluginTest extends ActiveMQTestBase {

   protected ActiveMQServer server;
   protected ClientSession session;
   protected ClientSessionFactory sf;
   protected ServerLocator locator;
   private static final String UUID_NAMESPACE = "uuid";

   protected void configureServer(ActiveMQServer server) {
      server.getConfiguration()
         .setUuidNamespace(UUID_NAMESPACE)
         .setMetricsConfiguration(new MetricsConfiguration().setPlugin(new SimpleMetricsPlugin().init(null)));
   }

   @Override
   @BeforeEach
   public void setUp() throws Exception {
      super.setUp();
      server = createServer(false, createDefaultInVMConfig());
      configureServer(server);
      server.start();
      locator = createInVMNonHALocator();
      sf = createSessionFactory(locator);
      session = addClientSession(sf.createSession(false, true, true));
   }

   @Test
   public void testForArtemisMetricsPresence() throws Exception {

      final String queueName = "simpleQueue";
      final String addressName = "simpleAddress";
      session.createQueue(QueueConfiguration.of(queueName).setAddress(addressName).setRoutingType(RoutingType.ANYCAST));

      Matcher<Iterable<? extends Metric>> anyOrder = containsInAnyOrder(
         // broker metrics
         new Metric("artemis.address.memory.usage", 0.0, Arrays.asList(Tag.of("broker", "localhost"))),
         new Metric("artemis.address.memory.usage.percentage", 0.0, Arrays.asList(Tag.of("broker", "localhost"))),
         new Metric("artemis.connection.count", 1.0, Arrays.asList(Tag.of("broker", "localhost"))),
         new Metric("artemis.total.connection.count", 1.0, Arrays.asList(Tag.of("broker", "localhost"))),
         new Metric("artemis.session.count", 1.0, Arrays.asList(Tag.of("broker", "localhost"))),
         new Metric("artemis.total.session.count", 1.0, Arrays.asList(Tag.of("broker", "localhost"))),
         new Metric("artemis.active", 1.0, Arrays.asList(Tag.of("broker", "localhost"))),
         new Metric("artemis.replica.sync", 0.0, Arrays.asList(Tag.of("broker", "localhost"))),
         new Metric("artemis.disk.store.usage", 0.0, Arrays.asList(Tag.of("broker", "localhost"))),
         new Metric("artemis.authentication.count", 0.0, Arrays.asList(Tag.of("broker", "localhost"), Tag.of("result", "success"))),
         new Metric("artemis.authentication.count", 0.0, Arrays.asList(Tag.of("broker", "localhost"), Tag.of("result", "failure"))),
         new Metric("artemis.authorization.count", 0.0, Arrays.asList(Tag.of("broker", "localhost"), Tag.of("result", "success"))),
         new Metric("artemis.authorization.count", 0.0, Arrays.asList(Tag.of("broker", "localhost"), Tag.of("result", "failure"))),
         // simpleQueue metrics
         new Metric("artemis.message.count", 0.0, Arrays.asList(Tag.of("address", "simpleAddress"), Tag.of("broker", "localhost"), Tag.of("queue", "simpleQueue"))),
         new Metric("artemis.durable.message.count", 0.0, Arrays.asList(Tag.of("address", "simpleAddress"), Tag.of("broker", "localhost"), Tag.of("queue", "simpleQueue"))),
         new Metric("artemis.persistent.size", 0.0, Arrays.asList(Tag.of("address", "simpleAddress"), Tag.of("broker", "localhost"), Tag.of("queue", "simpleQueue"))),
         new Metric("artemis.durable.persistent.size", 0.0, Arrays.asList(Tag.of("address", "simpleAddress"), Tag.of("broker", "localhost"), Tag.of("queue", "simpleQueue"))),
         new Metric("artemis.delivering.message.count", 0.0, Arrays.asList(Tag.of("address", "simpleAddress"), Tag.of("broker", "localhost"), Tag.of("queue", "simpleQueue"))),
         new Metric("artemis.delivering.durable.message.count", 0.0, Arrays.asList(Tag.of("address", "simpleAddress"), Tag.of("broker", "localhost"), Tag.of("queue", "simpleQueue"))),
         new Metric("artemis.delivering.persistent_size", 0.0, Arrays.asList(Tag.of("address", "simpleAddress"), Tag.of("broker", "localhost"), Tag.of("queue", "simpleQueue"))),
         new Metric("artemis.delivering.durable.persistent.size", 0.0, Arrays.asList(Tag.of("address", "simpleAddress"), Tag.of("broker", "localhost"), Tag.of("queue", "simpleQueue"))),
         new Metric("artemis.scheduled.message.count", 0.0, Arrays.asList(Tag.of("address", "simpleAddress"), Tag.of("broker", "localhost"), Tag.of("queue", "simpleQueue"))),
         new Metric("artemis.scheduled.durable.message.count", 0.0, Arrays.asList(Tag.of("address", "simpleAddress"), Tag.of("broker", "localhost"), Tag.of("queue", "simpleQueue"))),
         new Metric("artemis.scheduled.persistent.size", 0.0, Arrays.asList(Tag.of("address", "simpleAddress"), Tag.of("broker", "localhost"), Tag.of("queue", "simpleQueue"))),
         new Metric("artemis.scheduled.durable.persistent.size", 0.0, Arrays.asList(Tag.of("address", "simpleAddress"), Tag.of("broker", "localhost"), Tag.of("queue", "simpleQueue"))),
         new Metric("artemis.messages.acknowledged", 0.0, Arrays.asList(Tag.of("address", "simpleAddress"), Tag.of("broker", "localhost"), Tag.of("queue", "simpleQueue"))),
         new Metric("artemis.messages.added", 0.0, Arrays.asList(Tag.of("address", "simpleAddress"), Tag.of("broker", "localhost"), Tag.of("queue", "simpleQueue"))),
         new Metric("artemis.messages.killed", 0.0, Arrays.asList(Tag.of("address", "simpleAddress"), Tag.of("broker", "localhost"), Tag.of("queue", "simpleQueue"))),
         new Metric("artemis.messages.expired", 0.0, Arrays.asList(Tag.of("address", "simpleAddress"), Tag.of("broker", "localhost"), Tag.of("queue", "simpleQueue"))),
         new Metric("artemis.consumer.count", 0.0, Arrays.asList(Tag.of("address", "simpleAddress"), Tag.of("broker", "localhost"), Tag.of("queue", "simpleQueue"))),
         // simpleAddress metrics
         new Metric("artemis.routed.message.count", 0.0, Arrays.asList(Tag.of("address", "simpleAddress"), Tag.of("broker", "localhost"))),
         new Metric("artemis.unrouted.message.count", 0.0, Arrays.asList(Tag.of("address", "simpleAddress"), Tag.of("broker", "localhost"))),
         new Metric("artemis.address.size", 0.0, Arrays.asList(Tag.of("address", "simpleAddress"), Tag.of("broker", "localhost"))),
         new Metric("artemis.number.of.pages", 0.0, Arrays.asList(Tag.of("address", "simpleAddress"), Tag.of("broker", "localhost"))),
         new Metric("artemis.limit.percent", 0.0, Arrays.asList(Tag.of("address", "simpleAddress"), Tag.of("broker", "localhost"))),
         // activemq.notifications metrics
         new Metric("artemis.routed.message.count", 0.0, Arrays.asList(Tag.of("address", "activemq.notifications"), Tag.of("broker", "localhost"))),
         new Metric("artemis.unrouted.message.count", 2.0, Arrays.asList(Tag.of("address", "activemq.notifications"), Tag.of("broker", "localhost"))),
         new Metric("artemis.address.size", 0.0, Arrays.asList(Tag.of("address", "activemq.notifications"), Tag.of("broker", "localhost"))),
         new Metric("artemis.number.of.pages", 0.0, Arrays.asList(Tag.of("address", "activemq.notifications"), Tag.of("broker", "localhost"))),
         new Metric("artemis.limit.percent", 0.0, Arrays.asList(Tag.of("address", "activemq.notifications"), Tag.of("broker", "localhost")))
      );

      // Making sure any async operations will have time to fill up the data structure
      Wait.waitFor(() -> anyOrder.matches(parseMetrics()), 10_000, 100);
      // doing the actual assertion. I do this in two steps for proper formatting on junit as an assertion error
      assertThat(parseMetrics(), anyOrder);
   }

   class Metric {
      public final String name;
      public final Double value;
      public final List<Tag> tags;

      private Metric(String name, Double value) {
         this(name, value, Collections.emptyList());
      }

      private Metric(String name, Double value, List<Tag> tags) {
         this.name = name;
         this.value = value;
         this.tags = tags;
      }

      @Override
      public String toString() {
         return name + ": " + value + ": " + tags;
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj) {
            return true;
         }
         if (!(obj instanceof Metric other)) {
            return false;
         }

         return Objects.equals(name, other.name) &&
            Objects.equals(value, other.value) &&
            Objects.equals(tags, other.tags);
      }

      @Override
      public int hashCode() {
         return Objects.hash(name, value, tags);
      }
   }

   private List<Metric> parseMetrics() {
      Map<Meter.Id, Double> metrics = getMetrics();
      List<Metric> artemisMetrics = metrics.entrySet().stream()
              .map(entry -> new Metric(
                      entry.getKey().getName(),
                      entry.getValue(),
                      entry.getKey().getTags()))
              .filter(metric -> metric.name.startsWith("artemis"))
              .collect(Collectors.toList());
      return artemisMetrics;
   }

   @Test
   public void testForBasicQueueMetricsPresenceAndValue() throws Exception {
      internalTestForBasicQueueMetrics(true, false);
   }

   @Test
   public void testDisablingQueueMetrics() throws Exception {
      internalTestForBasicQueueMetrics(false, false);
   }

   @Test
   public void testForBasicUuidQueueMetricsPresenceAndValue() throws Exception {
      internalTestForBasicQueueMetrics(true, true);
   }

   @Test
   public void testDisablingUuidQueueMetrics() throws Exception {
      internalTestForBasicQueueMetrics(false, true);
   }

   private void internalTestForBasicQueueMetrics(boolean enabled, boolean uuid) throws Exception {
      final String data = "Simple Text " + UUID.randomUUID().toString();
      final String queueName = "simpleQueue";
      final String addressName = uuid ? RandomUtil.randomUUIDString() : "simpleAddress";

      server.getAddressSettingsRepository().getMatch(uuid ? UUID_NAMESPACE + "." + addressName : addressName).setEnableMetrics(enabled);

      session.createQueue(QueueConfiguration.of(queueName).setAddress(addressName).setRoutingType(RoutingType.ANYCAST).setDurable(!uuid));
      ClientProducer producer = session.createProducer(addressName);
      ClientMessage message = session.createMessage(true);
      message.getBodyBuffer().writeString(data);
      producer.send(message);
      producer.close();

      Queue queue = server.locateQueue(queueName);
      Wait.assertEquals(1, queue::getMessageCount);

      Map<Meter.Id, Double> metrics = getMetrics();

      checkMetric(metrics, "artemis.message.count", "queue", queueName, 1.0, enabled);
      checkMetric(metrics, "artemis.messages.added", "queue", queueName, 1.0, enabled);
      checkMetric(metrics, "artemis.messages.acknowledged", "queue", queueName, 0.0, enabled);
      if (!uuid) {
         checkMetric(metrics, "artemis.durable.message.count", "queue", queueName, 1.0, enabled);
      }
      checkMetric(metrics, "artemis.delivering.message.count", "queue", queueName, 0.0, enabled);
      checkMetric(metrics, "artemis.routed.message.count", "address", addressName, 1.0, enabled);
      checkMetric(metrics, "artemis.unrouted.message.count", "address", addressName, 0.0, enabled);
      checkMetric(metrics, "artemis.consumer.count", "queue", queueName, 0.0, enabled);

      ClientConsumer consumer = session.createConsumer(queueName);
      session.start();
      message = consumer.receive(1000);
      assertNotNull(message);

      metrics = getMetrics();
      checkMetric(metrics, "artemis.delivering.message.count", "queue", queueName, 1.0, enabled);
      checkMetric(metrics, "artemis.consumer.count", "queue", queueName, 1.0, enabled);

      message.acknowledge();
      assertEquals(data, message.getBodyBuffer().readString());
      session.commit(); // force the ack to be committed

      assertTrue(Wait.waitFor(() -> server.locateQueue(SimpleString.of(queueName)).getMessagesAcknowledged() == 1, 1000, 100));

      consumer.close();

      metrics = getMetrics();

      checkMetric(metrics, "artemis.message.count", "queue", queueName, 0.0, enabled);
      checkMetric(metrics, "artemis.messages.added", "queue", queueName, 1.0, enabled);
      checkMetric(metrics, "artemis.messages.acknowledged", "queue", queueName, 1.0, enabled);
      checkMetric(metrics, "artemis.durable.message.count", "queue", queueName, 0.0, enabled);
      checkMetric(metrics, "artemis.delivering.message.count", "queue", queueName, 0.0, enabled);
      checkMetric(metrics, "artemis.routed.message.count", "address", addressName, 1.0, enabled);
      checkMetric(metrics, "artemis.unrouted.message.count", "address", addressName, 0.0, enabled);
      checkMetric(metrics, "artemis.consumer.count", "queue", queueName, 0.0, enabled);
   }

   @Test
   public void testMessageCountWithPaging() throws Exception {
      final String data = "Simple Text " + UUID.randomUUID().toString();
      final String queueName = "simpleQueue";
      final String addressName = "simpleAddress";

      server.getAddressSettingsRepository().getMatch(addressName).setAddressFullMessagePolicy(AddressFullMessagePolicy.PAGE).setMaxSizeBytes(1024 * 10).setPageSizeBytes(1024 * 5);

      session.createQueue(QueueConfiguration.of(queueName).setAddress(addressName).setRoutingType(RoutingType.ANYCAST));
      ClientProducer producer = session.createProducer(addressName);
      ClientMessage message = session.createMessage(true);
      message.getBodyBuffer().writeString(data);
      long messageCount = 0;
      while (!server.getPagingManager().getPageStore(SimpleString.of(addressName)).isPaging()) {
         producer.send(message);
         messageCount++;
      }

      Wait.assertEquals(messageCount, server.locateQueue(queueName)::getMessageCount, 2000, 100);
      checkMetric(getMetrics(), "artemis.message.count", "queue", queueName, (double) messageCount);

      for (int i = 0; i < messageCount; i++) {
         producer.send(message);
      }
      producer.close();

      Wait.assertEquals(messageCount * 2, server.locateQueue(queueName)::getMessageCount, 2000, 100);
      checkMetric(getMetrics(), "artemis.message.count", "queue", queueName, (double) (messageCount * 2));
   }

   @Test
   public void testMetricsPluginRegistration() {
      assertEquals(SimpleMetricsPlugin.class, server.getConfiguration().getMetricsConfiguration().getPlugin().getClass());
      assertEquals(server, ((SimpleMetricsPlugin)server.getConfiguration().getMetricsConfiguration().getPlugin()).getServer());
   }

   public Map<Meter.Id, Double> getMetrics() {
      return getMetrics(server);
   }

   public static Map<Meter.Id, Double> getMetrics(ActiveMQServer server) {
      Map<Meter.Id, Double> metrics = new HashMap<>();
      List<Meter> meters = server.getMetricsManager().getMeterRegistry().getMeters();
      assertFalse(meters.isEmpty());
      for (Meter meter : meters) {
         Iterable<Measurement> measurements = meter.measure();
         for (Measurement measurement : measurements) {
            metrics.put(meter.getId(), measurement.getValue());
         }
      }
      return metrics;
   }

   public void checkMetric(Map<Meter.Id, Double> metrics, String metric, String tag, String tagValue, Double expectedValue) {
      checkMetric(metrics, metric, tag, tagValue, expectedValue, true);
   }

   public void checkMetric(Map<Meter.Id, Double> metrics, String metric, String tag, String tagValue, Double expectedValue, boolean enabled) {
      OptionalDouble actualValue = metrics.entrySet().stream()
              .filter(entry -> metric.equals(entry.getKey().getName()))
              .filter(entry -> tagValue.equals(entry.getKey().getTag(tag)))
              .mapToDouble(Map.Entry::getValue)
              .findFirst();

      if (enabled) {
         assertTrue(actualValue.isPresent(), metric + " for " + tag + " " + tagValue + " not present");
         assertEquals(expectedValue, actualValue.getAsDouble(), 0, metric + " not equal");
      } else {
         assertFalse(actualValue.isPresent(), metric + " for " + tag + " " + tagValue + " present");
      }
   }
}

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
package org.apache.activemq.artemis.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.mcp.admin.ArtemisAdminClient;
import org.apache.activemq.artemis.mcp.config.ArtemisMcpConfig;
import org.apache.activemq.artemis.mcp.config.ArtemisMcpConfig.Mode;
import org.apache.activemq.artemis.mcp.connection.ArtemisConnectionManager;
import org.apache.activemq.artemis.mcp.management.ArtemisManagementClient;
import org.apache.activemq.artemis.mcp.messaging.ArtemisBrowseClient;
import org.apache.activemq.artemis.mcp.messaging.ArtemisMessagingClient;
import org.apache.activemq.artemis.mcp.tools.ArtemisMcpTools;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ArtemisMcpIntegrationTest {

   private static final String BROKER_URL = "tcp://localhost:61716";
   private static final String TEST_QUEUE = "mcp.test.queue";
   private static final int MESSAGE_COUNT = 5;

   private EmbeddedActiveMQ broker;
   private ArtemisConnectionManager connectionManager;
   private ArtemisManagementClient management;
   private ArtemisBrowseClient browse;
   private ArtemisMcpTools tools;

   @BeforeAll
   public void startBrokerAndSeed() throws Exception {
      Configuration configuration = new ConfigurationImpl()
         .setPersistenceEnabled(false)
         .setSecurityEnabled(false)
         .setJMXManagementEnabled(false)
         .addAcceptorConfiguration("tcp", BROKER_URL);
      broker = new EmbeddedActiveMQ();
      broker.setConfiguration(configuration);
      broker.start();

      ArtemisMcpConfig config = new ArtemisMcpConfig(BROKER_URL, null, null, Mode.READ_ONLY, 50);
      connectionManager = new ArtemisConnectionManager(config);
      management = new ArtemisManagementClient(connectionManager);
      browse = new ArtemisBrowseClient(connectionManager);
      tools = new ArtemisMcpTools(config, management, browse,
         new ArtemisMessagingClient(connectionManager), new ArtemisAdminClient(connectionManager));

      seedMessages();
   }

   @AfterAll
   public void stop() throws Exception {
      if (connectionManager != null) {
         connectionManager.close();
      }
      if (broker != null) {
         broker.stop();
      }
   }

   private void seedMessages() throws Exception {
      try (Connection connection = connectionManager.newStartedConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(TEST_QUEUE);
         MessageProducer producer = session.createProducer(queue);
         for (int i = 0; i < MESSAGE_COUNT; i++) {
            producer.send(session.createTextMessage("message-" + i));
         }
      }
   }

   @Test
   public void listQueuesIncludesTestQueue() throws Exception {
      assertTrue(management.listQueues().contains(TEST_QUEUE),
         "expected listQueues to include " + TEST_QUEUE);
   }

   @Test
   public void queueStatsReportSeededCount() throws Exception {
      Map<String, Object> stats = management.queueStats(TEST_QUEUE);
      assertEquals((long) MESSAGE_COUNT, ((Number) stats.get("messageCount")).longValue());
      assertEquals(TEST_QUEUE, stats.get("name"));
   }

   @Test
   public void brokerOverviewReportsVersion() throws Exception {
      Map<String, Object> overview = management.brokerOverview();
      assertNotNull(overview.get("version"));
      assertTrue(((Number) overview.get("totalMessageCount")).longValue() >= MESSAGE_COUNT);
   }

   @Test
   public void browseReturnsMessagesWithoutConsuming() throws Exception {
      List<Map<String, Object>> first = browse.browse(TEST_QUEUE, 10, null);
      assertEquals(MESSAGE_COUNT, first.size());
      assertEquals("message-0", first.get(0).get("body"));
      assertEquals(MESSAGE_COUNT, browse.browse(TEST_QUEUE, 10, null).size());
   }

   @Test
   public void listQueuesToolReturnsJsonResult() {
      CallToolResult result = invoke("list_queues", Map.of());
      assertFalse(Boolean.TRUE.equals(result.isError()));
      assertTrue(textOf(result).contains(TEST_QUEUE));
   }

   @Test
   public void getQueueStatsToolUsesArgument() {
      CallToolResult result = invoke("get_queue_stats", Map.of("queue", TEST_QUEUE));
      assertFalse(Boolean.TRUE.equals(result.isError()));
      assertTrue(textOf(result).contains("\"messageCount\":" + MESSAGE_COUNT));
   }

   @Test
   public void missingRequiredArgumentReportsError() {
      CallToolResult result = invoke("get_queue_stats", Map.of());
      assertTrue(Boolean.TRUE.equals(result.isError()));
   }

   private CallToolResult invoke(String toolName, Map<String, Object> arguments) {
      SyncToolSpecification spec = tools.specifications().stream()
         .filter(s -> s.tool().name().equals(toolName))
         .findFirst()
         .orElseThrow(() -> new AssertionError("tool not found: " + toolName));
      return spec.callHandler().apply(null, new CallToolRequest(toolName, arguments));
   }

   private static String textOf(CallToolResult result) {
      return result.content().stream()
         .filter(c -> c instanceof TextContent)
         .map(c -> ((TextContent) c).text())
         .reduce("", String::concat);
   }
}

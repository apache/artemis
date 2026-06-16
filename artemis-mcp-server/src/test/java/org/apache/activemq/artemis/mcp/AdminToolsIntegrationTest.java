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
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
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
public class AdminToolsIntegrationTest {

   private static final String BROKER_URL = "tcp://localhost:61717";

   private EmbeddedActiveMQ broker;
   private ArtemisConnectionManager connectionManager;
   private ArtemisManagementClient management;
   private ArtemisAdminClient admin;
   private ArtemisMcpTools adminTools;

   @BeforeAll
   public void startBroker() throws Exception {
      Configuration configuration = new ConfigurationImpl()
         .setPersistenceEnabled(false)
         .setSecurityEnabled(false)
         .setJMXManagementEnabled(false)
         .addAcceptorConfiguration("tcp", BROKER_URL);
      broker = new EmbeddedActiveMQ();
      broker.setConfiguration(configuration);
      broker.start();

      ArtemisMcpConfig config = new ArtemisMcpConfig(BROKER_URL, null, null, Mode.ADMIN, 50);
      connectionManager = new ArtemisConnectionManager(config);
      management = new ArtemisManagementClient(connectionManager);
      admin = new ArtemisAdminClient(connectionManager);
      ArtemisBrowseClient browse = new ArtemisBrowseClient(connectionManager);
      ArtemisMessagingClient messaging = new ArtemisMessagingClient(connectionManager);
      adminTools = new ArtemisMcpTools(config, management, browse, messaging, admin);
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

   @Test
   public void adminToolsExposedOnlyInAdminMode() {
      ArtemisMcpConfig readOnly = new ArtemisMcpConfig(BROKER_URL, null, null, Mode.READ_ONLY, 50);
      ArtemisMcpTools readOnlyTools = new ArtemisMcpTools(readOnly, management,
         new ArtemisBrowseClient(connectionManager),
         new ArtemisMessagingClient(connectionManager), admin);
      assertEquals(5, readOnlyTools.specifications().size());
      assertEquals(15, adminTools.specifications().size());
   }

   @Test
   public void createQueueToolCreatesQueue() throws Exception {
      CallToolResult result = invoke("create_queue", Map.of("name", "admin.create.q"));
      assertFalse(isError(result));
      assertTrue(management.listQueues().contains("admin.create.q"));
   }

   @Test
   public void purgeRemovesAllMessages() throws Exception {
      admin.createQueue("admin.purge.q", null, "ANYCAST", true);
      send("admin.purge.q", 4, null);
      CallToolResult result = invoke("purge_queue", Map.of("queue", "admin.purge.q", "confirm", true));
      assertFalse(isError(result));
      assertTrue(textOf(result).contains("\"removed\":4"));
      assertEquals(0L, ((Number) management.queueStats("admin.purge.q").get("messageCount")).longValue());
   }

   @Test
   public void destructiveOperationRefusedWithoutConfirm() throws Exception {
      admin.createQueue("admin.guard.q", null, "ANYCAST", true);
      send("admin.guard.q", 2, null);
      CallToolResult result = invoke("purge_queue", Map.of("queue", "admin.guard.q"));
      assertTrue(isError(result));
      assertEquals(2L, ((Number) management.queueStats("admin.guard.q").get("messageCount")).longValue());
   }

   @Test
   public void moveMessagesBetweenQueues() throws Exception {
      admin.createQueue("admin.move.src", null, "ANYCAST", true);
      admin.createQueue("admin.move.dst", null, "ANYCAST", true);
      send("admin.move.src", 3, null);
      CallToolResult result = invoke("move_messages",
         Map.of("queue", "admin.move.src", "target", "admin.move.dst", "confirm", true));
      assertFalse(isError(result));
      assertTrue(textOf(result).contains("\"moved\":3"));
      assertEquals(3L, ((Number) management.queueStats("admin.move.dst").get("messageCount")).longValue());
   }

   @Test
   public void deleteMessagesByFilter() throws Exception {
      admin.createQueue("admin.del.q", null, "ANYCAST", true);
      send("admin.del.q", 2, "a");
      send("admin.del.q", 3, "b");
      CallToolResult result = invoke("delete_messages",
         Map.of("queue", "admin.del.q", "filter", "kind='a'", "confirm", true));
      assertFalse(isError(result));
      assertTrue(textOf(result).contains("\"removed\":2"));
      assertEquals(3L, ((Number) management.queueStats("admin.del.q").get("messageCount")).longValue());
   }

   @Test
   public void deleteQueueToolRemovesQueue() throws Exception {
      admin.createQueue("admin.delete.q", null, "ANYCAST", true);
      CallToolResult result = invoke("delete_queue", Map.of("name", "admin.delete.q", "confirm", true));
      assertFalse(isError(result));
      assertFalse(management.listQueues().contains("admin.delete.q"));
   }

   @Test
   public void sendThenConsumeRoundTrip() throws Exception {
      admin.createQueue("admin.msg.q", null, "ANYCAST", true);
      CallToolResult sent = invoke("send_message",
         Map.of("target", "admin.msg.q", "body", "hello-mcp", "properties", Map.of("kind", "x")));
      assertFalse(isError(sent));
      assertTrue(textOf(sent).contains("\"messageId\""));

      CallToolResult consumed = invoke("consume_message",
         Map.of("queue", "admin.msg.q", "limit", 5, "confirm", true));
      assertFalse(isError(consumed));
      assertTrue(textOf(consumed).contains("hello-mcp"));
      assertEquals(0L, ((Number) management.queueStats("admin.msg.q").get("messageCount")).longValue());
   }

   @Test
   public void consumeRefusedWithoutConfirm() throws Exception {
      admin.createQueue("admin.consume.guard.q", null, "ANYCAST", true);
      send("admin.consume.guard.q", 1, null);
      CallToolResult result = invoke("consume_message", Map.of("queue", "admin.consume.guard.q"));
      assertTrue(isError(result));
      assertEquals(1L, ((Number) management.queueStats("admin.consume.guard.q").get("messageCount")).longValue());
   }

   private void send(String queueName, int count, String kind) throws Exception {
      try (Connection connection = connectionManager.newStartedConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Queue queue = session.createQueue(queueName);
         MessageProducer producer = session.createProducer(queue);
         for (int i = 0; i < count; i++) {
            TextMessage message = session.createTextMessage("m-" + i);
            if (kind != null) {
               message.setStringProperty("kind", kind);
            }
            producer.send(message);
         }
      }
   }

   private CallToolResult invoke(String toolName, Map<String, Object> arguments) {
      SyncToolSpecification spec = adminTools.specifications().stream()
         .filter(s -> s.tool().name().equals(toolName))
         .findFirst()
         .orElseThrow(() -> new AssertionError("tool not found: " + toolName));
      return spec.callHandler().apply(null, new CallToolRequest(toolName, arguments));
   }

   private static boolean isError(CallToolResult result) {
      return Boolean.TRUE.equals(result.isError());
   }

   private static String textOf(CallToolResult result) {
      return result.content().stream()
         .filter(c -> c instanceof TextContent)
         .map(c -> ((TextContent) c).text())
         .reduce("", String::concat);
   }
}

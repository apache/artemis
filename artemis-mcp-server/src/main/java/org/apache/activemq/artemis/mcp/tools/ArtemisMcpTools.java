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
package org.apache.activemq.artemis.mcp.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.apache.activemq.artemis.mcp.admin.ArtemisAdminClient;
import org.apache.activemq.artemis.mcp.config.ArtemisMcpConfig;
import org.apache.activemq.artemis.mcp.management.ArtemisManagementClient;
import org.apache.activemq.artemis.mcp.messaging.ArtemisBrowseClient;
import org.apache.activemq.artemis.mcp.messaging.ArtemisMessagingClient;
import org.apache.activemq.artemis.mcp.util.Json;

public final class ArtemisMcpTools {

   private static final McpJsonMapper MAPPER = McpJsonDefaults.getMapper();

   private static final String EMPTY_OBJECT_SCHEMA =
      "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}";

   private static final String QUEUE_ARG_SCHEMA =
      "{\"type\":\"object\",\"properties\":{"
         + "\"queue\":{\"type\":\"string\",\"description\":\"Queue name.\"}},"
         + "\"required\":[\"queue\"],\"additionalProperties\":false}";

   private static final String BROWSE_SCHEMA =
      "{\"type\":\"object\",\"properties\":{"
         + "\"queue\":{\"type\":\"string\",\"description\":\"Queue name to browse.\"},"
         + "\"limit\":{\"type\":\"integer\",\"minimum\":1,\"description\":\"Max messages to return.\"},"
         + "\"selector\":{\"type\":\"string\",\"description\":\"Optional JMS message selector.\"}},"
         + "\"required\":[\"queue\"],\"additionalProperties\":false}";

   private static final String CREATE_QUEUE_SCHEMA =
      "{\"type\":\"object\",\"properties\":{"
         + "\"name\":{\"type\":\"string\",\"description\":\"Queue name.\"},"
         + "\"address\":{\"type\":\"string\",\"description\":\"Address (defaults to the queue name).\"},"
         + "\"routingType\":{\"type\":\"string\",\"enum\":[\"ANYCAST\",\"MULTICAST\"],\"description\":\"Routing type (default ANYCAST).\"},"
         + "\"durable\":{\"type\":\"boolean\",\"description\":\"Durable queue (default true).\"}},"
         + "\"required\":[\"name\"],\"additionalProperties\":false}";

   private static final String CREATE_ADDRESS_SCHEMA =
      "{\"type\":\"object\",\"properties\":{"
         + "\"name\":{\"type\":\"string\",\"description\":\"Address name.\"},"
         + "\"routingType\":{\"type\":\"string\",\"enum\":[\"ANYCAST\",\"MULTICAST\"],\"description\":\"Routing type (default ANYCAST).\"}},"
         + "\"required\":[\"name\"],\"additionalProperties\":false}";

   private static final String DELETE_NAMED_SCHEMA =
      "{\"type\":\"object\",\"properties\":{"
         + "\"name\":{\"type\":\"string\",\"description\":\"Resource name to delete.\"},"
         + "\"confirm\":{\"type\":\"boolean\",\"description\":\"Must be true to perform this destructive operation.\"}},"
         + "\"required\":[\"name\",\"confirm\"],\"additionalProperties\":false}";

   private static final String PURGE_SCHEMA =
      "{\"type\":\"object\",\"properties\":{"
         + "\"queue\":{\"type\":\"string\",\"description\":\"Queue name.\"},"
         + "\"confirm\":{\"type\":\"boolean\",\"description\":\"Must be true to perform this destructive operation.\"}},"
         + "\"required\":[\"queue\",\"confirm\"],\"additionalProperties\":false}";

   private static final String DELETE_MESSAGES_SCHEMA =
      "{\"type\":\"object\",\"properties\":{"
         + "\"queue\":{\"type\":\"string\",\"description\":\"Queue name.\"},"
         + "\"filter\":{\"type\":\"string\",\"description\":\"JMS filter; blank removes all messages.\"},"
         + "\"confirm\":{\"type\":\"boolean\",\"description\":\"Must be true to perform this destructive operation.\"}},"
         + "\"required\":[\"queue\",\"confirm\"],\"additionalProperties\":false}";

   private static final String MOVE_MESSAGES_SCHEMA =
      "{\"type\":\"object\",\"properties\":{"
         + "\"queue\":{\"type\":\"string\",\"description\":\"Source queue name.\"},"
         + "\"target\":{\"type\":\"string\",\"description\":\"Target queue name.\"},"
         + "\"filter\":{\"type\":\"string\",\"description\":\"JMS filter; blank moves all messages.\"},"
         + "\"confirm\":{\"type\":\"boolean\",\"description\":\"Must be true to perform this destructive operation.\"}},"
         + "\"required\":[\"queue\",\"target\",\"confirm\"],\"additionalProperties\":false}";

   private static final String RETRY_SCHEMA =
      "{\"type\":\"object\",\"properties\":{"
         + "\"queue\":{\"type\":\"string\",\"description\":\"Queue name (typically a DLQ).\"},"
         + "\"confirm\":{\"type\":\"boolean\",\"description\":\"Must be true to perform this operation.\"}},"
         + "\"required\":[\"queue\",\"confirm\"],\"additionalProperties\":false}";

   private static final String SEND_SCHEMA =
      "{\"type\":\"object\",\"properties\":{"
         + "\"target\":{\"type\":\"string\",\"description\":\"Queue or address to send to.\"},"
         + "\"body\":{\"type\":\"string\",\"description\":\"Text message body.\"},"
         + "\"properties\":{\"type\":\"object\",\"description\":\"Optional message properties.\"},"
         + "\"durable\":{\"type\":\"boolean\",\"description\":\"Persistent delivery (default true).\"}},"
         + "\"required\":[\"target\",\"body\"],\"additionalProperties\":false}";

   private static final String CONSUME_SCHEMA =
      "{\"type\":\"object\",\"properties\":{"
         + "\"queue\":{\"type\":\"string\",\"description\":\"Queue name to consume from.\"},"
         + "\"limit\":{\"type\":\"integer\",\"minimum\":1,\"description\":\"Max messages to consume.\"},"
         + "\"selector\":{\"type\":\"string\",\"description\":\"Optional JMS message selector.\"},"
         + "\"timeoutMillis\":{\"type\":\"integer\",\"minimum\":0,\"description\":\"Receive timeout per message (default 2000).\"},"
         + "\"confirm\":{\"type\":\"boolean\",\"description\":\"Must be true; consuming removes messages.\"}},"
         + "\"required\":[\"queue\",\"confirm\"],\"additionalProperties\":false}";

   private final ArtemisMcpConfig config;
   private final ArtemisManagementClient management;
   private final ArtemisBrowseClient browse;
   private final ArtemisMessagingClient messaging;
   private final ArtemisAdminClient admin;

   public ArtemisMcpTools(ArtemisMcpConfig config,
                          ArtemisManagementClient management,
                          ArtemisBrowseClient browse,
                          ArtemisMessagingClient messaging,
                          ArtemisAdminClient admin) {
      this.config = config;
      this.management = management;
      this.browse = browse;
      this.messaging = messaging;
      this.admin = admin;
   }

   public List<SyncToolSpecification> specifications() {
      List<SyncToolSpecification> tools = new ArrayList<>(readOnlySpecifications());
      if (config.isAdmin()) {
         tools.addAll(adminSpecifications());
      }
      return tools;
   }

   private List<SyncToolSpecification> readOnlySpecifications() {
      List<SyncToolSpecification> tools = new ArrayList<>();
      tools.add(tool("list_queues",
         "List the names of all queues on the broker.",
         EMPTY_OBJECT_SCHEMA,
         (exchange, request) -> ok(management.listQueues())));
      tools.add(tool("list_addresses",
         "List the names of all addresses on the broker.",
         EMPTY_OBJECT_SCHEMA,
         (exchange, request) -> ok(management.listAddresses())));
      tools.add(tool("get_broker_overview",
         "Get a high-level overview of the broker: version, uptime, connection/consumer/message counts.",
         EMPTY_OBJECT_SCHEMA,
         (exchange, request) -> ok(management.brokerOverview())));
      tools.add(tool("get_queue_stats",
         "Get message and consumer counters for a single queue.",
         QUEUE_ARG_SCHEMA,
         (exchange, request) -> ok(management.queueStats(requireString(request, "queue")))));
      tools.add(tool("browse_messages",
         "Browse messages on a queue without consuming them (non-destructive).",
         BROWSE_SCHEMA,
         (exchange, request) -> {
            String queue = requireString(request, "queue");
            int limit = optionalInt(request, "limit", config.defaultBrowseLimit());
            String selector = optionalString(request, "selector");
            return ok(browse.browse(queue, limit, selector));
         }));
      return tools;
   }

   private List<SyncToolSpecification> adminSpecifications() {
      List<SyncToolSpecification> tools = new ArrayList<>();
      tools.add(tool("create_queue",
         "Create a queue on the broker.",
         CREATE_QUEUE_SCHEMA,
         (exchange, request) -> {
            String name = requireString(request, "name");
            String address = optionalString(request, "address");
            String routingType = optionalString(request, "routingType", "ANYCAST");
            boolean durable = optionalBool(request, "durable", true);
            admin.createQueue(name, address, routingType, durable);
            return result("created", name);
         }));
      tools.add(tool("create_address",
         "Create an address on the broker.",
         CREATE_ADDRESS_SCHEMA,
         (exchange, request) -> {
            String name = requireString(request, "name");
            String routingType = optionalString(request, "routingType", "ANYCAST");
            admin.createAddress(name, routingType);
            return result("created", name);
         }));
      tools.add(tool("delete_queue",
         "Delete a queue (destructive; requires confirm=true).",
         DELETE_NAMED_SCHEMA,
         (exchange, request) -> {
            String name = requireConfirmed(request, "name");
            admin.deleteQueue(name);
            return result("deleted", name);
         }));
      tools.add(tool("delete_address",
         "Delete an address (destructive; requires confirm=true).",
         DELETE_NAMED_SCHEMA,
         (exchange, request) -> {
            String name = requireConfirmed(request, "name");
            admin.deleteAddress(name);
            return result("deleted", name);
         }));
      tools.add(tool("purge_queue",
         "Remove all messages from a queue (destructive; requires confirm=true).",
         PURGE_SCHEMA,
         (exchange, request) -> {
            String queue = requireConfirmed(request, "queue");
            return count("removed", admin.purgeQueue(queue));
         }));
      tools.add(tool("delete_messages",
         "Remove messages matching a filter from a queue (destructive; requires confirm=true).",
         DELETE_MESSAGES_SCHEMA,
         (exchange, request) -> {
            String queue = requireConfirmed(request, "queue");
            String filter = optionalString(request, "filter");
            return count("removed", admin.deleteMessages(queue, filter));
         }));
      tools.add(tool("move_messages",
         "Move messages from one queue to another (destructive; requires confirm=true).",
         MOVE_MESSAGES_SCHEMA,
         (exchange, request) -> {
            String queue = requireConfirmed(request, "queue");
            String target = requireString(request, "target");
            String filter = optionalString(request, "filter");
            return count("moved", admin.moveMessages(queue, filter, target));
         }));
      tools.add(tool("retry_dlq",
         "Retry messages on a queue, redelivering them to their original address (requires confirm=true).",
         RETRY_SCHEMA,
         (exchange, request) -> {
            String queue = requireConfirmed(request, "queue");
            return count("retried", admin.retryMessages(queue));
         }));
      tools.add(tool("send_message",
         "Send a text message to a queue or address.",
         SEND_SCHEMA,
         (exchange, request) -> {
            String target = requireString(request, "target");
            String body = requireString(request, "body");
            Map<String, Object> properties = optionalMap(request, "properties");
            boolean durable = optionalBool(request, "durable", true);
            return ok(messaging.send(target, body, properties, durable));
         }));
      tools.add(tool("consume_message",
         "Consume (remove) messages from a queue (destructive; requires confirm=true).",
         CONSUME_SCHEMA,
         (exchange, request) -> {
            String queue = requireConfirmed(request, "queue");
            int limit = optionalInt(request, "limit", 1);
            String selector = optionalString(request, "selector");
            long timeout = optionalLong(request, "timeoutMillis", 2000);
            return ok(messaging.consume(queue, limit, selector, timeout));
         }));
      return tools;
   }

   @FunctionalInterface
   private interface ThrowingHandler {
      Object handle(McpSyncServerExchange exchange, CallToolRequest request) throws Exception;
   }

   private SyncToolSpecification tool(String name, String description, String schema, ThrowingHandler handler) {
      Tool tool = Tool.builder()
         .name(name)
         .description(description)
         .inputSchema(MAPPER, schema)
         .build();
      BiFunction<McpSyncServerExchange, CallToolRequest, CallToolResult> callHandler = (exchange, request) -> {
         try {
            Object result = handler.handle(exchange, request);
            return (result instanceof CallToolResult callToolResult) ? callToolResult : ok(result);
         } catch (Exception e) {
            return error(name + " failed: " + e.getMessage());
         }
      };
      return SyncToolSpecification.builder().tool(tool).callHandler(callHandler).build();
   }

   private static CallToolResult ok(Object value) {
      return CallToolResult.builder().addTextContent(Json.write(value)).build();
   }

   private static CallToolResult error(String message) {
      return CallToolResult.builder().addTextContent(message).isError(true).build();
   }

   private static Map<String, Object> result(String key, Object value) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("status", "ok");
      map.put(key, value);
      return map;
   }

   private static Map<String, Object> count(String key, int value) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("status", "ok");
      map.put(key, value);
      return map;
   }

   private static String requireConfirmed(CallToolRequest request, String key) {
      if (!Boolean.TRUE.equals(arguments(request).get("confirm"))) {
         throw new IllegalStateException("destructive operation refused: set confirm=true to proceed");
      }
      return requireString(request, key);
   }

   private static String requireString(CallToolRequest request, String key) {
      Object value = arguments(request).get(key);
      if (value == null || value.toString().isBlank()) {
         throw new IllegalArgumentException("Missing required argument: " + key);
      }
      return value.toString();
   }

   private static String optionalString(CallToolRequest request, String key) {
      return optionalString(request, key, null);
   }

   private static String optionalString(CallToolRequest request, String key, String defaultValue) {
      Object value = arguments(request).get(key);
      return (value == null || value.toString().isBlank()) ? defaultValue : value.toString();
   }

   private static int optionalInt(CallToolRequest request, String key, int defaultValue) {
      Object value = arguments(request).get(key);
      if (value instanceof Number number) {
         return number.intValue();
      }
      if (value != null) {
         try {
            return Integer.parseInt(value.toString().trim());
         } catch (NumberFormatException ignored) {
         }
      }
      return defaultValue;
   }

   private static long optionalLong(CallToolRequest request, String key, long defaultValue) {
      Object value = arguments(request).get(key);
      if (value instanceof Number number) {
         return number.longValue();
      }
      if (value != null) {
         try {
            return Long.parseLong(value.toString().trim());
         } catch (NumberFormatException ignored) {
         }
      }
      return defaultValue;
   }

   @SuppressWarnings("unchecked")
   private static Map<String, Object> optionalMap(CallToolRequest request, String key) {
      Object value = arguments(request).get(key);
      return value instanceof Map ? (Map<String, Object>) value : null;
   }

   private static boolean optionalBool(CallToolRequest request, String key, boolean defaultValue) {
      Object value = arguments(request).get(key);
      if (value instanceof Boolean bool) {
         return bool;
      }
      if (value != null) {
         return Boolean.parseBoolean(value.toString().trim());
      }
      return defaultValue;
   }

   private static Map<String, Object> arguments(CallToolRequest request) {
      Map<String, Object> arguments = request.arguments();
      return arguments == null ? Map.of() : arguments;
   }
}

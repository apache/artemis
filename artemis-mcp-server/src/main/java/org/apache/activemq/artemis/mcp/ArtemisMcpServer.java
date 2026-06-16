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

import java.util.concurrent.CountDownLatch;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import org.apache.activemq.artemis.mcp.admin.ArtemisAdminClient;
import org.apache.activemq.artemis.mcp.config.ArtemisMcpConfig;
import org.apache.activemq.artemis.mcp.connection.ArtemisConnectionManager;
import org.apache.activemq.artemis.mcp.management.ArtemisManagementClient;
import org.apache.activemq.artemis.mcp.messaging.ArtemisBrowseClient;
import org.apache.activemq.artemis.mcp.messaging.ArtemisMessagingClient;
import org.apache.activemq.artemis.mcp.tools.ArtemisMcpTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ArtemisMcpServer {

   private static final Logger LOG = LoggerFactory.getLogger(ArtemisMcpServer.class);

   private final ArtemisMcpConfig config;
   private final ArtemisConnectionManager connectionManager;
   private McpSyncServer server;

   public ArtemisMcpServer(ArtemisMcpConfig config) {
      this.config = config;
      this.connectionManager = new ArtemisConnectionManager(config);
   }

   public McpSyncServer start() {
      ArtemisManagementClient management = new ArtemisManagementClient(connectionManager);
      ArtemisBrowseClient browse = new ArtemisBrowseClient(connectionManager);
      ArtemisMessagingClient messaging = new ArtemisMessagingClient(connectionManager);
      ArtemisAdminClient admin = new ArtemisAdminClient(connectionManager);
      ArtemisMcpTools tools = new ArtemisMcpTools(config, management, browse, messaging, admin);

      StdioServerTransportProvider transport = new StdioServerTransportProvider(McpJsonDefaults.getMapper());

      this.server = McpServer.sync(transport)
         .serverInfo("artemis-mcp-server", "1.0.0")
         .instructions(config.isAdmin()
            ? "Tools to inspect, monitor and administer an Apache ActiveMQ Artemis broker. "
               + "Destructive tools require confirm=true."
            : "Tools to inspect and monitor an Apache ActiveMQ Artemis broker. "
               + "All tools in this build are read-only.")
         .capabilities(ServerCapabilities.builder().tools(true).build())
         .tools(tools.specifications().toArray(new SyncToolSpecification[0]))
         .build();

      LOG.info("Artemis MCP server started (mode={}, broker={})", config.mode(), config.brokerUrl());
      return server;
   }

   public void stop() {
      if (server != null) {
         server.closeGracefully();
      }
      connectionManager.close();
   }

   public static void main(String[] args) throws InterruptedException {
      ArtemisMcpConfig config = ArtemisMcpConfig.fromEnvironment();
      ArtemisMcpServer mcpServer = new ArtemisMcpServer(config);

      CountDownLatch shutdownLatch = new CountDownLatch(1);
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
         mcpServer.stop();
         shutdownLatch.countDown();
      }, "artemis-mcp-shutdown"));

      mcpServer.start();
      shutdownLatch.await();
   }
}

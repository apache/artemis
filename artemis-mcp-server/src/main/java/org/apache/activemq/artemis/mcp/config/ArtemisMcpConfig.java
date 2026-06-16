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
package org.apache.activemq.artemis.mcp.config;

public final class ArtemisMcpConfig {

   public enum Mode {
      READ_ONLY,
      ADMIN;

      static Mode parse(String value) {
         if (value == null || value.isBlank()) {
            return READ_ONLY;
         }
         String normalized = value.trim().toUpperCase().replace('-', '_');
         return Mode.valueOf(normalized);
      }
   }

   private final String brokerUrl;
   private final String user;
   private final String password;
   private final Mode mode;
   private final int defaultBrowseLimit;

   public ArtemisMcpConfig(String brokerUrl, String user, String password, Mode mode, int defaultBrowseLimit) {
      this.brokerUrl = brokerUrl;
      this.user = user;
      this.password = password;
      this.mode = mode;
      this.defaultBrowseLimit = defaultBrowseLimit;
   }

   public static ArtemisMcpConfig fromEnvironment() {
      String brokerUrl = resolve("artemis.mcp.brokerUrl", "ARTEMIS_MCP_BROKER_URL", "tcp://localhost:61616");
      String user = resolve("artemis.mcp.user", "ARTEMIS_MCP_USER", null);
      String password = resolve("artemis.mcp.password", "ARTEMIS_MCP_PASSWORD", null);
      Mode mode = Mode.parse(resolve("artemis.mcp.mode", "ARTEMIS_MCP_MODE", null));
      int browseLimit = parseInt(resolve("artemis.mcp.browseLimit", "ARTEMIS_MCP_BROWSE_LIMIT", "50"), 50);
      return new ArtemisMcpConfig(brokerUrl, user, password, mode, browseLimit);
   }

   private static String resolve(String sysProp, String envVar, String defaultValue) {
      String value = System.getProperty(sysProp);
      if (value == null || value.isBlank()) {
         value = System.getenv(envVar);
      }
      return (value == null || value.isBlank()) ? defaultValue : value;
   }

   private static int parseInt(String value, int defaultValue) {
      try {
         return value == null ? defaultValue : Integer.parseInt(value.trim());
      } catch (NumberFormatException e) {
         return defaultValue;
      }
   }

   public String brokerUrl() {
      return brokerUrl;
   }

   public String user() {
      return user;
   }

   public String password() {
      return password;
   }

   public Mode mode() {
      return mode;
   }

   public boolean isAdmin() {
      return mode == Mode.ADMIN;
   }

   public int defaultBrowseLimit() {
      return defaultBrowseLimit;
   }
}

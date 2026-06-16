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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.activemq.artemis.mcp.config.ArtemisMcpConfig.Mode;
import org.junit.jupiter.api.Test;

public class ArtemisMcpConfigTest {

   @Test
   public void defaultsToReadOnly() {
      ArtemisMcpConfig config = new ArtemisMcpConfig("tcp://localhost:61616", null, null, Mode.READ_ONLY, 50);
      assertEquals(Mode.READ_ONLY, config.mode());
      assertFalse(config.isAdmin());
   }

   @Test
   public void adminModeIsExplicit() {
      ArtemisMcpConfig config = new ArtemisMcpConfig("tcp://localhost:61616", null, null, Mode.ADMIN, 50);
      assertTrue(config.isAdmin());
   }

   @Test
   public void modeParsingIsLenient() {
      assertEquals(Mode.READ_ONLY, Mode.parse(null));
      assertEquals(Mode.READ_ONLY, Mode.parse(""));
      assertEquals(Mode.READ_ONLY, Mode.parse("read-only"));
      assertEquals(Mode.READ_ONLY, Mode.parse("READ_ONLY"));
      assertEquals(Mode.ADMIN, Mode.parse("admin"));
   }

   @Test
   public void fromEnvironmentUsesSystemProperties() {
      String prevUrl = System.getProperty("artemis.mcp.brokerUrl");
      String prevMode = System.getProperty("artemis.mcp.mode");
      try {
         System.setProperty("artemis.mcp.brokerUrl", "tcp://example:61616");
         System.setProperty("artemis.mcp.mode", "admin");
         ArtemisMcpConfig config = ArtemisMcpConfig.fromEnvironment();
         assertEquals("tcp://example:61616", config.brokerUrl());
         assertTrue(config.isAdmin());
      } finally {
         restore("artemis.mcp.brokerUrl", prevUrl);
         restore("artemis.mcp.mode", prevMode);
      }
   }

   private static void restore(String key, String value) {
      if (value == null) {
         System.clearProperty(key);
      } else {
         System.setProperty(key, value);
      }
   }
}

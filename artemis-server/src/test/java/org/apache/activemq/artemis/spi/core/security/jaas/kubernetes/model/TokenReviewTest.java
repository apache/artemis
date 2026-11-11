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
package org.apache.activemq.artemis.spi.core.security.jaas.kubernetes.model;

import org.junit.jupiter.api.Test;

import static org.apache.activemq.artemis.spi.core.security.jaas.KubernetesLoginModuleTest.USERNAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TokenReviewTest {

   @Test
   public void testEmpty() {
      String json = "{}";
      TokenReview tr = TokenReview.fromJsonString(json);

      assertFalse(tr.isAuthenticated());
      assertNull(tr.getUser());
      assertNull(tr.getUsername());
   }

   @Test
   public void testSimple() {
      String json = "{\"status\": {\"authenticated\": true, \"user\": {\"username\": \"" + USERNAME + "\"}}}";

      TokenReview tr = TokenReview.fromJsonString(json);

      assertNotNull(tr);
      assertTrue(tr.isAuthenticated());
      assertEquals(USERNAME, tr.getUsername());
      assertNotNull(tr.getUser());
      assertEquals(USERNAME, tr.getUser().getUsername());
      assertEquals(0, tr.getAudiences().size());
      assertNull(tr.getUser().getExtra());
   }

   @Test
   public void testCompleteObject() {
      String json = "{\"status\": {"
            + "\"authenticated\": true, "
            + "\"user\": {"
            + "  \"username\": \"" + USERNAME + "\","
            + "  \"uid\": \"kermit-uid\","
            + "  \"groups\": ["
            + "    \"group-1\","
            + "    \"group-2\""
            + "  ],"
            + "  \"extra\": {"
            + "    \"authentication.kubernetes.io/pod-name\": ["
            + "      \"pod-1\","
            + "      \"pod-2\""
            + "    ],"
            + "    \"authentication.kubernetes.io/pod-uid\": ["
            + "      \"pod-uid-1\","
            + "      \"pod-uid-2\""
            + "    ]"
            + "  }"
            + "},"
            + "\"audiences\": ["
            + "  \"audience-1\","
            + "  \"audience-2\""
            + "]}}";

      TokenReview tr = TokenReview.fromJsonString(json);

      assertNotNull(tr);
      assertTrue(tr.isAuthenticated());
      assertEquals(USERNAME, tr.getUsername());
      assertNotNull(tr.getUser());
      assertEquals(USERNAME, tr.getUser().getUsername());
      assertTrue(tr.getAudiences().contains("audience-1"));
      assertTrue(tr.getAudiences().contains("audience-2"));
      assertTrue(tr.getUser().getGroups().contains("group-1"));
      assertTrue(tr.getUser().getGroups().contains("group-2"));
      assertEquals("kermit-uid", tr.getUser().getUid());

      assertNotNull(tr.getUser().getExtra());
      assertTrue(tr.getUser().getExtra().getPodNames().contains("pod-1"));
      assertTrue(tr.getUser().getExtra().getPodNames().contains("pod-2"));
      assertTrue(tr.getUser().getExtra().getPodUids().contains("pod-uid-1"));
      assertTrue(tr.getUser().getExtra().getPodUids().contains("pod-uid-2"));
   }

}

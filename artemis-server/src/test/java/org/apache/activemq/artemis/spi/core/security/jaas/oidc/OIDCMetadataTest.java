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
package org.apache.activemq.artemis.spi.core.security.jaas.oidc;

import org.apache.activemq.artemis.api.core.JsonUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link OIDCMetadata} assuming some proper JSON was retrieved from the provider.
 */
public class OIDCMetadataTest {

   @Test
   public void missingIssuer() {
      OIDCMetadata md = new OIDCMetadata(null, JsonUtil.readJsonObject("{}"));
      assertFalse(md.isValid());
      assertFalse(md.isRecoverable());
      assertNull(md.getException());
      assertTrue(md.getErrorMessage().contains("OIDC Metadata issuer is missing"));
   }

   @Test
   public void differentIssuer() {
      OIDCMetadata md = new OIDCMetadata("http://localhost:8080", JsonUtil.readJsonObject("{\"issuer\":\"http://localhost:8081\"}"));
      assertFalse(md.isValid());
      assertFalse(md.isRecoverable());
      assertNull(md.getException());
      assertTrue(md.getErrorMessage().contains("OIDC Metadata issuer mismatch"));
   }

   @Test
   public void noJwksURI() {
      OIDCMetadata md = new OIDCMetadata("http://localhost:8080", JsonUtil.readJsonObject("{\"issuer\":\"http://localhost:8080\"}"));
      assertTrue(md.isValid());
      assertNull(md.getJwksURI());
      assertNull(md.getException());
      assertNull(md.getErrorMessage());
      assertTrue(md.currentSecurityContext().getKeys().isEmpty());
   }

}

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
package org.apache.activemq.artemis.protocol.amqp.sasl;

import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.core.security.SecurityStore;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class XOAuth2SASLTest {

   @Test
   public void properXOAUTH2SASL() throws Exception {
      SecurityStore securityStore = mock(SecurityStore.class);
      when(securityStore.isSecurityEnabled()).thenReturn(true);
      when(securityStore.authenticate("me", "JWT-token", null, null)).thenReturn("OK");
      XOAuth2SASL sasl = new XOAuth2SASL(securityStore, null, null);
      assertNull(sasl.processSASL(String.format("user=%s\001auth=Bearer %s\001\001", "me", "JWT-token").getBytes()));

      SASLResult result = sasl.result();
      assertInstanceOf(TokenSASLResult.class, result);
      TokenSASLResult tokenResult = (TokenSASLResult) result;
      assertTrue(tokenResult.isSuccess());
      assertEquals("me", tokenResult.getUser());
      assertEquals("JWT-token", tokenResult.getToken());
   }

   @Test
   public void noInitialResponse() {
      SecurityStore securityStore = mock(SecurityStore.class);
      RemotingConnection remotingConnection = mock(RemotingConnection.class);
      when(remotingConnection.getTransportLocalAddress()).thenReturn("tcp://localhost:5672");

      XOAuth2SASL sasl = new XOAuth2SASL(securityStore, null, remotingConnection);

      byte[] challenge = sasl.processSASL(null);
      assertNotNull(challenge);
      SASLResult result = sasl.result();
      assertInstanceOf(TokenSASLResult.class, result);
      TokenSASLResult tokenResult = (TokenSASLResult) result;
      assertFalse(tokenResult.isSuccess());
      assertNull(tokenResult.getUser());
      assertNull(tokenResult.getToken());

      challenge = sasl.processSASL(new byte[0]);
      assertNotNull(challenge);
      result = sasl.result();
      assertInstanceOf(TokenSASLResult.class, result);
      tokenResult = (TokenSASLResult) result;
      assertFalse(tokenResult.isSuccess());
      assertNull(tokenResult.getUser());
      assertNull(tokenResult.getToken());

      challenge = sasl.processSASL(String.format("user=%s\001auth=Bearer %s\001", "me", "JWT-token").getBytes());
      assertNotNull(challenge);
      result = sasl.result();
      assertInstanceOf(TokenSASLResult.class, result);
      tokenResult = (TokenSASLResult) result;
      assertFalse(tokenResult.isSuccess());
      assertNull(tokenResult.getUser());
      assertNull(tokenResult.getToken());

      challenge = sasl.processSASL(String.format("user=%s\002auth=Bearer %s\001\001", "me", "JWT-token").getBytes());
      assertNotNull(challenge);
      result = sasl.result();
      assertInstanceOf(TokenSASLResult.class, result);
      tokenResult = (TokenSASLResult) result;
      assertFalse(tokenResult.isSuccess());
      assertNull(tokenResult.getUser());
      assertNull(tokenResult.getToken());

      String json = new String(Base64.getDecoder().decode(challenge));
      JsonObject jsonObject = JsonUtil.readJsonObject(json);
      assertEquals("401", jsonObject.getString("status"));
   }

}

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
package org.apache.activemq.artemis.spi.core.security.jaas;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.TextInputCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

import java.security.Principal;
import java.util.Collections;

import org.apache.activemq.artemis.core.management.impl.ManagementRemotingConnection;
import org.junit.jupiter.api.Test;

public class JaasCallbackHandlerTest {

   @Test
   public void handlePasswordCallback() throws Exception {
      final String password = "password";
      PasswordCallback passwordCallback = new PasswordCallback("prompt", false);

      new JaasCallbackHandler(null, password, null).handle(new Callback[]{passwordCallback});
      assertEquals(password, new String(passwordCallback.getPassword()));

      new JaasCallbackHandler(null, null, null).handle(new Callback[]{passwordCallback});
      assertNull(passwordCallback.getPassword());
   }

   @Test
   public void handleNameCallback() throws Exception {
      final String username = "username";
      NameCallback nameCallback = new NameCallback("prompt");

      new JaasCallbackHandler(username, null, null).handle(new Callback[]{nameCallback});
      assertEquals(username, nameCallback.getName());

      new JaasCallbackHandler(null, null, null).handle(new Callback[]{nameCallback});
      assertNull(nameCallback.getName());
   }

   @Test
   public void handleCertificateCallback() throws Exception {
      CertificateCallback certificateCallback = new CertificateCallback();

      new JaasCallbackHandler(null, null, null).handle(new Callback[]{certificateCallback});
      assertNull(certificateCallback.getCertificates());
   }

   @Test
   public void handlePrincipalsCallback() throws Exception {
      PrincipalsCallback principalsCallback = new PrincipalsCallback();

      new JaasCallbackHandler(null, null, new ManagementRemotingConnection()).handle(new Callback[]{principalsCallback});
      assertNull(principalsCallback.getPeerPrincipals());

      final Principal principal = new UserPrincipal("");
      ManagementRemotingConnection remotingConnection = new ManagementRemotingConnection() {
         @Override
         public Subject getSubject() {
            return new Subject(false, Collections.singleton(principal), Collections.emptySet(), Collections.emptySet());
         }
      };
      new JaasCallbackHandler(null, null, remotingConnection).handle(new Callback[]{principalsCallback});
      assertArrayEquals(new Principal[]{principal}, principalsCallback.getPeerPrincipals());
   }

   @Test
   public void handleClientIDCallback() throws Exception {
      final String clientID = "clientID";
      ClientIDCallback clientIDCallback = new ClientIDCallback();

      ManagementRemotingConnection remotingConnection = new ManagementRemotingConnection() {
         @Override
         public String getClientID() {
            return clientID;
         }
      };
      new JaasCallbackHandler(null, null, remotingConnection).handle(new Callback[]{clientIDCallback});
      assertEquals(clientID, clientIDCallback.getClientID());

      new JaasCallbackHandler(null, null, new ManagementRemotingConnection()).handle(new Callback[]{clientIDCallback});
      assertNull(clientIDCallback.getClientID());
   }

   @Test
   public void handleUnsupported() throws Exception {
      assertThrows(UnsupportedCallbackException.class, () -> {
         new JaasCallbackHandler(null, null, null).handle(new Callback[]{new TextInputCallback("prompt")});
      });
   }

}

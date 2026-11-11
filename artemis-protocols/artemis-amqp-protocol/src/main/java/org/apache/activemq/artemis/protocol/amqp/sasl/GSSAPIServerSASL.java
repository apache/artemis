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

import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.login.LoginContext;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;

// delegate the the jdk GSSAPI support
public class GSSAPIServerSASL implements ServerSASL {
   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String NAME = "GSSAPI";
   private String loginConfigScope;
   private SaslServer saslServer;
   private Subject jaasId;
   private SASLResult result;

   @Override
   public String getName() {
      return NAME;
   }

   @Override
   public byte[] processSASL(byte[] bytes) {
      try {
         if (jaasId == null) {
            // populate subject with acceptor private credentials
            LoginContext loginContext = new LoginContext(loginConfigScope);
            loginContext.login();
            jaasId = loginContext.getSubject();
         }

         if (saslServer == null) {
            saslServer = SecurityManagerShim.callAs(jaasId, (Callable<SaslServer>) () -> Sasl.createSaslServer(NAME, null, null, new HashMap<String, String>(), callbacks -> {
               for (Callback callback : callbacks) {
                  if (callback instanceof AuthorizeCallback authorizeCallback) {
                     // only ok to authenticate as self
                     authorizeCallback.setAuthorized(authorizeCallback.getAuthenticationID().equals(authorizeCallback.getAuthorizationID()));
                  }
               }
            }));
         }

         byte[] challenge = SecurityManagerShim.callAs(jaasId, (Callable<byte[]>) () -> saslServer.evaluateResponse(bytes));
         if (saslServer.isComplete()) {
            result = new PrincipalSASLResult(true, new KerberosPrincipal(saslServer.getAuthorizationID()));
         }
         return challenge;
      } catch (CompletionException ce) {
         Throwable cause = ce.getCause();
         logger.info("Error on sasl input: {}", String.valueOf(cause), cause);
         result = new PrincipalSASLResult(false, null);
      } catch (Exception outOfHere) {
         logger.info("Error on sasl input: {}", outOfHere.toString(), outOfHere);
         result = new PrincipalSASLResult(false, null);
      }
      return null;
   }

   @Override
   public SASLResult result() {
      return result;
   }

   @Override
   public void done() {
      if (saslServer != null) {
         try {
            saslServer.dispose();
         } catch (SaslException error) {
            logger.debug("Exception on sasl dispose", error);
         }
      }
   }

   public String getLoginConfigScope() {
      return loginConfigScope;
   }

   public void setLoginConfigScope(String loginConfigScope) {
      this.loginConfigScope = loginConfigScope;
   }

}
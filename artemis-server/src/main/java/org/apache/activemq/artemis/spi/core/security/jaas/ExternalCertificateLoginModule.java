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

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.security.Principal;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.utils.CertificateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A LoginModule that propagates TLS certificates subject DN as a UserPrincipal.
 */
public class ExternalCertificateLoginModule implements AuditLoginModule {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String SAN_URI_ROLE_PREFIX_PROP = "sanUriRolePrefix";
   public static final int SAN_EXT_URI_TYPE = 6;
   private CallbackHandler callbackHandler;
   private Subject subject;
   private String userName;

   private final Set<Principal> principals = new HashSet<>();
   private final Set<Principal> roles = new HashSet<>();
   private String sanUriRolePrefix;

   @Override
   public void initialize(Subject subject,
                          CallbackHandler callbackHandler,
                          Map<String, ?> sharedState,
                          Map<String, ?> options) {
      this.subject = subject;
      this.callbackHandler = callbackHandler;
      if (options != null && options.containsKey(SAN_URI_ROLE_PREFIX_PROP)) {
         sanUriRolePrefix = String.valueOf(options.get(SAN_URI_ROLE_PREFIX_PROP));
      }
   }

   @Override
   public boolean login() throws LoginException {
      Callback[] callbacks = new Callback[1];

      callbacks[0] = new CertificateCallback();
      try {
         callbackHandler.handle(callbacks);
      } catch (IOException ioe) {
         throw new LoginException(ioe.getMessage());
      } catch (UnsupportedCallbackException uce) {
         throw new LoginException("Unable to obtain client certificates: " + uce.getMessage());
      }

      X509Certificate[] certificates = ((CertificateCallback) callbacks[0]).getCertificates();
      userName = CertificateUtil.getDistinguishedName(certificates);

      if (userName != null && sanUriRolePrefix != null) {
         // getSubjectAlternativeNames returns a Collection of Lists
         // Each inner list is [Integer type, Object value]
         Collection<List<?>> sans = null;
         try {
            sans = certificates[0].getSubjectAlternativeNames();
         } catch (CertificateParsingException e) {
            throw new LoginException(e.getMessage());
         }

         if (sans != null) {
            for (List<?> san : sans) {
               int type = (Integer) san.get(0);
               if (type == SAN_EXT_URI_TYPE) {
                  final String value = (String) san.get(1);
                  if (value != null && value.startsWith(sanUriRolePrefix)) {
                     roles.add(new RolePrincipal(value.substring(sanUriRolePrefix.length())));
                  }
               }
            }
         }
      }

      if (logger.isDebugEnabled()) {
         logger.debug("Certificates: {}, userName: {}", Arrays.toString(certificates), userName);
      }

      return userName != null;
   }

   @Override
   public boolean commit() throws LoginException {
      if (userName != null) {
         principals.add(new UserPrincipal(userName));
         principals.addAll(roles);
         subject.getPrincipals().addAll(principals);
      }

      clear();
      logger.debug("commit");
      return true;
   }

   @Override
   public boolean abort() throws LoginException {
      registerFailureForAudit(userName);
      clear();
      logger.debug("abort");
      return true;
   }

   @Override
   public boolean logout() {
      subject.getPrincipals().removeAll(principals);
      principals.clear();
      logger.debug("logout");
      return true;
   }

   private void clear() {
      userName = null;
   }
}

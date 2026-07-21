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
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.LoginException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.activemq.artemis.utils.CertificateUtil;

/**
 * A LoginModule allowing for SSL certificate based authentication based on User Principal Name (UPN). The UPNs are
 * retrieved from the Subject Alternative Name (SAN) extension of the client's certificate. There is no mapping from UPN
 * to another name as there is with DN when using the {@link TextFileCertificateLoginModule}.
 * <p>
 * This class uses a role definition file where each line is like:
 * <pre>{@code
 * <role_name>=<user_name_1>, <user_name_2>, etc.
 * }</pre>
 * The role file's locations must be specified in the {@code org.apache.activemq.jaas.textfileupn.role} property. NOTE:
 * This class will re-read the role file if it has been modified and the {@code reload} option is {@code true}.
 */
public class TextFileUpnCertificateLoginModule extends CertificateLoginModule {

   private static final String USER_FILE_PROP_NAME = "org.apache.activemq.jaas.textfileupn.user";
   private static final String ROLE_FILE_PROP_NAME = "org.apache.activemq.jaas.textfileupn.role";

   private Map<String, Set<String>> rolesByUser;
   private Properties users;

   @Override
   public void initialize(Subject subject,
                          CallbackHandler callbackHandler,
                          Map<String, ?> sharedState,
                          Map<String, ?> options) {
      super.initialize(subject, callbackHandler, sharedState, options);
      users = load(USER_FILE_PROP_NAME, "", options).getProps();
      rolesByUser = load(ROLE_FILE_PROP_NAME, "", options).invertedPropertiesValuesMap();
   }

   /**
    * Overriding to allow auth based on the User Principal Name (UPN).
    *
    * @param certs The certificate the incoming connection provided.
    * @return The user's authenticated name or null if unable to authenticate the user.
    * @throws LoginException Thrown if unable to find user file or connection certificate.
    */
   @Override
   protected String getUserNameForCertificates(final X509Certificate[] certs) throws LoginException {
      if (certs == null || certs.length == 0) {
         throw new LoginException("Client certificates not found. Cannot authenticate.");
      }
      try {
         String upn = getCertificateInfo(certs);
         if (upn != null && users.containsKey(upn)) {
            return upn;
         } else {
            return null;
         }
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   /**
    * Overriding to allow for role discovery based on text files.
    *
    * @param username The name of the user being examined. This is the same name returned by
    *                 {@link #getUserNameForCertificates(X509Certificate[])}
    * @return A Set of name Strings for roles this user belongs to
    * @throws LoginException Thrown if unable to find role definition file.
    */
   @Override
   protected Set<String> getUserRoles(String username) throws LoginException {
      Set<String> userRoles = rolesByUser.get(username);
      if (userRoles == null) {
         userRoles = Collections.emptySet();
      }

      return userRoles;
   }

   @Override
   protected String getCertificateInfo(X509Certificate[] certificates) {
      return CertificateUtil.getUserPrincipalName(certificates);
   }
}

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
package org.apache.activemq.artemis.ra;

import javax.resource.spi.ConnectionRequestInfo;
import javax.resource.spi.ManagedConnectionFactory;
import javax.resource.spi.SecurityException;
import javax.resource.spi.security.PasswordCredential;
import javax.security.auth.Subject;
import java.io.Serializable;
import java.security.PrivilegedAction;
import java.util.Set;

import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * Credential information
 */
public class ActiveMQRACredential implements Serializable {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   static final long serialVersionUID = 210476602237497193L;

   private String userName;

   private String password;

   private ActiveMQRACredential() {
      logger.trace("constructor()");
   }

   public String getUserName() {
      logger.trace("getUserName()");

      return userName;
   }

   private void setUserName(final String userName) {
      logger.trace("setUserName({})", userName);

      this.userName = userName;
   }

   public String getPassword() {
      logger.trace("getPassword()");

      return password;
   }

   private void setPassword(final String password) {
      logger.trace("setPassword(****)");

      this.password = password;
   }

   /**
    * Get credentials
    *
    * @param mcf     The managed connection factory
    * @param subject The subject
    * @param info    The connection request info
    * @return The credentials
    * @throws SecurityException Thrown if the credentials can't be retrieved
    */
   public static ActiveMQRACredential getCredential(final ManagedConnectionFactory mcf,
                                                    final Subject subject,
                                                    final ConnectionRequestInfo info) throws SecurityException {
      if (logger.isTraceEnabled()) {
         logger.trace("getCredential({}, {} ,{})", mcf, subject, info);
      }

      ActiveMQRACredential jc = new ActiveMQRACredential();
      if (subject == null && info != null) {
         jc.setUserName(((ActiveMQRAConnectionRequestInfo) info).getUserName());
         jc.setPassword(((ActiveMQRAConnectionRequestInfo) info).getPassword());
      } else if (subject != null) {
         PasswordCredential pwdc = GetCredentialAction.getCredential(subject, mcf);

         if (pwdc == null) {
            throw new SecurityException("No password credentials found");
         }

         jc.setUserName(pwdc.getUserName());
         jc.setPassword(new String(pwdc.getPassword()));
      } else {
         throw new SecurityException("No Subject or ConnectionRequestInfo set, could not get credentials");
      }

      return jc;
   }

   @Override
   public String toString() {
      logger.trace("toString()");

      return super.toString() + "{ username=" + userName + ", password=**** }";
   }

   /**
    * Privileged class to get credentials
    */
   private static class GetCredentialAction implements PrivilegedAction<PasswordCredential> {

      private final Subject subject;

      private final ManagedConnectionFactory mcf;

      GetCredentialAction(final Subject subject, final ManagedConnectionFactory mcf) {
         logger.trace("constructor({}, {})", subject, mcf);

         this.subject = subject;
         this.mcf = mcf;
      }

      /**
       * {@return the credential}
       */
      @Override
      public PasswordCredential run() {
         logger.trace("run()");

         Set<PasswordCredential> creds = subject.getPrivateCredentials(PasswordCredential.class);
         PasswordCredential pwdc = null;

         for (PasswordCredential curCred : creds) {
            if (curCred.getManagedConnectionFactory().equals(mcf)) {
               pwdc = curCred;
               break;
            }
         }
         return pwdc;
      }

      /**
       * {@return the credential}
       */
      static PasswordCredential getCredential(final Subject subject, final ManagedConnectionFactory mcf) {
         logger.trace("getCredential({}, {})", subject, mcf);

         GetCredentialAction action = new GetCredentialAction(subject, mcf);
         return SecurityManagerShim.doPrivileged(action);
      }
   }
}

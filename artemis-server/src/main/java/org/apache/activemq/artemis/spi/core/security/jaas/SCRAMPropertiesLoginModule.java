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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.Principal;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.crypto.Mac;
import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginException;

import org.apache.activemq.artemis.spi.core.security.scram.SCRAM;
import org.apache.activemq.artemis.spi.core.security.scram.ScramException;
import org.apache.activemq.artemis.spi.core.security.scram.ScramUtils;
import org.apache.activemq.artemis.spi.core.security.scram.StringPrep;
import org.apache.activemq.artemis.spi.core.security.scram.StringPrep.StringPrepError;
import org.apache.activemq.artemis.spi.core.security.scram.UserData;
import org.apache.activemq.artemis.utils.PasswordMaskingUtil;

/**
 * Login modules that uses properties files similar to the {@link PropertiesLoginModule}. It can either store the
 * username-password in plain text or in an encrypted/hashed form. the {@link #main(String[])} method provides a way to
 * prepare unencrypted data to be encrypted/hashed.
 */
public class SCRAMPropertiesLoginModule extends PropertiesLoader implements AuditLoginModule {

   private static final String SEPARATOR_MECHANISM = "|";
   private static final String SEPARATOR_PARAMETER = ":";
   private static final int MIN_ITERATIONS = 4096;
   private static final SecureRandom RANDOM_GENERATOR = new SecureRandom();
   private Subject subject;
   private CallbackHandler callbackHandler;
   private Properties users;
   private Map<String, Set<String>> roles;
   private UserData userData;
   private String user;
   private final Set<Principal> principals = new LinkedHashSet<>();

   @Override
   public void initialize(Subject subject, CallbackHandler callbackHandler, Map<String, ?> sharedState,
                          Map<String, ?> options) {
      this.subject = subject;
      this.callbackHandler = callbackHandler;

      init(options);
      users = load(PropertiesLoginModule.USER_FILE_PROP_NAME, "user", options).getProps();
      roles = load(PropertiesLoginModule.ROLE_FILE_PROP_NAME, "role", options).invertedPropertiesValuesMap();

   }

   @Override
   public boolean login() throws LoginException {
      NameCallback nameCallback = new NameCallback("Username: ");
      executeCallbacks(nameCallback);
      user = nameCallback.getName();
      SCRAMMechanismCallback mechanismCallback = new SCRAMMechanismCallback();
      executeCallbacks(mechanismCallback);
      SCRAM scram = getTypeByString(mechanismCallback.getMechanism());
      if (user == null) {
         userData = generateUserData(null); // generate random user data
      } else {
         String password = users.getProperty(user + SEPARATOR_MECHANISM + scram.name());
         if (password == null) {
            // fallback for probably unencoded user/password or a single encoded entry
            password = users.getProperty(user);
         }
         if (PasswordMaskingUtil.isEncMasked(password)) {
            String[] unwrap = PasswordMaskingUtil.unwrap(password).split(SEPARATOR_PARAMETER);
            userData = new UserData(unwrap[0], Integer.parseInt(unwrap[1]), unwrap[2], unwrap[3]);
         } else {
            userData = generateUserData(password);
         }
      }
      return true;
   }

   private UserData generateUserData(String plainTextPassword) throws LoginException {
      if (plainTextPassword == null) {
         // if the user is not available (or the password) generate a random password here so an
         // attacker can't
         // distinguish between a missing username and a wrong password
         byte[] randomPassword = new byte[256];
         RANDOM_GENERATOR.nextBytes(randomPassword);
         plainTextPassword = new String(randomPassword);
      }
      DigestCallback digestCallback = new DigestCallback();
      HmacCallback hmacCallback = new HmacCallback();
      executeCallbacks(digestCallback, hmacCallback);
      byte[] salt = generateSalt();
      try {
         ScramUtils.NewPasswordStringData data =
                  ScramUtils.byteArrayToStringData(ScramUtils.newPassword(plainTextPassword, salt, 4096,
                                                                          digestCallback.getDigest(),
                                                                          hmacCallback.getHmac()));
         return new UserData(data.salt, data.iterations, data.serverKey, data.storedKey);
      } catch (ScramException e) {
         throw new LoginException();
      }
   }

   private static byte[] generateSalt() {
      byte[] salt = new byte[32];
      RANDOM_GENERATOR.nextBytes(salt);
      return salt;
   }

   private void executeCallbacks(Callback... callbacks) throws LoginException {
      try {
         callbackHandler.handle(callbacks);
      } catch (UnsupportedCallbackException | IOException e) {
         throw new LoginException();
      }
   }

   @Override
   public boolean commit() throws LoginException {
      if (userData == null) {
         throw new LoginException();
      }
      subject.getPublicCredentials().add(userData);
      Set<UserPrincipal> authenticatedUsers = subject.getPrincipals(UserPrincipal.class);
      UserPrincipal principal = new UserPrincipal(user);
      principals.add(principal);
      authenticatedUsers.add(principal);
      for (UserPrincipal userPrincipal : authenticatedUsers) {
         Set<String> matchedRoles = roles.get(userPrincipal.getName());
         if (matchedRoles != null) {
            for (String entry : matchedRoles) {
               principals.add(new RolePrincipal(entry));
            }
         }
      }
      subject.getPrincipals().addAll(principals);
      return true;
   }

   @Override
   public boolean abort() throws LoginException {
      return true;
   }

   @Override
   public boolean logout() throws LoginException {
      subject.getPrincipals().removeAll(principals);
      principals.clear();
      subject.getPublicCredentials().remove(userData);
      userData = null;
      return true;
   }

   /**
    * Main method that could be used to encrypt given credentials for use in properties files
    *
    * @param args username password type [iterations]
    * @throws GeneralSecurityException if any security mechanism is not available on this JVM
    * @throws ScramException           if invalid data is supplied
    * @throws StringPrepError          if username can't be encoded according to SASL StringPrep
    * @throws IOException              if writing as properties failed
    */
   public static void main(String[] args) throws GeneralSecurityException, ScramException, StringPrepError,
                                          IOException {
      if (args.length < 2) {
         System.out.println("Usage: " + SCRAMPropertiesLoginModule.class.getSimpleName() +
                  " <username> <password> [<iterations>]");
         System.out.println("\ttype: " + getSupportedTypes());
         System.out.println("\titerations desired number of iteration (min value: " + MIN_ITERATIONS + ")");
         return;
      }
      String username = args[0];
      String password = args[1];
      Properties properties = new Properties();
      String encodedUser = StringPrep.prepAsQueryString(username);
      for (SCRAM scram : SCRAM.values()) {
         MessageDigest digest = MessageDigest.getInstance(scram.getDigest());
         Mac hmac = Mac.getInstance(scram.getHmac());
         byte[] salt = generateSalt();
         int iterations;
         if (args.length > 2) {
            iterations = Integer.parseInt(args[2]);
            if (iterations < MIN_ITERATIONS) {
               throw new IllegalArgumentException("minimum of " + MIN_ITERATIONS + " required!");
            }
         } else {
            iterations = MIN_ITERATIONS;
         }
         ScramUtils.NewPasswordStringData data =
                  ScramUtils.byteArrayToStringData(ScramUtils.newPassword(password, salt, iterations, digest, hmac));
         String encodedPassword = PasswordMaskingUtil.wrap(data.salt + SEPARATOR_PARAMETER + data.iterations +
                  SEPARATOR_PARAMETER + data.serverKey + SEPARATOR_PARAMETER + data.storedKey);
         properties.setProperty(encodedUser + SEPARATOR_MECHANISM + scram.name(), encodedPassword);
      }
      properties.store(System.out,
                       "Insert the lines stating with '" + encodedUser + "' into the desired user properties file");
   }

   private static SCRAM getTypeByString(String type) {
      SCRAM scram = Arrays.stream(SCRAM.values())
                          .filter(v -> v.getName().equals(type))
                          .findFirst()
                          .orElseThrow(() -> new IllegalArgumentException("unknown type " + type +
                                   ", supported ones are " + getSupportedTypes()));
      return scram;
   }

   private static String getSupportedTypes() {
      return String.join(", ", Arrays.stream(SCRAM.values()).map(SCRAM::getName).toArray(String[]::new));
   }

}

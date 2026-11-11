/*
 * Copyright 2016 Ognyan Bankov
 * <p>
 * All rights reserved. Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.activemq.artemis.spi.core.security.scram;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Provides static methods for working with SCRAM/SASL
 */
public class ScramUtils {
   private static final byte[] INT_1 = new byte[] {0, 0, 0, 1};

   private ScramUtils() {
      throw new AssertionError("non-instantiable utility class");
   }

   /**
    * Generates salted password.
    *
    * @param password        Clear form password, i.e. what user typed
    * @param salt            Salt to be used
    * @param iterationsCount Iterations for 'salting'
    * @param mac             HMAC to be used
    * @return salted password
    */
   public static byte[] generateSaltedPassword(final String password, byte[] salt, int iterationsCount,
                                               Mac mac) throws ScramException {
      SecretKeySpec key = new SecretKeySpec(password.getBytes(StandardCharsets.US_ASCII), mac.getAlgorithm());
      try {
         mac.init(key);
      } catch (InvalidKeyException e) {
         throw new ScramException("Incompatible key", e);
      }
      mac.update(salt);
      mac.update(INT_1);
      byte[] result = mac.doFinal();

      byte[] previous = null;
      for (int i = 1; i < iterationsCount; i++) {
         mac.update(Objects.requireNonNullElse(previous, result));
         previous = mac.doFinal();
         for (int x = 0; x < result.length; x++) {
            result[x] ^= previous[x];
         }
      }

      return result;
   }

   /**
    * Creates HMAC
    *
    * @param keyBytes key
    * @param hmacName HMAC name
    * @return Mac
    * @throws InvalidKeyException      if internal error occur while working with SecretKeySpec
    * @throws NoSuchAlgorithmException if hmacName is not supported by the java
    */
   public static Mac createHmac(final byte[] keyBytes, String hmacName) throws NoSuchAlgorithmException,
                                                                        InvalidKeyException {

      Mac mac = Mac.getInstance(hmacName);
      SecretKeySpec key = new SecretKeySpec(keyBytes, hmacName);
      mac.init(key);
      return mac;
   }

   /**
    * Computes HMAC byte array for given string
    *
    * @param key      key
    * @param hmacName HMAC name
    * @param string   string for which HMAC will be computed
    * @return computed HMAC
    * @throws InvalidKeyException      if internal error occur while working with SecretKeySpec
    * @throws NoSuchAlgorithmException if hmacName is not supported by the java
    */
   public static byte[] computeHmac(final byte[] key, String hmacName, final String string) throws InvalidKeyException,
                                                                                            NoSuchAlgorithmException {

      Mac mac = createHmac(key, hmacName);
      mac.update(string.getBytes(StandardCharsets.US_ASCII));
      return mac.doFinal();
   }

   public static byte[] computeHmac(final byte[] key, Mac hmac, final String string) throws ScramException {

      try {
         hmac.init(new SecretKeySpec(key, hmac.getAlgorithm()));
      } catch (InvalidKeyException e) {
         throw new ScramException("invalid key", e);
      }
      hmac.update(string.getBytes(StandardCharsets.US_ASCII));
      return hmac.doFinal();
   }

   /**
    * Checks if string is null or empty
    *
    * @param string String to be tested
    * @param name name of String to be tested and reported if null or empty
    */
   public static <T> String requireNonNullAndNotEmpty(String string, String name) {
      return Objects.requireNonNull(string == null ? null : (string.isEmpty() ? null : string), name + " is null or empty");
   }

   /**
    * Computes the data associated with new password like salted password, keys, etc
    * <p>
    * This method is supposed to be used by a server when user provides new clear form password. We don't want to save
    * it that way so we generate salted password and store it along with other data required by the SCRAM mechanism
    *
    * @param passwordClearText Clear form password, i.e. as provided by the user
    * @param salt              Salt to be used
    * @param iterations        Iterations for 'salting'
    * @param mac               HMAC name to be used
    * @param messageDigest     Digest name to be used
    * @return new password data while working with SecretKeySpec
    */
   public static NewPasswordByteArrayData newPassword(String passwordClearText, byte[] salt, int iterations,
                                                      MessageDigest messageDigest, Mac mac) throws ScramException {
      byte[] saltedPassword = ScramUtils.generateSaltedPassword(passwordClearText, salt, iterations, mac);

      byte[] clientKey = ScramUtils.computeHmac(saltedPassword, mac, "Client Key");
      byte[] storedKey = messageDigest.digest(clientKey);
      byte[] serverKey = ScramUtils.computeHmac(saltedPassword, mac, "Server Key");

      return new NewPasswordByteArrayData(saltedPassword, salt, clientKey, storedKey, serverKey, iterations);
   }

   /**
    * Transforms NewPasswordByteArrayData into NewPasswordStringData into database friendly (string) representation Uses
    * Base64 to encode the byte arrays into strings
    *
    * @param ba Byte array data
    * @return String data
    */
   public static NewPasswordStringData byteArrayToStringData(NewPasswordByteArrayData ba) {
      return new NewPasswordStringData(Base64.getEncoder().encodeToString(ba.saltedPassword),
                                       Base64.getEncoder().encodeToString(ba.salt),
                                       Base64.getEncoder().encodeToString(ba.clientKey),
                                       Base64.getEncoder().encodeToString(ba.storedKey),
                                       Base64.getEncoder().encodeToString(ba.serverKey), ba.iterations);
   }

   /**
    * New password data in database friendly format, i.e. Base64 encoded strings
    */
   @SuppressWarnings("unused")
   public static class NewPasswordStringData {
      public final String saltedPassword;
      public final String salt;
      public final String clientKey;
      public final String storedKey;
      public final String serverKey;
      public final int iterations;

      /**
       * Creates new NewPasswordStringData
       *
       * @param saltedPassword Salted password
       * @param salt           Used salt
       * @param clientKey      Client key
       * @param storedKey      Stored key
       * @param serverKey      Server key
       * @param iterations     Iterations for slating
       */
      public NewPasswordStringData(String saltedPassword, String salt, String clientKey, String storedKey,
                                   String serverKey, int iterations) {
         this.saltedPassword = saltedPassword;
         this.salt = salt;
         this.clientKey = clientKey;
         this.storedKey = storedKey;
         this.serverKey = serverKey;
         this.iterations = iterations;
      }
   }

   /**
    * New password data in byte array format
    */
   @SuppressWarnings("unused")
   public static class NewPasswordByteArrayData {
      public final byte[] saltedPassword;
      public final byte[] salt;
      public final byte[] clientKey;
      public final byte[] storedKey;
      public final byte[] serverKey;
      public final int iterations;

      /**
       * Creates new NewPasswordByteArrayData
       *
       * @param saltedPassword Salted password
       * @param salt           Used salt
       * @param clientKey      Client key
       * @param storedKey      Stored key
       * @param serverKey      Server key
       * @param iterations     Iterations for slating
       */
      public NewPasswordByteArrayData(byte[] saltedPassword, byte[] salt, byte[] clientKey, byte[] storedKey,
                                      byte[] serverKey, int iterations) {

         this.saltedPassword = saltedPassword;
         this.salt = salt;
         this.clientKey = clientKey;
         this.storedKey = storedKey;
         this.serverKey = serverKey;
         this.iterations = iterations;
      }
   }
}

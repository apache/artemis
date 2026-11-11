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
package org.apache.activemq.artemis.utils;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import org.apache.activemq.artemis.logs.ActiveMQUtilLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

/**
 * A DefaultSensitiveDataCodec
 * <p>
 * The default implementation of SensitiveDataCodec. This class is used when the user indicates in the config file to
 * use a masked password but doesn't give a codec implementation.
 * <p>
 * It supports one-way hash (digest) and two-way (encrypt-decrpt) algorithms The two-way uses "Blowfish" algorithm The
 * one-way uses "PBKDF2" hash algorithm
 */
public class DefaultSensitiveStringCodec implements SensitiveDataCodec<String> {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   public static final String ALGORITHM = "algorithm";
   public static final String BLOWFISH_KEY = "key";
   public static final String ONE_WAY = "one-way";
   public static final String TWO_WAY = "two-way";
   public static final String KEY_SYSTEM_PROPERTY = "artemis.default.sensitive.string.codec.key";

   private CodecAlgorithm algorithm;

   @Override
   public String decode(Object secret) throws Exception {
      if (algorithm == null) {
         throw new IllegalStateException("Algorithm not initialized");
      }

      return algorithm.decode((String) secret);
   }

   @Override
   public String encode(Object secret) throws Exception {
      if (algorithm == null) {
         throw new IllegalStateException("Algorithm not initialized");
      }

      return algorithm.encode((String) secret);
   }

   @Override
   public void init(Map<String, String> params) throws Exception {
      String algorithm = params.get(ALGORITHM);
      if (algorithm == null || algorithm.equals(TWO_WAY)) {
         //two way
         ActiveMQUtilLogger.LOGGER.deprecatedDefaultCodecTwoWayAlgorithm();
         this.algorithm = new BlowfishAlgorithm(params);
      } else if (algorithm.equals(ONE_WAY)) {
         this.algorithm = new PBKDF2Algorithm(params);
      } else {
         throw new IllegalArgumentException("Invalid algorithm: " + algorithm);
      }
   }

   /**
    * This main class is as documented on configuration-index.md, where the user can mask the password here.
    */
   public static void main(String[] args) throws Exception {
      if (args.length != 1) {
         System.err.println("Use: java -cp <classPath> org.apache.activemq.artemis.utils.DefaultSensitiveStringCodec password-to-encode");
         System.err.println("Error: no password on the args");
         System.exit(-1);
      }
      DefaultSensitiveStringCodec codec = new DefaultSensitiveStringCodec();
      Map<String, String> params = new HashMap<>();
      Properties properties = System.getProperties();
      synchronized (properties) {
         for (final String name : properties.stringPropertyNames()) {
            params.put(name, properties.getProperty(name));
         }
      }
      codec.init(params);
      Object encode = codec.encode(args[0]);

      System.out.println("Encoded password (without quotes): \"" + encode + "\"");
   }

   @Override
   public boolean verify(char[] inputValue, String storedValue) {
      return algorithm.verify(inputValue, storedValue);
   }

   private abstract static class CodecAlgorithm {

      protected Map<String, String> params;

      CodecAlgorithm(Map<String, String> params) {
         this.params = params;
      }

      public abstract String decode(String secret) throws Exception;
      public abstract String encode(String secret) throws Exception;

      public boolean verify(char[] inputValue, String storedValue) {
         return false;
      }
   }

   @Deprecated
   private class BlowfishAlgorithm extends CodecAlgorithm {

      private byte[] internalKey = "clusterpassword".getBytes();


      BlowfishAlgorithm(Map<String, String> params) {
         super(params);
         String key = params.get(BLOWFISH_KEY);
         if (key != null) {
            updateKey(key);
         } else {
            key = System.getProperty(KEY_SYSTEM_PROPERTY);
            if (key != null && !key.trim().isEmpty()) {
               logger.trace("Set key from system property {}", KEY_SYSTEM_PROPERTY);
               updateKey(key);
            }
            if (key == null) {
               final String matchingEnvVarName = envVarNameFromSystemPropertyName(KEY_SYSTEM_PROPERTY);
               key = getFromEnv(matchingEnvVarName);
               if (key != null) {
                  logger.trace("Set key from env var {}", matchingEnvVarName);
                  updateKey(key);
               }
            }
         }
      }

      private void updateKey(String key) {
         this.internalKey = key.getBytes();
      }

      @Override
      public String decode(String secret) throws Exception {
         SecretKeySpec key = new SecretKeySpec(internalKey, "Blowfish");

         byte[] encoding;
         try {
            encoding = new BigInteger(secret, 16).toByteArray();
         } catch (Exception ex) {
            if (logger.isDebugEnabled()) {
               logger.debug(ex.getMessage(), ex);
            }
            throw new IllegalArgumentException("Password must be encrypted.");
         }

         if (encoding.length % 8 != 0) {
            int length = encoding.length;
            int newLength = ((length / 8) + 1) * 8;
            int pad = newLength - length; // number of leading zeros
            byte[] old = encoding;
            encoding = new byte[newLength];
            System.arraycopy(old, 0, encoding, pad, old.length);
         }

         Cipher cipher = Cipher.getInstance("Blowfish");
         cipher.init(Cipher.DECRYPT_MODE, key);
         byte[] decode = cipher.doFinal(encoding);

         return new String(decode);
      }

      @Override
      public String encode(String secret) throws Exception {
         SecretKeySpec key = new SecretKeySpec(internalKey, "Blowfish");

         Cipher cipher = Cipher.getInstance("Blowfish");
         cipher.init(Cipher.ENCRYPT_MODE, key);
         byte[] encoding = cipher.doFinal(secret.getBytes());
         BigInteger n = new BigInteger(encoding);
         return n.toString(16);
      }

      @Override
      public boolean verify(char[] inputValue, String storedValue) {
         try {
            return Objects.equals(storedValue, encode(String.valueOf(inputValue)));
         } catch (Exception e) {
            logger.debug("Exception on verifying:", e);
            return false;
         }
      }
   }

   protected String getFromEnv(final String envVarName) {
      return System.getenv(envVarName);
   }

   public static String envVarNameFromSystemPropertyName(final String systemPropertyName) {
      return systemPropertyName.replace(".", "_").toUpperCase(Locale.getDefault());
   }

   private static class PBKDF2Algorithm extends CodecAlgorithm {
      private static final String SEPARATOR = ":";
      private String sceretKeyAlgorithm = "PBKDF2WithHmacSHA1";
      private int keyLength = 64 * 8;
      private int saltLength = 32;
      private int iterations = 1024;
      private SecretKeyFactory skf;
      private static SecureRandom sr;

      PBKDF2Algorithm(Map<String, String> params) throws NoSuchAlgorithmException {
         super(params);
         skf = SecretKeyFactory.getInstance(sceretKeyAlgorithm);
         if (sr == null) {
            sr = new SecureRandom();
         }
      }

      @Override
      public String decode(String secret) throws Exception {
         throw new IllegalArgumentException("Algorithm doesn't support decoding");
      }

      public byte[] getSalt() {
         byte[] salt = new byte[this.saltLength];
         sr.nextBytes(salt);
         return salt;
      }

      @Override
      public String encode(String secret) throws Exception {
         char[] chars = secret.toCharArray();
         byte[] salt = getSalt();

         StringBuilder builder = new StringBuilder();
         builder.append(iterations).append(SEPARATOR).append(ByteUtil.bytesToHex(salt)).append(SEPARATOR);

         PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, keyLength);

         byte[] hash = skf.generateSecret(spec).getEncoded();
         String hexValue = ByteUtil.bytesToHex(hash);
         builder.append(hexValue);

         return builder.toString();
      }

      @Override
      public boolean verify(char[] plainChars, String storedValue) {
         String[] parts = storedValue.split(SEPARATOR);
         int originalIterations = Integer.parseInt(parts[0]);
         byte[] salt = ByteUtil.hexToBytes(parts[1]);
         byte[] originalHash = ByteUtil.hexToBytes(parts[2]);

         PBEKeySpec spec = new PBEKeySpec(plainChars, salt, originalIterations, originalHash.length * 8);
         byte[] newHash;

         try {
            newHash = skf.generateSecret(spec).getEncoded();
         } catch (InvalidKeySpecException e) {
            return false;
         }

         return Arrays.equals(newHash, originalHash);
      }
   }
}

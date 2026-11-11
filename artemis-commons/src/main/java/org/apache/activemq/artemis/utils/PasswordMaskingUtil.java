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

import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.logs.ActiveMQUtilBundle;
import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;

public final class PasswordMaskingUtil {

   public static final String BEGIN_ENC = "ENC(";
   public static final String END_ENC = ")";

   private PasswordMaskingUtil() {

   }

   /**
    * This method deals with password masking and returns the password in its plain text form.
    *
    * @param password   : the original value of password string; interpreted as masked if wrapped in ENC() or as plain
    *                   text otherwise.
    * @param codecClass : the codec used to decode the password. Only when the password is interpreted as masked will
    *                   this codec be used. Ignored otherwise.
    */
   public static String resolveMask(String password, String codecClass) throws Exception {
      return resolveMask(null, password, codecClass);
   }

   /**
    * This method deals with password masking and returns the password in its plain text form.
    *
    * @param maskPassword : explicit mask flag. If it's true, the password is interpreted as masked. If it is false, the
    *                     password is interpreted as plain text. if it is null, the password will be interpreted as
    *                     masked if the password is wrapped in ENC(), or as plain text otherwise.
    * @param password     : the original value of password string
    * @param codecClass   : the codec used to decode the password. Only when the password is interpreted as masked will
    *                     this codec be used. Ignored otherwise.
    */
   public static String resolveMask(Boolean maskPassword, String password, String codecClass) throws Exception {
      String plainText = password;
      if (maskPassword == null) {
         if (isEncMasked(password)) {
            //masked
            String bareMaskedPassword = unwrap(password);
            plainText = getCodec(codecClass).decode(bareMaskedPassword);
         }
      } else if (maskPassword) {
         plainText = getCodec(codecClass).decode(password);
      }
      return plainText;
   }

   public static boolean isEncMasked(String password) {
      return password == null ? false : (password.startsWith(BEGIN_ENC) && password.endsWith(END_ENC));
   }

   //remove ENC() from the password body
   public static String unwrap(String password) {
      return password.substring(4, password.length() - 1);
   }

   public static String wrap(String password) {
      return BEGIN_ENC + password + END_ENC;
   }

   private static final class LazyPlainTextProcessorHolder {

      private LazyPlainTextProcessorHolder() {
      }

      private static final HashProcessor INSTANCE = new NoHashProcessor();

   }

   private static final class LazySecureProcessorHolder {

      private LazySecureProcessorHolder() {

      }

      private static final HashProcessor INSTANCE;
      private static final Exception EXCEPTION;

      static {
         HashProcessor processor = null;
         Exception exception = null;
         final String codecDesc = new StringBuilder().append(DefaultSensitiveStringCodec.class.getName()).append(";").append(DefaultSensitiveStringCodec.ALGORITHM).append("=").append(DefaultSensitiveStringCodec.ONE_WAY).toString();
         try {
            final DefaultSensitiveStringCodec codec = (DefaultSensitiveStringCodec) PasswordMaskingUtil.getCodec(codecDesc);
            processor = new SecureHashProcessor(codec);
         } catch (Exception e) {
            //THE STACK TRACE IS THE ORIGINAL ONE!
            exception = e;
         } finally {
            EXCEPTION = exception;
            INSTANCE = processor;
         }
      }
   }

   //stored password takes 2 forms, ENC() or plain text
   public static HashProcessor getHashProcessor(String storedPassword) {
      return getHashProcessor(storedPassword, null);
   }

   public static HashProcessor getHashProcessor(String storedPassword, HashProcessor secureHashProcessor) {

      if (!isEncoded(storedPassword)) {
         return LazyPlainTextProcessorHolder.INSTANCE;
      }
      if (secureHashProcessor != null) {
         return secureHashProcessor;
      }
      final Exception secureProcessorException = LazySecureProcessorHolder.EXCEPTION;
      if (secureProcessorException != null) {
         //reuse old descriptions/messages of the exception but refill the stack trace
         throw new RuntimeException(secureProcessorException);
      }
      return LazySecureProcessorHolder.INSTANCE;
   }

   private static boolean isEncoded(String storedPassword) {
      return storedPassword == null || isEncMasked(storedPassword);
   }

   public static HashProcessor getHashProcessor() {
      HashProcessor processor = LazySecureProcessorHolder.INSTANCE;
      //it can be null due to a previous failed attempts to instantiate it!
      if (processor == null) {
         processor = LazyPlainTextProcessorHolder.INSTANCE;
      }
      return processor;
   }

   /**
    * Loading the codec class.
    *
    * @param codecDesc This parameter must have the following format:
    *                  {@code <full qualified class name>;key=value;key1=value1;...} where only
    *                  {@code <full qualified class name>} is required. key/value pairs are optional
    */
   public static SensitiveDataCodec<String> getCodec(String codecDesc) throws ActiveMQException {
      SensitiveDataCodec<String> codecInstance;

      if (codecDesc == null) {
         return getDefaultCodec();
      }

      // semi colons
      String[] parts = codecDesc.split(";");
      if (parts.length < 1) {
         throw new ActiveMQException(ActiveMQExceptionType.ILLEGAL_STATE, "Invalid PasswordCodec value: " + codecDesc);
      }

      final String codecClassName = parts[0];

      // load class
      codecInstance = SecurityManagerShim.doPrivileged((PrivilegedAction<SensitiveDataCodec<String>>) () -> {
         ServiceLoader<SensitiveDataCodec> serviceLoader = ServiceLoader.load(SensitiveDataCodec.class, PasswordMaskingUtil.class.getClassLoader());
         try {
            // Service load the codec, if a service is available
            for (SensitiveDataCodec<String> codec : serviceLoader) {
               if (codec.getClass().getCanonicalName().equals(codecClassName)) {
                  return codec.getClass().getDeclaredConstructor().newInstance();
               }
            }
         } catch (Exception e) {
            // Will ignore the exception and attempt to load the class directly
         }
         try {
            // If a service is not available, load the codec class using this class's class loader
            return (SensitiveDataCodec<String>) PasswordMaskingUtil.class.getClassLoader().loadClass(codecClassName).getDeclaredConstructor().newInstance();
         } catch (Exception e) {
            try {
               // As a last resort, load the codec class using the current thread's context class loader
               return (SensitiveDataCodec<String>) Thread.currentThread().getContextClassLoader().loadClass(codecClassName).getDeclaredConstructor().newInstance();
            } catch (Exception e2) {
               throw ActiveMQUtilBundle.BUNDLE.errorCreatingCodec(codecClassName, e2);
            }
         }
      });

      Map<String, String> props = new HashMap<>();
      if (parts.length > 1) {
         for (int i = 1; i < parts.length; i++) {
            String[] keyVal = parts[i].split("=");
            if (keyVal.length != 2) {
               throw ActiveMQUtilBundle.BUNDLE.invalidProperty(parts[i]);
            }
            props.put(keyVal[0], keyVal[1]);
         }
      }
      try {
         codecInstance.init(props);
      } catch (Exception e) {
         throw new ActiveMQException("Fail to init codec", e, ActiveMQExceptionType.SECURITY_EXCEPTION);
      }

      return codecInstance;
   }

   public static DefaultSensitiveStringCodec getDefaultCodec() {
      return getDefaultCodec(Collections.emptyMap());
   }

   public static DefaultSensitiveStringCodec getDefaultCodec(Map<String, String> params) {
      DefaultSensitiveStringCodec defaultCodec = new DefaultSensitiveStringCodec();
      try {
         defaultCodec.init(params);
      } catch (Exception e) {
         throw ActiveMQUtilBundle.BUNDLE.errorCreatingCodec(DefaultSensitiveStringCodec.class.getName(), e);
      }
      return defaultCodec;
   }

}

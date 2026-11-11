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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.core.client.ActiveMQClientLogger;
import org.apache.activemq.artemis.core.client.ActiveMQClientMessageBundle;

public class ConfigurationHelper {

   public static String getStringProperty(final String propName, final String def, final Map<String, ?> props) {
      if (props == null) {
         return def;
      }

      Object prop = props.get(propName);

      if (prop == null) {
         return def;
      } else {
         if (prop instanceof String == false) {
            return prop.toString();
         } else {
            return (String) prop;
         }
      }
   }

   public static int getIntProperty(final String propName, final int def, final Map<String, ?> props) {
      if (props == null) {
         return def;
      }
      Object prop = props.get(propName);

      if (prop == null) {
         return def;
      } else {
         // The resource adapter will aways send Strings, hence the conversion here
         if (prop instanceof String string) {
            try {
               return Integer.parseInt(string);
            } catch (NumberFormatException e) {
               ActiveMQClientLogger.LOGGER.propertyNotInteger(propName, prop.getClass().getName());

               return def;
            }
         } else if (prop instanceof Number == false) {
            ActiveMQClientLogger.LOGGER.propertyNotInteger(propName, prop.getClass().getName());

            return def;
         } else {
            return ((Number) prop).intValue();
         }
      }
   }

   public static long getLongProperty(final String propName, final long def, final Map<String, ?> props) {
      if (props == null) {
         return def;
      }

      Object prop = props.get(propName);

      if (prop == null) {
         return def;
      } else {
         // The resource adapter will aways send Strings, hence the conversion here
         if (prop instanceof String string) {
            try {
               return Long.parseLong(string);
            } catch (NumberFormatException e) {
               ActiveMQClientLogger.LOGGER.propertyNotLong(propName, prop.getClass().getName());
               return def;
            }
         } else if (prop instanceof Number == false) {
            ActiveMQClientLogger.LOGGER.propertyNotLong(propName, prop.getClass().getName());

            return def;
         } else {
            return ((Number) prop).longValue();
         }
      }
   }

   public static boolean getBooleanProperty(final String propName, final boolean def, final Map<String, ?> props) {
      if (props == null) {
         return def;
      }

      Object prop = props.get(propName);

      if (prop == null) {
         return def;
      } else {
         // The resource adapter will aways send Strings, hence the conversion here
         if (prop instanceof String string) {
            return Boolean.parseBoolean(string);
         } else if (prop instanceof Boolean == false) {
            ActiveMQClientLogger.LOGGER.propertyNotBoolean(propName, prop.getClass().getName());

            return def;
         } else {
            return (Boolean) prop;
         }
      }
   }

   public static Set<String> checkKeys(final Set<String> allowableKeys, final Set<String> keys) {
      Set<String> invalid = new HashSet<>();

      for (String key : keys) {
         if (!allowableKeys.contains(key)) {
            invalid.add(key);
         }
      }
      return invalid;
   }

   public static Set<String> checkKeysExist(final Set<String> requiredKeys, final Set<String> keys) {
      Set<String> invalid = new HashSet<>(requiredKeys);

      for (String key : keys) {
         if (requiredKeys.contains(key)) {
            invalid.remove(key);
         }
      }
      return invalid;
   }

   public static String stringSetToCommaListString(final Set<String> invalid) {
      StringBuilder sb = new StringBuilder();
      int count = 0;
      for (String key : invalid) {
         sb.append(key);
         if (count != invalid.size() - 1) {
            sb.append(", ");
         }
         count++;
      }
      return sb.toString();
   }

   public static String getPasswordProperty(final String propName,
                                            final String def,
                                            final Map<String, ?> props,
                                            String defaultMaskPassword,
                                            String defaultPasswordCodec) {
      if (props == null) {
         return def;
      }

      Object prop = props.get(propName);

      if (prop == null) {
         return def;
      }

      String value = prop.toString();
      Object useMaskObject = props.get(defaultMaskPassword);
      Boolean useMask;
      if (useMaskObject instanceof String string) {
         useMask = Boolean.parseBoolean(string);
      } else {
         useMask = (Boolean) useMaskObject;
      }
      final String classImpl = (String) props.get(defaultPasswordCodec);
      try {
         return PasswordMaskingUtil.resolveMask(useMask, value, classImpl);
      } catch (Exception e) {
         throw ActiveMQClientMessageBundle.BUNDLE.errordecodingPassword(e);
      }
   }

   public static double getDoubleProperty(String name, double def, Map<String, ?> props) {
      if (props == null) {
         return def;
      }
      Object prop = props.get(name);
      if (prop == null) {
         return def;
      } else {
         String value = prop.toString();
         return Double.parseDouble(value);
      }
   }
}

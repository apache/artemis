/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.server.routing;

import javax.security.auth.Subject;

import org.apache.activemq.artemis.spi.core.remoting.Connection;
import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.invoke.MethodHandles;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KeyResolver {
   public static final String NULL_KEY_VALUE = "NULL";


   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private static final char SOCKET_ADDRESS_DELIMITER = ':';
   private static final String SOCKET_ADDRESS_PREFIX = "/";


   private final KeyType key;

   private volatile Pattern keyFilter;


   public KeyType getKey() {
      return key;
   }

   public String getKeyFilter() {
      return keyFilter != null ? keyFilter.pattern() : null;
   }

   public KeyResolver(KeyType key, String keyFilter) {
      this.key = key;
      setKeyFilter(keyFilter);
   }

   public String resolve(Connection connection, String clientID, String username) {
      String keyValue = null;

      switch (key) {
         case CLIENT_ID:
            keyValue = clientID;
            break;
         case SNI_HOST:
            if (connection != null) {
               keyValue = connection.getSNIHostName();
            }
            break;
         case SOURCE_IP:
            if (connection != null && connection.getRemoteAddress() != null) {
               keyValue = connection.getRemoteAddress();

               boolean hasPrefix = keyValue.startsWith(SOCKET_ADDRESS_PREFIX);
               int delimiterIndex = keyValue.lastIndexOf(SOCKET_ADDRESS_DELIMITER);

               if (hasPrefix || delimiterIndex > 0) {
                  keyValue = keyValue.substring(hasPrefix ? SOCKET_ADDRESS_PREFIX.length() : 0,
                     delimiterIndex > 0 ? delimiterIndex : keyValue.length());
               }
            }
            break;
         case USER_NAME:
            keyValue = username;
            break;
         case ROLE_NAME:
            if (connection != null && connection.getProtocolConnection() != null) {
               Subject subject = connection.getProtocolConnection().getSubject();
               if (subject != null) {
                  for (RolePrincipal candidateRole : subject.getPrincipals(RolePrincipal.class)) {
                     String roleName = candidateRole.getName();
                     if (roleName != null) {
                        if (keyFilter != null) {
                           Matcher keyMatcher = keyFilter.matcher(roleName);
                           if (keyMatcher.find()) {
                              keyValue = keyMatcher.group();
                              logger.debug("role match for {} via {}", roleName, keyMatcher);
                              return keyValue;
                           }
                        } else {
                           // with no filter, first role is the candidate
                           keyValue = roleName;
                           logger.debug("first role match: {}", roleName);
                           return keyValue;
                        }
                     }
                  }
               }
            }
            break;
         default:
            throw new IllegalStateException("Unexpected value: " + key);
      }

      logger.debug("keyValue for {}: {}", key, keyValue);

      if (keyValue == null) {
         keyValue = NULL_KEY_VALUE;
      } else if (keyFilter != null) {
         Matcher keyMatcher = keyFilter.matcher(keyValue);

         if (keyMatcher.find()) {
            keyValue = keyMatcher.group();

            logger.debug("keyValue for {} matches filter {}: {}", key, keyFilter, keyValue);
         } else {
            keyValue = NULL_KEY_VALUE;

            logger.debug("keyValue for {} doesn't matches filter {}", key, keyFilter);
         }
      }

      return keyValue;
   }

   public void setKeyFilter(String regExp) {
      if (regExp == null || regExp.isBlank()) {
         this.keyFilter = null;
      } else {
         this.keyFilter = Pattern.compile(regExp);
      }
   }
}

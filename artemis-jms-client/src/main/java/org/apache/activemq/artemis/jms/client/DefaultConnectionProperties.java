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

package org.apache.activemq.artemis.jms.client;

import java.security.PrivilegedAction;

import org.apache.activemq.artemis.utils.sm.SecurityManagerShim;

/**
 * This class will provide default properties for constructors
 * <table border='1'>
 * <caption>Default properties</caption>
 * <tr> <td>Name</td> <td>Default Value</td></tr>
 * <tr> <td>AMQ_HOST or org.apache.activemq.AMQ_HOST</td> <td>localhost</td></tr>
 * <tr><td>AMQ_PORT or org.apache.activemq.AMQ_PORT</td> <td>61616</td></tr>
 * <tr><td>BROKER_BIND_URL or org.apache.activemq.BROKER_BIND_URL</td> <td>tcp://${AMQ_HOST}:${AMQ_PORT}</td></tr>
 * <tr><td>AMQ_USER or org.apache.activemq.AMQ_USER</td> <td>null</td></tr>
 * <tr><td>AMQ_PASSWORD or org.apache.activemq.AMQ_PASSWORD</td> <td>null</td></tr>
 * </table>
 */
public class DefaultConnectionProperties {

   public static final String AMQ_HOST = "AMQ_HOST";
   public static final String AMQ_PORT = "AMQ_PORT";
   public static final String AMQ_USER = "AMQ_USER";
   public static final String AMQ_PASSWORD = "AMQ_PASSWORD";
   public static final String AMQ_PASSWORD_CODEC = "AMQ_PASSWORD_CODEC";
   public static final String BROKER_BIND_URL = "BROKER_BIND_URL";
   public static final String PREFIX = "org.apache.activemq.";

   public static String DEFAULT_BROKER_HOST;
   public static int DEFAULT_BROKER_PORT;
   public static String DEFAULT_BROKER_BIND_URL;
   public static String DEFAULT_BROKER_URL;
   public static String DEFAULT_USER;
   public static String DEFAULT_PASSWORD;
   public static String DEFAULT_PASSWORD_CODEC;

   static String getProperty(final String defaultValue, final String... propertyNames) {
      return SecurityManagerShim.doPrivileged((PrivilegedAction<String>) () -> {
         for (String name : propertyNames) {
            String property = System.getProperty(name);
            if (property != null && !property.isEmpty()) {
               return property;
            }
         }
         return defaultValue;
      });
   }

   static {
      initialize();
   }

   public static void initialize() {
      String host = getProperty("localhost", AMQ_HOST, PREFIX + AMQ_HOST);
      String port = getProperty("61616", AMQ_PORT, PREFIX + AMQ_PORT);
      DEFAULT_BROKER_HOST = host;
      DEFAULT_BROKER_PORT = Integer.parseInt(port);
      String url = getProperty("tcp://" + host + ":" + port, PREFIX + BROKER_BIND_URL, BROKER_BIND_URL);
      DEFAULT_USER = getProperty(null, AMQ_USER, PREFIX + AMQ_USER);
      DEFAULT_PASSWORD = getProperty(null, AMQ_PASSWORD, PREFIX + AMQ_PASSWORD);
      DEFAULT_PASSWORD_CODEC = getProperty(null, AMQ_PASSWORD_CODEC, PREFIX + AMQ_PASSWORD_CODEC);

      DEFAULT_BROKER_BIND_URL = url;
      // TODO: improve this once we implement failover:// as ActiveMQ5 does
      DEFAULT_BROKER_URL = DEFAULT_BROKER_BIND_URL;
   }
}

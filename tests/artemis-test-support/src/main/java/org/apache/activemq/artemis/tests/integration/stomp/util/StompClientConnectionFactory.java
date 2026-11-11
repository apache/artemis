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
package org.apache.activemq.artemis.tests.integration.stomp.util;

import java.io.IOException;
import java.net.URI;

public class StompClientConnectionFactory {

   public static final String LATEST_VERSION = "1.2";

   //create a raw connection to the host.
   public static StompClientConnection createClientConnection(String version,
                                                              String host,
                                                              int port) throws IOException {
      if ("1.0".equals(version)) {
         return new StompClientConnectionV10(host, port);
      }
      if ("1.1".equals(version)) {
         return new StompClientConnectionV11(host, port);
      }
      if ("1.2".equals(version)) {
         return new StompClientConnectionV12(host, port);
      }
      return null;
   }

   public static StompClientConnection createClientConnection(URI uri) throws Exception {
      String version = getStompVersionFromURI(uri);
      if ("1.0".equals(version)) {
         return new StompClientConnectionV10(uri);
      }
      if ("1.1".equals(version)) {
         return new StompClientConnectionV11(uri);
      }
      if ("1.2".equals(version)) {
         return new StompClientConnectionV12(uri);
      }
      return null;
   }

   public static StompClientConnection createClientConnection(URI uri, boolean autoConnect) throws Exception {
      String version = getStompVersionFromURI(uri);
      if ("1.0".equals(version)) {
         return new StompClientConnectionV10(uri, autoConnect);
      }
      if ("1.1".equals(version)) {
         return new StompClientConnectionV11(uri, autoConnect);
      }
      if ("1.2".equals(version)) {
         return new StompClientConnectionV12(uri, autoConnect);
      }
      return null;
   }

   public static String getStompVersionFromURI(URI uri) {
      String scheme = uri.getScheme();
      if (scheme.contains("10")) {
         return "1.0";
      }
      if (scheme.contains("11")) {
         return "1.1";
      }
      if (scheme.contains("12")) {
         return "1.2";
      }
      return LATEST_VERSION;
   }

   public static void main(String[] args) throws Exception {
      StompClientConnection connection = StompClientConnectionFactory.createClientConnection("1.0", "localhost", 61613);

      System.out.println("created a new connection: " + connection);

      connection.connect();

      System.out.println("connected.");

      connection.disconnect();
      System.out.println("Simple stomp client works.");

   }
}

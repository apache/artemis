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

package org.apache.activemq.artemis.uri.schema.serverLocator;

/**
 * This will represent all the possible options you could setup on URLs When parsing the URL this will serve as an
 * intermediate object And it could also be a pl
 */
public class ConnectionOptions {

   private boolean ha;

   private String host;

   private int port;

   public ConnectionOptions setHost(String host) {
      this.host = host;
      return this;
   }

   public String getHost() {
      return host;
   }

   public ConnectionOptions setPort(int port) {
      this.port = port;
      return this;
   }

   public int getPort() {
      return port;
   }

   public boolean isHa() {
      return ha;
   }

   public void setHa(boolean ha) {
      this.ha = ha;
   }

   /**
    * Se need both options (ha / HA in case of typos on the URI)
    */
   public boolean isHA() {
      return ha;
   }

   public void setHA(boolean ha) {
      this.ha = ha;
   }

   @Override
   public String toString() {
      return "ConnectionOptions{" +
         "ha=" + ha +
         '}';
   }
}

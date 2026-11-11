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
package org.apache.activemq.transport.tcp;

import javax.net.ssl.SSLServerSocket;
import java.io.IOException;

public class StubSSLServerSocket extends SSLServerSocket {

   public static final int UNTOUCHED = -1;
   public static final int FALSE = 0;
   public static final int TRUE = 1;

   private int wantClientAuthStatus = UNTOUCHED;
   private int needClientAuthStatus = UNTOUCHED;

   public StubSSLServerSocket() throws IOException {

   }

   public int getWantClientAuthStatus() {
      return wantClientAuthStatus;
   }

   public int getNeedClientAuthStatus() {
      return needClientAuthStatus;
   }

   @Override
   public void setWantClientAuth(boolean want) {
      wantClientAuthStatus = want ? TRUE : FALSE;
   }

   @Override
   public void setNeedClientAuth(boolean need) {
      needClientAuthStatus = need ? TRUE : FALSE;
   }

   // --- Stubbed methods ---

   @Override
   public boolean getEnableSessionCreation() {
      return false;
   }

   @Override
   public String[] getEnabledCipherSuites() {
      return null;
   }

   @Override
   public String[] getEnabledProtocols() {
      return null;
   }

   @Override
   public boolean getNeedClientAuth() {
      return false;
   }

   @Override
   public String[] getSupportedCipherSuites() {
      return null;
   }

   @Override
   public String[] getSupportedProtocols() {
      return null;
   }

   @Override
   public boolean getUseClientMode() {
      return false;
   }

   @Override
   public boolean getWantClientAuth() {
      return false;
   }

   @Override
   public void setEnableSessionCreation(boolean flag) {
   }

   @Override
   public void setEnabledCipherSuites(String[] suites) {
   }

   @Override
   public void setEnabledProtocols(String[] protocols) {
   }

   @Override
   public void setUseClientMode(boolean mode) {
   }
}

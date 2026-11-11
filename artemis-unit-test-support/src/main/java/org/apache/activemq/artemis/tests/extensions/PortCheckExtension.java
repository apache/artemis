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
package org.apache.activemq.artemis.tests.extensions;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;

public class PortCheckExtension implements Extension, BeforeEachCallback, AfterEachCallback {

   final int[] ports;

   public PortCheckExtension(int... ports) {
      this.ports = ports;
   }

   @Override
   public void beforeEach(ExtensionContext context) throws Exception {
      String testName = context.getRequiredTestMethod().getName();
      for (int p : ports) {
         if (!checkAvailable(p)) {
            fail("a previous test is using port " + p + " before " + testName);
         }
      }
   }

   @Override
   public void afterEach(ExtensionContext context) throws Exception {
      String testName = context.getRequiredTestMethod().getName();
      for (int p : ports) {
         if (!checkAvailable(p)) {
            fail(testName + " has left a server socket open on port " + p);
         }
      }
   }

   public static boolean checkAvailable(int port) {
      ServerSocket s = null;
      try {
         s = new ServerSocket();
         s.bind(new InetSocketAddress("localhost", port));
         return true;
      } catch (IOException e) {
         e.printStackTrace();
         return false;
      } finally {
         try {
            s.close();
         } catch (Throwable ignored) {
         }
      }
   }

}

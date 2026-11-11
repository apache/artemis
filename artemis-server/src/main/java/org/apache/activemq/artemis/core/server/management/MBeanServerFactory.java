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
package org.apache.activemq.artemis.core.server.management;

import javax.management.MBeanServer;
import java.lang.management.ManagementFactory;
import java.util.List;

public class MBeanServerFactory {

   private boolean locateExistingServerIfPossible = false;
   private String defaultDomain;
   private boolean registerWithFactory = true;
   private MBeanServer server;
   private boolean newlyRegistered = false;

   public boolean isLocateExistingServerIfPossible() {
      return locateExistingServerIfPossible;
   }

   public void setLocateExistingServerIfPossible(boolean locateExistingServerIfPossible) {
      this.locateExistingServerIfPossible = locateExistingServerIfPossible;
   }

   public String getDefaultDomain() {
      return defaultDomain;
   }

   public void setDefaultDomain(String defaultDomain) {
      this.defaultDomain = defaultDomain;
   }

   public boolean isRegisterWithFactory() {
      return registerWithFactory;
   }

   public void setRegisterWithFactory(boolean registerWithFactory) {
      this.registerWithFactory = registerWithFactory;
   }

   public boolean isNewlyRegistered() {
      return newlyRegistered;
   }

   public void setNewlyRegistered(boolean newlyRegistered) {
      this.newlyRegistered = newlyRegistered;
   }

   public MBeanServer getServer() throws Exception {
      if (this.server == null) {
         init();
      }
      return server;
   }

   public void init() throws Exception {
      if (this.locateExistingServerIfPossible) {
         List servers = javax.management.MBeanServerFactory.findMBeanServer(null);
         if (servers != null && !servers.isEmpty()) {
            this.server = (MBeanServer) servers.get(0);
         }
         if (this.server == null) {
            this.server = ManagementFactory.getPlatformMBeanServer();
         }
         if (this.server == null) {
            throw new Exception("Unable to locate MBeanServer");
         }
      }
      if (this.server == null) {
         if (this.registerWithFactory) {
            this.server = javax.management.MBeanServerFactory.createMBeanServer(this.defaultDomain);
         } else {
            this.server = javax.management.MBeanServerFactory.newMBeanServer(this.defaultDomain);
         }
         this.newlyRegistered = this.registerWithFactory;
      }
   }

   public void destroy() throws Exception {

      if (this.newlyRegistered) {
         javax.management.MBeanServerFactory.releaseMBeanServer(this.server);
      }
   }

}

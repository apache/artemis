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
package org.apache.activemq.artemis.core.server.cluster.ha;

import java.util.Map;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.core.io.IOCriticalErrorListener;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.PrimaryActivation;
import org.apache.activemq.artemis.core.server.impl.SharedStorePrimaryActivation;

public class SharedStorePrimaryPolicy implements HAPolicy<PrimaryActivation> {

   private boolean failoverOnServerShutdown = ActiveMQDefaultConfiguration.isDefaultFailoverOnServerShutdown();
   private boolean waitForActivation = ActiveMQDefaultConfiguration.isDefaultWaitForActivation();

   private SharedStoreBackupPolicy sharedStoreBackupPolicy;

   public SharedStorePrimaryPolicy() {
   }

   public SharedStorePrimaryPolicy(boolean failoverOnServerShutdown, boolean waitForActivation) {
      this.failoverOnServerShutdown = failoverOnServerShutdown;
      this.waitForActivation = waitForActivation;
   }

   @Deprecated
   public long getFailbackDelay() {
      return -1;
   }

   @Deprecated
   public void setFailbackDelay(long failbackDelay) {
   }

   public boolean isFailoverOnServerShutdown() {
      return failoverOnServerShutdown;
   }

   public void setFailoverOnServerShutdown(boolean failoverOnServerShutdown) {
      this.failoverOnServerShutdown = failoverOnServerShutdown;
   }

   @Override
   public boolean isWaitForActivation() {
      return waitForActivation;
   }

   public void setWaitForActivation(boolean waitForActivation) {
      this.waitForActivation = waitForActivation;
   }

   public SharedStoreBackupPolicy getSharedStoreBackupPolicy() {
      return sharedStoreBackupPolicy;
   }

   public void setSharedStoreBackupPolicy(SharedStoreBackupPolicy sharedStoreBackupPolicy) {
      this.sharedStoreBackupPolicy = sharedStoreBackupPolicy;
   }

   @Override
   public boolean isSharedStore() {
      return true;
   }

   @Override
   public boolean isBackup() {
      return false;
   }

   @Override
   public boolean canScaleDown() {
      return false;
   }

   @Override
   public PrimaryActivation createActivation(ActiveMQServerImpl server,
                                             boolean wasPrimary,
                                             Map<String, Object> activationParams,
                                             IOCriticalErrorListener ioCriticalErrorListener) {
      return new SharedStorePrimaryActivation(server, this, ioCriticalErrorListener);
   }

   @Override
   public String getBackupGroupName() {
      return null;
   }
}

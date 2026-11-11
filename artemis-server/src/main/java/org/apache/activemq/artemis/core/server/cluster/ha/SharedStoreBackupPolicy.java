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
import org.apache.activemq.artemis.core.server.impl.Activation;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.SharedStoreBackupActivation;

public class SharedStoreBackupPolicy extends BackupPolicy {

   private boolean failoverOnServerShutdown = ActiveMQDefaultConfiguration.isDefaultFailoverOnServerShutdown();

   private boolean allowAutoFailBack = ActiveMQDefaultConfiguration.isDefaultAllowAutoFailback();

   private boolean isWaitForActivation = ActiveMQDefaultConfiguration.isDefaultWaitForActivation();

   //this is how we act once we have failed over
   private SharedStorePrimaryPolicy sharedStorePrimaryPolicy;

   public SharedStoreBackupPolicy() {
   }

   public SharedStoreBackupPolicy(boolean failoverOnServerShutdown,
                                  boolean restartBackup,
                                  boolean allowAutoFailBack,
                                  ScaleDownPolicy scaleDownPolicy) {
      this.failoverOnServerShutdown = failoverOnServerShutdown;
      this.restartBackup = restartBackup;
      this.allowAutoFailBack = allowAutoFailBack;
      this.scaleDownPolicy = scaleDownPolicy;
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

   public SharedStorePrimaryPolicy getSharedStorePrimaryPolicy() {
      if (sharedStorePrimaryPolicy == null) {
         sharedStorePrimaryPolicy = new SharedStorePrimaryPolicy(failoverOnServerShutdown, isWaitForActivation);
      }
      return sharedStorePrimaryPolicy;
   }

   public void setSharedStorePrimaryPolicy(SharedStorePrimaryPolicy sharedStorePrimaryPolicy) {
      this.sharedStorePrimaryPolicy = sharedStorePrimaryPolicy;
   }

   @Override
   public boolean isSharedStore() {
      return true;
   }

   @Override
   public boolean canScaleDown() {
      return scaleDownPolicy != null;
   }

   public boolean isAllowAutoFailBack() {
      return allowAutoFailBack;
   }

   public void setAllowAutoFailBack(boolean allowAutoFailBack) {
      this.allowAutoFailBack = allowAutoFailBack;
   }

   public void setIsWaitForActivation(boolean isWaitForActivation) {
      this.isWaitForActivation = isWaitForActivation;
   }

   @Override
   public Activation createActivation(ActiveMQServerImpl server,
                                      boolean wasPrimary,
                                      Map<String, Object> activationParams,
                                      IOCriticalErrorListener ioCriticalErrorListener) {
      return new SharedStoreBackupActivation(server, this, ioCriticalErrorListener);
   }

   @Override
   public String getBackupGroupName() {
      return null;
   }
}

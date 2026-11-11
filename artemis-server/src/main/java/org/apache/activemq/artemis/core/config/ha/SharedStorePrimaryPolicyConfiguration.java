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
package org.apache.activemq.artemis.core.config.ha;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.core.config.HAPolicyConfiguration;

public class SharedStorePrimaryPolicyConfiguration implements HAPolicyConfiguration {

   private boolean failoverOnServerShutdown = ActiveMQDefaultConfiguration.isDefaultFailoverOnServerShutdown();
   private boolean waitForActivation = ActiveMQDefaultConfiguration.isDefaultWaitForActivation();

   public SharedStorePrimaryPolicyConfiguration() {
   }

   @Override
   public TYPE getType() {
      return TYPE.SHARED_STORE_PRIMARY;
   }

   @Deprecated
   public long getFailbackDelay() {
      return -1;
   }

   @Deprecated
   public SharedStorePrimaryPolicyConfiguration setFailbackDelay(long failbackDelay) {
      return this;
   }

   public boolean isFailoverOnServerShutdown() {
      return failoverOnServerShutdown;
   }

   public SharedStorePrimaryPolicyConfiguration setFailoverOnServerShutdown(boolean failoverOnServerShutdown) {
      this.failoverOnServerShutdown = failoverOnServerShutdown;
      return this;
   }

   public boolean isWaitForActivation() {
      return waitForActivation;
   }

   public SharedStorePrimaryPolicyConfiguration setWaitForActivation(boolean waitForActivation) {
      this.waitForActivation = waitForActivation;
      return this;
   }
}

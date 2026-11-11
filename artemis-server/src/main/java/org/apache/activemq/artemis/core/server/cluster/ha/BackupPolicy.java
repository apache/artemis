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

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.core.server.impl.Activation;

public abstract class BackupPolicy implements HAPolicy<Activation> {

   protected ScaleDownPolicy scaleDownPolicy;
   protected boolean restartBackup = ActiveMQDefaultConfiguration.isDefaultRestartBackup();

   public ScaleDownPolicy getScaleDownPolicy() {
      return scaleDownPolicy;
   }

   public void setScaleDownPolicy(ScaleDownPolicy scaleDownPolicy) {
      this.scaleDownPolicy = scaleDownPolicy;
   }

   @Override
   public boolean isBackup() {
      return true;
   }

   @Override
   public String getScaleDownGroupName() {
      return getScaleDownPolicy() != null ? getScaleDownPolicy().getGroupName() : null;
   }

   public boolean isRestartBackup() {
      return restartBackup;
   }

   public void setRestartBackup(boolean restartBackup) {
      this.restartBackup = restartBackup;
   }
}

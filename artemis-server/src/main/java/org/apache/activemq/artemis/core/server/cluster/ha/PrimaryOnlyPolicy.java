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

import org.apache.activemq.artemis.core.io.IOCriticalErrorListener;
import org.apache.activemq.artemis.core.server.impl.Activation;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.PrimaryOnlyActivation;

public class PrimaryOnlyPolicy implements HAPolicy<Activation> {

   private ScaleDownPolicy scaleDownPolicy;

   public PrimaryOnlyPolicy() {
   }

   public PrimaryOnlyPolicy(ScaleDownPolicy scaleDownPolicy) {
      this.scaleDownPolicy = scaleDownPolicy;
   }

   @Override
   public Activation createActivation(ActiveMQServerImpl server,
                                      boolean wasPrimary,
                                      Map<String, Object> activationParams,
                                      IOCriticalErrorListener ioCriticalErrorListener) {
      return new PrimaryOnlyActivation(server, this);
   }

   @Override
   public String getBackupGroupName() {
      return null;
   }

   @Override
   public String getScaleDownGroupName() {
      return scaleDownPolicy == null ? null : scaleDownPolicy.getGroupName();
   }

   @Override
   public String getScaleDownClustername() {
      return null;
   }

   @Override
   public int getScaleDownCommitInterval() {
      return scaleDownPolicy == null ? -1 : scaleDownPolicy.getCommitInterval();
   }

   @Override
   public boolean isSharedStore() {
      return false;
   }

   @Override
   public boolean isBackup() {
      return false;
   }

   @Override
   public boolean canScaleDown() {
      return scaleDownPolicy != null;
   }

   public ScaleDownPolicy getScaleDownPolicy() {
      return scaleDownPolicy;
   }

   public void setScaleDownPolicy(ScaleDownPolicy scaleDownPolicy) {
      this.scaleDownPolicy = scaleDownPolicy;
   }
}

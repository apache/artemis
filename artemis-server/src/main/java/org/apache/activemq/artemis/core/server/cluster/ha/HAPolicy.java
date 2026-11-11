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

/**
 * Every active server will have an HAPolicy that configures the type of server that it should be either primary, backup
 * or colocated (both). It also configures how, if colocated, it should react to sending and receiving requests for
 * backups.
 */
public interface HAPolicy<T extends Activation> {

   /**
    * created the Activation associated with this policy.
    */
   T createActivation(ActiveMQServerImpl server,
                      boolean wasPrimary,
                      Map<String, Object> activationParams,
                      IOCriticalErrorListener shutdownOnCriticalIO) throws Exception;

   boolean isSharedStore();

   boolean isBackup();

   default boolean isWaitForActivation() {
      return ActiveMQDefaultConfiguration.isDefaultWaitForActivation();
   }

   boolean canScaleDown();

   // todo These 3 methods could probably be moved as they are specific to the activation however they are needed for certain packets.

   String getBackupGroupName();

   default String getScaleDownGroupName() {
      return null;
   }

   default String getScaleDownClustername() {
      return null;
   }

   default int getScaleDownCommitInterval() {
      return -1;
   }

   default boolean useQuorumManager() {
      return true;
   }

}

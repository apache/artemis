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
package org.apache.activemq.artemis.core.server.routing.pools;

import org.apache.activemq.artemis.core.server.ActiveMQComponent;
import org.apache.activemq.artemis.core.server.routing.targets.Target;
import org.apache.activemq.artemis.core.server.routing.targets.TargetProbe;

import java.util.List;

public interface Pool extends ActiveMQComponent {
   String getUsername();

   void setUsername(String username);

   String getPassword();

   void setPassword(String password);

   int getQuorumSize();

   void setQuorumSize(int quorumSize);

   int getQuorumTimeout();

   void setQuorumTimeout(int quorumTimeout);

   int getCheckPeriod();



   Target getTarget(String nodeId);

   Target getReadyTarget(String nodeId);

   boolean isTargetReady(Target target);

   List<Target> getTargets();

   List<Target> getAllTargets();

   boolean addTarget(Target target);

   boolean removeTarget(Target target);


   List<TargetProbe> getTargetProbes();

   void addTargetProbe(TargetProbe probe);

   void removeTargetProbe(TargetProbe probe);
}

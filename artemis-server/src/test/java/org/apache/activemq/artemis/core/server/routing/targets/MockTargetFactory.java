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
package org.apache.activemq.artemis.core.server.routing.targets;

import org.apache.activemq.artemis.api.core.TransportConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MockTargetFactory extends AbstractTargetFactory {

   private final List<MockTarget> createdTargets = new ArrayList<>();

   private Boolean connectable = null;

   private Boolean ready = null;

   private Map<String, Object> attributeValues = null;

   private Map<String, Object> operationReturnValues = null;

   public Boolean getConnectable() {
      return connectable;
   }

   public MockTargetFactory setConnectable(Boolean connectable) {
      this.connectable = connectable;
      return this;
   }

   public Boolean getReady() {
      return ready;
   }

   public MockTargetFactory setReady(Boolean ready) {
      this.ready = ready;
      return this;
   }

   public Map<String, Object> getAttributeValues() {
      return attributeValues;
   }

   public MockTargetFactory setAttributeValues(Map<String, Object> attributeValues) {
      this.attributeValues = attributeValues;
      return this;
   }

   public Map<String, Object> getOperationReturnValues() {
      return operationReturnValues;
   }

   public MockTargetFactory setOperationReturnValues(Map<String, Object> operationReturnValues) {
      this.operationReturnValues = operationReturnValues;
      return this;
   }

   public List<MockTarget> getCreatedTargets() {
      return createdTargets;
   }

   @Override
   public Target createTarget(TransportConfiguration connector, String nodeID) {
      MockTarget target = new MockTarget(connector, nodeID);

      target.setUsername(getUsername());
      target.setPassword(getPassword());

      createdTargets.add(target);

      if (connectable != null) {
         target.setConnectable(connectable);
      }

      if (ready != null) {
         target.setReady(ready);
      }

      if (attributeValues != null) {
         target.setAttributeValues(attributeValues);
      }

      if (operationReturnValues != null) {
         target.setOperationReturnValues(operationReturnValues);
      }

      return target;
   }
}

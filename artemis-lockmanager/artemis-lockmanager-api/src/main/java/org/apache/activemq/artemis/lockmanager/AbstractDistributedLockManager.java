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

package org.apache.activemq.artemis.lockmanager;

import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.joining;

public abstract class AbstractDistributedLockManager implements DistributedLockManager {

   public AbstractDistributedLockManager() {
   }

   public AbstractDistributedLockManager(Map<String, String> properties) {
      validateParameters(properties);
   }

   protected String commaOnParameters() {
      return getValidParams().stream().collect(joining(","));
   }


   protected void validateParameters(Map<String, String> config) {
      config.forEach((parameterName, ignore) -> validateParameter(parameterName));
   }

   protected abstract Set<String> getValidParams();

   protected void validateParameter(String parameterName) {
      if (!getValidParams().contains(parameterName)) {
         throw new IllegalArgumentException("non existent parameter " + parameterName + ": accepted list is " + commaOnParameters());
      }
   }



}

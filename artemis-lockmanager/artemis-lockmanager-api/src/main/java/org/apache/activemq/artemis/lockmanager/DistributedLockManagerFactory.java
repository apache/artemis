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

public interface DistributedLockManagerFactory {
   DistributedLockManager build(Map<String, String> properties);

   String getName();

   String getImplName();

   default Map<String, String> validateParameters(Map<String, String> config) {
      config.forEach((parameterName, ignore) -> validateParameter(parameterName));
      return config;
   }

   default String getParameterListAsString() {
      return String.join(", ", getValidParametersList());
   }

   Set<String> getValidParametersList();

   default void validateParameter(String parameterName) {
      Set<String> validList = getValidParametersList();
      if (!validList.contains(parameterName)) {
         throw new IllegalArgumentException("Invalid parameter '" + parameterName + "'. Accepted parameters: " + String.join(", ", validList));
      }
   }
}

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

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class DistributedLockManagerConfiguration implements Serializable {

   private String className;
   private final Map<String, String> properties;

   public DistributedLockManagerConfiguration() {
      properties = new HashMap<>();
   }

   public DistributedLockManagerConfiguration(String className, Map<String, String> properties) {
      this.className = className;
      this.properties = properties;
   }

   public String getClassName() {
      return className;
   }

   public DistributedLockManagerConfiguration setClassName(String className) {
      this.className = className;
      return this;
   }


   public Map<String, String> getProperties() {
      return properties;
   }

}

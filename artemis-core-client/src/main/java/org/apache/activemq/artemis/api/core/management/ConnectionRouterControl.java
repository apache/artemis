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
package org.apache.activemq.artemis.api.core.management;

import javax.management.MBeanOperationInfo;
import javax.management.openmbean.CompositeData;

/**
 * A ConnectionRouterControl is used to manage a ConnectionRouter
 */
public interface ConnectionRouterControl {
   @Operation(desc = "Get the target associated with key", impact = MBeanOperationInfo.INFO)
   CompositeData getTarget(@Parameter(desc = "a key", name = "key") String key) throws Exception;

   @Operation(desc = "Get the target associated with key as JSON", impact = MBeanOperationInfo.INFO)
   String getTargetAsJSON(@Parameter(desc = "a key", name = "key") String key);

   @Operation(desc = "Set the local target filter regular expression", impact = MBeanOperationInfo.ACTION)
   void setLocalTargetFilter(@Parameter(desc = "the regular expression", name = "regExp") String regExp);

   @Operation(desc = "Get the local target filter regular expression", impact = MBeanOperationInfo.INFO)
   String getLocalTargetFilter();

   @Operation(desc = "Set the target key filter regular expression", impact = MBeanOperationInfo.ACTION)
   void setTargetKeyFilter(@Parameter(desc = "the regular expression", name = "regExp") String regExp);

   @Operation(desc = "Get the target key filter regular expression", impact = MBeanOperationInfo.INFO)
   String getTargetKeyFilter();
}

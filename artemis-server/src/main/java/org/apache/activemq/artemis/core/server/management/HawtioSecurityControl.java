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
package org.apache.activemq.artemis.core.server.management;

import org.apache.activemq.artemis.api.core.management.Operation;

import javax.management.MBeanOperationInfo;
import javax.management.openmbean.TabularData;
import java.util.List;
import java.util.Map;

public interface HawtioSecurityControl {

   @Operation(desc = "Can invoke an Object", impact = MBeanOperationInfo.ACTION)
   boolean canInvoke(String objectName) throws Exception;

   @Operation(desc = "Can invoke an Object", impact = MBeanOperationInfo.ACTION)
   boolean canInvoke(String objectName, String methodName) throws Exception;

   @Operation(desc = "Can invoke an Object", impact = MBeanOperationInfo.ACTION)
   boolean canInvoke(String objectName, String methodName, String[] argumentTypes) throws Exception;

   @Operation(desc = "Can invoke a number of Objects", impact = MBeanOperationInfo.ACTION)
   TabularData canInvoke(Map<String, List<String>> bulkQuery) throws Exception;
}

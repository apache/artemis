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

package org.apache.activemq.artemis.service.extensions;

import org.apache.activemq.artemis.logs.annotation.LogBundle;
import org.apache.activemq.artemis.logs.annotation.LogMessage;
import org.apache.activemq.artemis.logs.BundleFactory;

/**
 * Logger Codes 350000 - 359999
 */
@LogBundle(projectCode = "AMQ", regexID = "35[0-9]{4}")
public interface ActiveMQServiceExtensionLogger {

   ActiveMQServiceExtensionLogger LOGGER = BundleFactory.newBundle(ActiveMQServiceExtensionLogger.class, ActiveMQServiceExtensionLogger.class.getPackage().getName());

   @LogMessage(id = 352000, value = "Attempted to locate a Transaction Manager but none found.", level = LogMessage.Level.WARN)
   void transactionManagerNotFound();
}

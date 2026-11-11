/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.artemis.client.cdi.logger;

import javax.enterprise.inject.spi.ProcessBean;

import org.apache.activemq.artemis.logs.annotation.LogBundle;
import org.apache.activemq.artemis.logs.annotation.LogMessage;
import org.apache.activemq.artemis.logs.BundleFactory;

/**
 * Logger Codes 570000 - 579999
 */
@LogBundle(projectCode = "AMQ", regexID = "57[0-9]{4}")
public interface ActiveMQCDILogger {

   ActiveMQCDILogger LOGGER = BundleFactory.newBundle(ActiveMQCDILogger.class, ActiveMQCDILogger.class.getPackage().getName());

   @LogMessage(id = 571000, value = "Discovered configuration class {}", level = LogMessage.Level.INFO)
   void discoveredConfiguration(ProcessBean<?> pb);

   @LogMessage(id = 571001, value = "Discovered client configuration class {}", level = LogMessage.Level.INFO)
   void discoveredClientConfiguration(ProcessBean<?> pb);

   @LogMessage(id = 573000, value = "Configuration found, not using built in configuration", level = LogMessage.Level.DEBUG)
   void notUsingDefaultConfiguration();

   @LogMessage(id = 573001, value = "Configuration found, not using built in configuration", level = LogMessage.Level.DEBUG)
   void notUsingDefaultClientConfiguration();
}

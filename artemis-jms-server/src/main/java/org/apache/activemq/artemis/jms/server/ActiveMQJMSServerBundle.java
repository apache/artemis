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
package org.apache.activemq.artemis.jms.server;

import org.apache.activemq.artemis.api.core.ActiveMQAddressExistsException;
import org.apache.activemq.artemis.api.core.ActiveMQIllegalStateException;
import org.apache.activemq.artemis.api.core.ActiveMQInternalErrorException;
import org.apache.activemq.artemis.logs.annotation.LogBundle;
import org.apache.activemq.artemis.logs.annotation.Message;
import org.apache.activemq.artemis.logs.BundleFactory;

/**
 * Logger Codes 129000 - 129999
 */
@LogBundle(projectCode = "AMQ", regexID = "129[0-9]{3}", retiredIDs = {129001, 129002, 129008, 129009, 129010, 129011, 129012, 129013, 129014, 129015})
public interface ActiveMQJMSServerBundle {

   ActiveMQJMSServerBundle BUNDLE = BundleFactory.newBundle(ActiveMQJMSServerBundle.class);

   @Message(id = 129000, value = "Connection Factory {} does not exist")
   ActiveMQInternalErrorException cfDoesntExist(String name);

   @Message(id = 129003, value = "Discovery Group '{}' does not exist on main config")
   ActiveMQIllegalStateException discoveryGroupDoesntExist(String name);

   @Message(id = 129004, value = "No Connector name configured on create ConnectionFactory")
   ActiveMQIllegalStateException noConnectorNameOnCF();

   @Message(id = 129005, value = "Connector '{}' not found on the main configuration file")
   ActiveMQIllegalStateException noConnectorNameConfiguredOnCF(String name);

   @Message(id = 129006, value = "Binding {} is already being used by another connection factory")
   ActiveMQAddressExistsException cfBindingsExists(String name);

   @Message(id = 129007, value = "Error decoding password using codec instance")
   ActiveMQIllegalStateException errorDecodingPassword(Exception e);
}

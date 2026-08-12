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
package org.apache.activemq.artemis.core.protocol.mqtt;

import org.apache.activemq.artemis.logs.BundleFactory;
import org.apache.activemq.artemis.logs.annotation.LogBundle;
import org.apache.activemq.artemis.logs.annotation.Message;

/**
 * Logger Code 850000 - 859999
 */
@LogBundle(projectCode = "AMQ", regexID = "85[0-9]{4}")
public interface MQTTBundle {

   MQTTBundle BUNDLE = BundleFactory.newBundle(MQTTBundle.class);

   @Message(id = 850000, value = "Unable to store MQTT state within given timeout: {}ms")
   IllegalStateException unableToStoreMqttState(long timeout);

   @Message(id = 850001, value = "Unable to generate MQTT packet ID. All valid values between 1 and 65535 are in use. IDs will become available as messages are acknowledged by the client that has received them.")
   IllegalStateException unableToGenerateID();

   @Message(id = 850002, value = "StorageManager is null")
   IllegalStateException storageManagerIsNull();
}

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
package org.apache.activemq.artemis.logs;

import org.apache.activemq.artemis.api.core.ActiveMQIllegalStateException;
import org.apache.activemq.artemis.logs.annotation.LogBundle;
import org.apache.activemq.artemis.logs.annotation.Message;

/**
 * Logger Codes 209000 - 209999
 */
@LogBundle(projectCode = "AMQ", regexID = "209[0-9]{3}")
public interface ActiveMQUtilBundle {

   ActiveMQUtilBundle BUNDLE = BundleFactory.newBundle(ActiveMQUtilBundle.class);

   @Message(id = 209000, value = "invalid property: {}")
   ActiveMQIllegalStateException invalidProperty(String part);

   @Message(id = 209001, value = "Invalid type: {}")
   IllegalStateException invalidType(Byte type);

   @Message(id = 209002, value = "the specified string is too long ({})")
   IllegalStateException stringTooLong(Integer length);

   @Message(id = 209003, value = "Error instantiating codec {}")
   IllegalArgumentException errorCreatingCodec(String codecClassName, Exception e);

   @Message(id = 209004, value = "Failed to parse long value from {}")
   IllegalArgumentException failedToParseLong(String value);
}

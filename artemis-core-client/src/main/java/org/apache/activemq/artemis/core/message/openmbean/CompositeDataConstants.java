/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.message.openmbean;

public interface CompositeDataConstants {

   String ADDRESS = "address";
   String MESSAGE_ID = "messageID";
   String PROTOCOL = "protocol";
   String USER_ID = "userID";
   String TYPE = "type";
   String DURABLE = "durable";
   String EXPIRATION = "expiration";
   String PRIORITY = "priority";
   String REDELIVERED = "redelivered";
   String TIMESTAMP = "timestamp";
   String BODY = "BodyPreview";
   String TEXT_BODY = "text";
   String LARGE_MESSAGE = "largeMessage";
   String PERSISTENT_SIZE = "persistentSize";
   String PROPERTIES = "PropertiesText";

   String ADDRESS_DESCRIPTION = "The Address";
   String MESSAGE_ID_DESCRIPTION = "The message ID";
   String PROTOCOL_DESCRIPTION = "The message protocol";
   String USER_ID_DESCRIPTION = "The user ID";
   String TYPE_DESCRIPTION = "The message type";
   String DURABLE_DESCRIPTION = "Is the message durable";
   String EXPIRATION_DESCRIPTION = "The message expiration";
   String PRIORITY_DESCRIPTION = "The message priority";
   String REDELIVERED_DESCRIPTION = "Has the message been redelivered";
   String TIMESTAMP_DESCRIPTION = "The message timestamp";
   String BODY_DESCRIPTION = "The message body";
   String LARGE_MESSAGE_DESCRIPTION = "Is the message treated as a large message";
   String PERSISTENT_SIZE_DESCRIPTION = "The message size when persisted on disk";
   String PROPERTIES_DESCRIPTION = "The properties text";

   // User properties
   String STRING_PROPERTIES = "StringProperties";
   String BOOLEAN_PROPERTIES = "BooleanProperties";
   String BYTE_PROPERTIES = "ByteProperties";
   String SHORT_PROPERTIES = "ShortProperties";
   String INT_PROPERTIES = "IntProperties";
   String LONG_PROPERTIES = "LongProperties";
   String FLOAT_PROPERTIES = "FloatProperties";
   String DOUBLE_PROPERTIES = "DoubleProperties";

   String STRING_PROPERTIES_DESCRIPTION = "User String Properties";
   String BOOLEAN_PROPERTIES_DESCRIPTION = "User Boolean Properties";
   String BYTE_PROPERTIES_DESCRIPTION = "User Byte Properties";
   String SHORT_PROPERTIES_DESCRIPTION = "User Short Properties";
   String INT_PROPERTIES_DESCRIPTION = "User Int Properties";
   String LONG_PROPERTIES_DESCRIPTION = "User Long Properties";
   String FLOAT_PROPERTIES_DESCRIPTION = "User Float Properties";
   String DOUBLE_PROPERTIES_DESCRIPTION = "User Double Properties";

}

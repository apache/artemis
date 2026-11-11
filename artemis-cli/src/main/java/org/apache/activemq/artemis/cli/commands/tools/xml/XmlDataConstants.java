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
package org.apache.activemq.artemis.cli.commands.tools.xml;

/**
 * The constants shared by {@code org.apache.activemq.tools.XmlDataImporter} and
 * {@code org.apache.activemq.tools.XmlDataExporter}.
 */
public final class XmlDataConstants {

   private XmlDataConstants() {
      // Utility
   }

   public static final String XML_VERSION = "1.0";
   public static final String DOCUMENT_PARENT = "activemq-journal";
   public static final String BINDINGS_PARENT = "bindings";

   // used on importing data from 1.x
   static final String OLD_BINDING = "binding";
   static final String OLD_ADDRESS = "address";
   static final String OLD_FILTER = "filter-string";
   static final String OLD_QUEUE = "queue-name";



   public static final String QUEUE_BINDINGS_CHILD = "queue-binding";
   static final String QUEUE_BINDING_ADDRESS = "address";
   static final String QUEUE_BINDING_FILTER_STRING = "filter-string";
   static final String QUEUE_BINDING_NAME = "name";
   static final String QUEUE_BINDING_ID = "id";
   static final String QUEUE_BINDING_ROUTING_TYPE = "routing-type";

   public static final String ADDRESS_BINDINGS_CHILD = "address-binding";
   static final String ADDRESS_BINDING_NAME = "name";
   static final String ADDRESS_BINDING_ID = "id";
   static final String ADDRESS_BINDING_ROUTING_TYPE = "routing-types";

   public static final String DIVERT_BINDINGS_CHILD = "divert-binding";
   static final String DIVERT_BINDING_NAME = "name";
   static final String DIVERT_BINDING_ROUTING_NAME = "routing-name";
   static final String DIVERT_BINDING_ADDRESS = "address";
   static final String DIVERT_BINDING_FORWARDING_ADDRESS = "forwarding-address";
   static final String DIVERT_BINDING_EXCLUSIVE = "exclusive";
   static final String DIVERT_BINDING_FILTER_STRING = "filter-string";
   static final String DIVERT_BINDING_ROUTING_TYPE = "routing-type";
   static final String DIVERT_BINDING_TRANSFORMER_CLASS_NAME = "transformer-class-name";

   public static final String MESSAGES_PARENT = "messages";
   public static final String MESSAGES_CHILD = "message";
   static final String MESSAGE_ID = "id";
   static final String MESSAGE_PRIORITY = "priority";
   static final String MESSAGE_EXPIRATION = "expiration";
   static final String MESSAGE_TIMESTAMP = "timestamp";
   static final String DEFAULT_TYPE_PRETTY = "default";
   static final String BYTES_TYPE_PRETTY = "bytes";
   static final String MAP_TYPE_PRETTY = "map";
   static final String OBJECT_TYPE_PRETTY = "object";
   static final String STREAM_TYPE_PRETTY = "stream";
   public static final String TEXT_TYPE_PRETTY = "text";
   static final String MESSAGE_TYPE = "type";
   static final String MESSAGE_IS_LARGE = "isLarge";
   static final String MESSAGE_USER_ID = "user-id";
   static final String MESSAGE_BODY = "body";
   static final String PROPERTIES_PARENT = "properties";
   static final String PROPERTIES_CHILD = "property";
   static final String PROPERTY_NAME = "name";
   static final String PROPERTY_VALUE = "value";
   static final String PROPERTY_TYPE = "type";
   public static final String QUEUES_PARENT = "queues";
   public static final String QUEUES_CHILD = "queue";
   public static final String QUEUE_NAME = "name";
   static final String PROPERTY_TYPE_BOOLEAN = "boolean";
   static final String PROPERTY_TYPE_BYTE = "byte";
   static final String PROPERTY_TYPE_BYTES = "bytes";
   static final String PROPERTY_TYPE_SHORT = "short";
   static final String PROPERTY_TYPE_INTEGER = "integer";
   static final String PROPERTY_TYPE_LONG = "long";
   static final String PROPERTY_TYPE_FLOAT = "float";
   static final String PROPERTY_TYPE_DOUBLE = "double";
   static final String PROPERTY_TYPE_STRING = "string";
   static final String PROPERTY_TYPE_SIMPLE_STRING = "simple-string";

   static final String NULL = "_AMQ_NULL";
}
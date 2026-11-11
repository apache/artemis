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

import java.util.Objects;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.utils.Base64;

/**
 * Common utility methods to help with XML message conversion
 */
public class XmlDataExporterUtil {

   public static String convertProperty(final Object value) {
      if (value instanceof byte[] bytes) {
         return encode(bytes);
      } else {
         return value == null ? XmlDataConstants.NULL : value.toString();
      }
   }

   public static String getPropertyType(final Object value) {
      String stringValue = null;

      // if the value is null then we can't really know what it is so just set
      // the type to the most generic thing
      if (value == null) {
         stringValue = XmlDataConstants.PROPERTY_TYPE_BYTES;
      } else if (value instanceof Boolean) {
         stringValue = XmlDataConstants.PROPERTY_TYPE_BOOLEAN;
      } else if (value instanceof Byte) {
         stringValue = XmlDataConstants.PROPERTY_TYPE_BYTE;
      } else if (value instanceof Short) {
         stringValue = XmlDataConstants.PROPERTY_TYPE_SHORT;
      } else if (value instanceof Integer) {
         stringValue = XmlDataConstants.PROPERTY_TYPE_INTEGER;
      } else if (value instanceof Long) {
         stringValue = XmlDataConstants.PROPERTY_TYPE_LONG;
      } else if (value instanceof Float) {
         stringValue = XmlDataConstants.PROPERTY_TYPE_FLOAT;
      } else if (value instanceof Double) {
         stringValue = XmlDataConstants.PROPERTY_TYPE_DOUBLE;
      } else if (value instanceof String) {
         stringValue = XmlDataConstants.PROPERTY_TYPE_STRING;
      } else if (value instanceof SimpleString) {
         stringValue = XmlDataConstants.PROPERTY_TYPE_SIMPLE_STRING;
      } else if (value instanceof byte[]) {
         stringValue = XmlDataConstants.PROPERTY_TYPE_BYTES;
      }

      return stringValue;
   }

   public static String getMessagePrettyType(byte rawType) {
      String prettyType = XmlDataConstants.DEFAULT_TYPE_PRETTY;

      if (rawType == Message.BYTES_TYPE) {
         prettyType = XmlDataConstants.BYTES_TYPE_PRETTY;
      } else if (rawType == Message.MAP_TYPE) {
         prettyType = XmlDataConstants.MAP_TYPE_PRETTY;
      } else if (rawType == Message.OBJECT_TYPE) {
         prettyType = XmlDataConstants.OBJECT_TYPE_PRETTY;
      } else if (rawType == Message.STREAM_TYPE) {
         prettyType = XmlDataConstants.STREAM_TYPE_PRETTY;
      } else if (rawType == Message.TEXT_TYPE) {
         prettyType = XmlDataConstants.TEXT_TYPE_PRETTY;
      }

      return prettyType;
   }

   /**
    * Base64 encode a ServerMessage body into the proper XML format
    */
   public static String encodeMessageBodyBase64(final Message message) throws Exception {
      Objects.requireNonNull(message, "ServerMessage can not be null");

      ActiveMQBuffer byteBuffer = message.toCore().getReadOnlyBodyBuffer();
      byte[] buffer = new byte[byteBuffer.writerIndex()];
      byteBuffer.readBytes(buffer);

      return XmlDataExporterUtil.encode(buffer);
   }

   protected static String encode(final byte[] data) {
      return Base64.encodeBytes(data, true);
   }
}

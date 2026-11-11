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
package org.apache.activemq.artemis.selector;

import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.selector.filter.FilterException;
import org.apache.activemq.artemis.selector.filter.Filterable;

public class MockMessage implements Filterable {

   Map<String, Object> properties = new HashMap<>();
   private String text;
   private Object destination;
   private String messageId;
   private String type;
   private Object localConnectionId;

   public void setDestination(Object destination) {
      this.destination = destination;
   }

   public void setJMSMessageID(String messageId) {
      this.messageId = messageId;
   }

   public void setJMSType(String type) {
      this.type = type;
   }

   public void setText(String text) {
      this.text = text;
   }

   public void setBooleanProperty(String key, boolean value) {
      properties.put(key, value);
   }

   public void setStringProperty(String key, String value) {
      properties.put(key, value);
   }

   public void setByteProperty(String key, byte value) {
      properties.put(key, value);
   }

   public void setDoubleProperty(String key, double value) {
      properties.put(key, value);
   }

   public void setFloatProperty(String key, float value) {
      properties.put(key, value);
   }

   public void setLongProperty(String key, long value) {
      properties.put(key, value);
   }

   public void setIntProperty(String key, int value) {
      properties.put(key, value);
   }

   public void setShortProperty(String key, short value) {
      properties.put(key, value);
   }

   public void setObjectProperty(String key, Object value) {
      properties.put(key, value);
   }

   @Override
   public <T> T getBodyAs(Class<T> type) throws FilterException {
      if (type == String.class) {
         return type.cast(text);
      }
      return null;
   }

   @Override
   public Object getProperty(SimpleString name) {
      String stringName = name.toString();
      if ("JMSType".equals(stringName)) {
         return type;
      }
      if ("JMSMessageID".equals(stringName)) {
         return messageId;
      }
      return properties.get(stringName);
   }

   public Object getDestination() {
      return destination;
   }

   @Override
   public Object getLocalConnectionId() {
      return localConnectionId;
   }

}
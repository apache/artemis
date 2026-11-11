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
package org.apache.activemq.artemis.core.management.impl.view;

import java.util.Map;
import java.util.TreeMap;

public enum ProducerField {
   ID("id"),
   NAME("name"),
   SESSION("session", "sessionID"),
   CONNECTION_ID("connectionID"),
   ADDRESS("address", "destination"),
   USER("user"),
   VALIDATED_USER("validatedUser"),
   PROTOCOL("protocol"),
   CLIENT_ID("clientID"),
   LOCAL_ADDRESS("localAddress"),
   REMOTE_ADDRESS("remoteAddress"),
   CREATION_TIME("creationTime"),
   MESSAGE_SENT("msgSent"),
   MESSAGE_SENT_SIZE("msgSizeSent"),
   LAST_PRODUCED_MESSAGE_ID("lastProducedMessageID");

   private static final Map<String, ProducerField> lookup = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

   static {
      for (ProducerField e: values()) {
         lookup.put(e.name, e);
      }
   }

   private final String name;

   private final String alternativeName;

   public String getName() {
      return name;
   }

   /**
    * There is some inconsistency with some json objects returned for consumers because they were hard coded. This is
    * just to track the differences and provide backward compatibility.
    *
    * @return the old alternative name
    */
   public String getAlternativeName() {
      return alternativeName;
   }

   ProducerField(String name) {
      this(name, "");
   }
   ProducerField(String name, String alternativeName) {
      this.name = name;
      this.alternativeName = alternativeName;
   }

   public static ProducerField valueOfName(String name) {
      return lookup.get(name);
   }
}

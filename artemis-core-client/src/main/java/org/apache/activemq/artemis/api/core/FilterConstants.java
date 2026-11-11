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
package org.apache.activemq.artemis.api.core;

/**
 * Constants representing pre-defined message attributes that can be referenced in Apache Artemis core filter
 * expressions.
 */
public final class FilterConstants {

   /**
    * Name of the Apache Artemis UserID header.
    */
   public static final SimpleString ACTIVEMQ_USERID = SimpleString.of("AMQUserID");

   /**
    * Name of the Apache Artemis Message expiration header.
    */
   public static final SimpleString ACTIVEMQ_EXPIRATION = SimpleString.of("AMQExpiration");

   /**
    * Name of the Apache Artemis Message durable header.
    */
   public static final SimpleString ACTIVEMQ_DURABLE = SimpleString.of("AMQDurable");

   /**
    * Value for the Durable header when the message is non-durable.
    */
   public static final SimpleString NON_DURABLE = SimpleString.of("NON_DURABLE");

   /**
    * Value for the Durable header when the message is durable.
    */
   public static final SimpleString DURABLE = SimpleString.of("DURABLE");

   /**
    * Name of the Apache Artemis Message timestamp header.
    */
   public static final SimpleString ACTIVEMQ_TIMESTAMP = SimpleString.of("AMQTimestamp");

   /**
    * Name of the Apache Artemis Message priority header.
    */
   public static final SimpleString ACTIVEMQ_PRIORITY = SimpleString.of("AMQPriority");

   /**
    * Name of the Apache Artemis Message size header.
    */
   public static final SimpleString ACTIVEMQ_SIZE = SimpleString.of("AMQSize");

   /**
    * Name of the Apache Artemis Address header
    */
   public static final SimpleString ACTIVEMQ_ADDRESS = SimpleString.of("AMQAddress");

   /**
    * Name of the Apache Artemis Message group id header.
    */
   public static final SimpleString ACTIVEMQ_GROUP_ID = SimpleString.of("AMQGroupID");

   /**
    * All Apache Artemis headers are prepended by this prefix.
    */
   public static final SimpleString ACTIVEMQ_PREFIX = SimpleString.of("AMQ");

   /**
    * Core to AMQP wrapper can store JMSMessageID as NATIVE_MESSAGE_ID internally
    */
   public static final String NATIVE_MESSAGE_ID = "NATIVE_MESSAGE_ID";

   private FilterConstants() {
      // Utility class
   }
}

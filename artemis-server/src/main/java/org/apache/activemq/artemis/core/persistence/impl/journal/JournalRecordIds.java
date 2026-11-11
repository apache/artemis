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
package org.apache.activemq.artemis.core.persistence.impl.journal;

/**
 * These record IDs definitions are meant to be public.
 * <p>
 * If any other component or any test needs to validate user-record-types from the Journal directly This is where the
 * definitions will exist and this is what these tests should be using to verify the IDs.
 */
public final class JournalRecordIds {

   // grouping journal record type

   public static final byte GROUP_RECORD = 20;

   // BindingsImpl journal record type

   public static final byte QUEUE_BINDING_RECORD = 21;

   public static final byte QUEUE_STATUS_RECORD = 22;

   /**
    * Records storing the current recordID number.
    *
    * @see org.apache.activemq.artemis.utils.IDGenerator
    * @see BatchingIDGenerator
    */
   public static final byte ID_COUNTER_RECORD = 24;

   public static final byte ADDRESS_SETTING_RECORD = 25;

   public static final byte SECURITY_SETTING_RECORD = 26;

   public static final byte DIVERT_RECORD = 27;

   public static final byte BRIDGE_RECORD = 28;

   // Message journal record types

   /**
    * THIS RECORD IS NO LONGER USED, WE NOW WILL SCAN ALL PAGE FILES FOR PENDING LARGE MESSAGES
    */
   public static final byte ADD_LARGE_MESSAGE_PENDING = 29;

   public static final byte ADD_LARGE_MESSAGE = 30;

   public static final byte ADD_MESSAGE = 31;

   public static final byte ADD_REF = 32;

   public static final byte ACKNOWLEDGE_REF = 33;

   public static final byte UPDATE_DELIVERY_COUNT = 34;

   public static final byte PAGE_TRANSACTION = 35;

   public static final byte SET_SCHEDULED_DELIVERY_TIME = 36;

   public static final byte DUPLICATE_ID = 37;

   public static final byte HEURISTIC_COMPLETION = 38;

   public static final byte ACKNOWLEDGE_CURSOR = 39;

   public static final byte PAGE_CURSOR_COUNTER_VALUE = 40;

   public static final byte PAGE_CURSOR_COUNTER_INC = 41;

   public static final byte PAGE_CURSOR_COMPLETE = 42;

   public static final byte PAGE_CURSOR_PENDING_COUNTER = 43;

   public static final byte ADDRESS_BINDING_RECORD = 44;

   public static final byte ADD_MESSAGE_PROTOCOL = 45;

   public static final byte ADDRESS_STATUS_RECORD = 46;

   public static final byte USER_RECORD = 47;

   public static final byte ROLE_RECORD = 48;

   // Used to record the large message body on the journal when history is on
   public static final byte ADD_MESSAGE_BODY = 49;

   public static final byte KEY_VALUE_PAIR_RECORD = 50;

   public static final byte CONNECTOR_RECORD = 51;

   public static final byte ADDRESS_SETTING_RECORD_JSON = 52;

   public static final byte ACK_RETRY = 53;

   public static final byte MQTT_PACKET_ID_CORRELATION = 54;

   public static String recordTypeName(byte recordType) {

      switch (recordType) {
         case JournalRecordIds.GROUP_RECORD: return "GROUP";
         case JournalRecordIds.QUEUE_BINDING_RECORD: return "QUEUE_BINDING";
         case JournalRecordIds.QUEUE_STATUS_RECORD: return "QUEUE_STATUS";
         case JournalRecordIds.ID_COUNTER_RECORD: return "ID_COUNTER";
         case JournalRecordIds.ADDRESS_SETTING_RECORD: return "ADDRESS_SETTING";
         case JournalRecordIds.SECURITY_SETTING_RECORD: return "SECURITY_SETTING";
         case JournalRecordIds.DIVERT_RECORD: return "DIVERT";
         case JournalRecordIds.BRIDGE_RECORD: return "BRIDGE";
         case JournalRecordIds.ADD_LARGE_MESSAGE_PENDING: return "ADD_LARGE_MESSAGE_PENDING";
         case JournalRecordIds.ADD_LARGE_MESSAGE: return "ADD_LARGE_MESSAGE";
         case JournalRecordIds.ADD_MESSAGE: return "ADD_MESSAGE";
         case JournalRecordIds.ADD_REF: return "ADD_REF";
         case JournalRecordIds.ACKNOWLEDGE_REF: return "ACKNOWLEDGE_REF";
         case JournalRecordIds.UPDATE_DELIVERY_COUNT: return "UPDATE_DELIVERY_COUNT";
         case JournalRecordIds.PAGE_TRANSACTION: return "PAGE_TRANSACTION";
         case JournalRecordIds.SET_SCHEDULED_DELIVERY_TIME: return "SET_SCHEDULED_DELIVERY_TIME";
         case JournalRecordIds.DUPLICATE_ID: return "DUPLICATE_ID";
         case JournalRecordIds.HEURISTIC_COMPLETION: return "HEURISTIC_COMPLETION";
         case JournalRecordIds.ACKNOWLEDGE_CURSOR: return "ACKNOWLEDGE_CURSOR";
         case JournalRecordIds.PAGE_CURSOR_COUNTER_VALUE: return "PAGE_CURSOR_COUNTER_VALUE";
         case JournalRecordIds.PAGE_CURSOR_COUNTER_INC: return "PAGE_CURSOR_COUNTER_INC";
         case JournalRecordIds.PAGE_CURSOR_COMPLETE: return "PAGE_CURSOR_COMPLETE";
         case JournalRecordIds.PAGE_CURSOR_PENDING_COUNTER: return "PAGE_CURSOR_PENDING_COUNTER";
         case JournalRecordIds.ADDRESS_BINDING_RECORD: return "ADDRESS_BINDING";
         case JournalRecordIds.ADD_MESSAGE_PROTOCOL: return "ADD_MESSAGE_PROTOCOL";
         case JournalRecordIds.ADDRESS_STATUS_RECORD: return "ADDRESS_STATUS";
         case JournalRecordIds.USER_RECORD: return "USER";
         case JournalRecordIds.ROLE_RECORD: return "ROLE";
         case JournalRecordIds.ADD_MESSAGE_BODY: return "ADD_MESSAGE_BODY";
         case JournalRecordIds.KEY_VALUE_PAIR_RECORD: return "KEY_VALUE_PAIR";
         case JournalRecordIds.CONNECTOR_RECORD: return "CONNECTOR";
         case JournalRecordIds.ADDRESS_SETTING_RECORD_JSON: return "ADDRESS_SETTING_JSON";
         case JournalRecordIds.ACK_RETRY: return "ACK_RETRY";
         case JournalRecordIds.MQTT_PACKET_ID_CORRELATION: return "MQTT_CORRELATION";
         default: return "UNKNOWN";
      }
   }

}

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
package org.apache.activemq.artemis.core.protocol.mqtt;

/**
 * Taken mainly from https://docs.oasis-open.org/mqtt/mqtt/v5.0/os/mqtt-v5.0-os.html#_Toc3901031
 */
public class MQTTReasonCodes {
   // codes specific to MQTT 3.x
   public static final byte UNACCEPTABLE_PROTOCOL_VERSION_3 = (byte) 0X01;
   public static final byte IDENTIFIER_REJECTED_3 = (byte) 0x02;
   public static final byte SERVER_UNAVAILABLE_3 = (byte) 0x03;
   public static final byte BAD_USER_NAME_OR_PASSWORD_3 = (byte) 0x04;
   public static final byte NOT_AUTHORIZED_3 = (byte) 0x05;

   // codes specific to MQTT 5
   public static final byte SUCCESS = (byte) 0x00;
   public static final byte NORMAL_DISCONNECTION = (byte) 0x00;
   public static final byte GRANTED_QOS_0 = (byte) 0x00;
   public static final byte GRANTED_QOS_1 = (byte) 0x01;
   public static final byte GRANTED_QOS_2 = (byte) 0x02;
   public static final byte DISCONNECT_WITH_WILL_MESSAGE = (byte) 0x04;
   public static final byte NO_MATCHING_SUBSCRIBERS = (byte) 0x10;
   public static final byte NO_SUBSCRIPTION_EXISTED = (byte) 0x11;
   public static final byte CONTINUE_AUTHENTICATION = (byte) 0x18;
   public static final byte RE_AUTHENTICATE = (byte) 0x19;
   public static final byte UNSPECIFIED_ERROR = (byte) 0x80;
   public static final byte MALFORMED_PACKET = (byte) 0x81;
   public static final byte PROTOCOL_ERROR = (byte) 0x82;
   public static final byte IMPLEMENTATION_SPECIFIC_ERROR = (byte) 0x83;
   public static final byte UNSUPPORTED_PROTOCOL_VERSION = (byte) 0x84;
   public static final byte CLIENT_IDENTIFIER_NOT_VALID = (byte) 0x85;
   public static final byte BAD_USER_NAME_OR_PASSWORD = (byte) 0x86;
   public static final byte NOT_AUTHORIZED = (byte) 0x87;
   public static final byte SERVER_UNAVAILABLE = (byte) 0x88;
   public static final byte SERVER_BUSY = (byte) 0x89;
   public static final byte BANNED = (byte) 0x8A;
   public static final byte SERVER_SHUTTING_DOWN = (byte) 0x8B;
   public static final byte BAD_AUTHENTICATION_METHOD = (byte) 0x8C;
   public static final byte KEEP_ALIVE_TIMEOUT = (byte) 0x8D;
   public static final byte SESSION_TAKEN_OVER = (byte) 0x8E;
   public static final byte TOPIC_FILTER_INVALID = (byte) 0x8F;
   public static final byte TOPIC_NAME_INVALID = (byte) 0x90;
   public static final byte PACKET_IDENTIFIER_IN_USE = (byte) 0x91;
   public static final byte PACKET_IDENTIFIER_NOT_FOUND = (byte) 0x92;
   public static final byte RECEIVE_MAXIMUM_EXCEEDED = (byte) 0x93;
   public static final byte TOPIC_ALIAS_INVALID = (byte) 0x94;
   public static final byte PACKET_TOO_LARGE = (byte) 0x95;
   public static final byte MESSAGE_RATE_TOO_HIGH = (byte) 0x96;
   public static final byte QUOTA_EXCEEDED = (byte) 0x97;
   public static final byte ADMINISTRATIVE_ACTION = (byte) 0x98;
   public static final byte PAYLOAD_FORMAT_INVALID = (byte) 0x99;
   public static final byte RETAIN_NOT_SUPPORTED = (byte) 0x9A;
   public static final byte QOS_NOT_SUPPORTED = (byte) 0x9B;
   public static final byte USE_ANOTHER_SERVER = (byte) 0x9C;
   public static final byte SERVER_MOVED = (byte) 0x9D;
   public static final byte SHARED_SUBSCRIPTIONS_NOT_SUPPORTED = (byte) 0x9E;
   public static final byte CONNECTION_RATE_EXCEEDED = (byte) 0x9F;
   public static final byte MAXIMUM_CONNECT_TIME = (byte) 0xA0;
   public static final byte SUBSCRIPTION_IDENTIFIERS_NOT_SUPPORTED = (byte) 0xA1;
   public static final byte WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED = (byte) 0xA2;
}

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
package org.apache.activemq.artemis.protocol.amqp.proton;

/**
 * Message constants used for handling the "tunneling" of other protocol messages in an AMQP delivery sent from one
 * broker to another without conversion.
 * <p>
 * A tunneled Core message is sent with a custom message format indicating either a standard or large core message is
 * carried within. The message is encoded using the standard (message format zero) AMQP message structure. The core
 * message is encoded in the body section as two or more Data sections. The first being the message headers and
 * properties encoding. Any remaining Data sections comprise the body of the Core message.
 */
public class AMQPTunneledMessageConstants {

   /*
    * Prefix value used on all custom message formats that is the ASF IANA number.
    *
    * https://www.iana.org/assignments/enterprise-numbers/enterprise-numbers
    */
   private static final int ARTEMIS_TUNNELED_MESSAGE_FORMAT_PREFIX = 0x468C0000;

   // Used to indicate that the format contains a Core message (non-large).
   private static final int ARTEMIS_CORE_MESSAGE_TYPE = 0x00000100;

   // Used to indicate that the format contains a Core large message.
   private static final int ARTEMIS_CORE_LARGE_MESSAGE_TYPE = 0x00000200;

   // Indicate version one of the message format
   private static final int ARTEMIS_MESSAGE_FORMAT_V1 = 0x00;

   /**
    * Core message format value used when sending from one broker to another
    */
   public static final int AMQP_TUNNELED_CORE_MESSAGE_FORMAT = ARTEMIS_TUNNELED_MESSAGE_FORMAT_PREFIX |
                                                               ARTEMIS_CORE_MESSAGE_TYPE |
                                                               ARTEMIS_MESSAGE_FORMAT_V1;

   /**
    * Core large message format value used when sending from one broker to another
    */
   public static final int AMQP_TUNNELED_CORE_LARGE_MESSAGE_FORMAT = ARTEMIS_TUNNELED_MESSAGE_FORMAT_PREFIX |
                                                                     ARTEMIS_CORE_LARGE_MESSAGE_TYPE |
                                                                     ARTEMIS_MESSAGE_FORMAT_V1;

}


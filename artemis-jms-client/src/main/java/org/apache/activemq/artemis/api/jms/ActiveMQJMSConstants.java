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
package org.apache.activemq.artemis.api.jms;

/**
 * Constants for Apache Artemis for property keys used for Apache Artemis specific extensions to JMS.
 */
public class ActiveMQJMSConstants {

   public static final String JMS_ACTIVEMQ_INPUT_STREAM = "JMS_AMQ_InputStream";

   public static final String JMS_ACTIVEMQ_OUTPUT_STREAM = "JMS_AMQ_OutputStream";

   public static final String JMS_ACTIVEMQ_SAVE_STREAM = "JMS_AMQ_SaveStream";

   public static final String AMQ_MESSAGING_BRIDGE_MESSAGE_ID_LIST = "AMQ_BRIDGE_MSG_ID_LIST";

   public static final int PRE_ACKNOWLEDGE = 100;

   public static final int INDIVIDUAL_ACKNOWLEDGE = 101;

   public static final String JMS_ACTIVEMQ_ENABLE_BYTE_ARRAY_JMS_CORRELATION_ID_PROPERTY_NAME = "amq.jms.support-bytes-id";
}

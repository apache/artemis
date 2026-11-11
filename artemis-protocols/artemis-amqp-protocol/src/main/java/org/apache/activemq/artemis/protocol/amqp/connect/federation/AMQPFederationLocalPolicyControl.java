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
package org.apache.activemq.artemis.protocol.amqp.connect.federation;

import org.apache.activemq.artemis.api.core.management.Attribute;

/**
 * Management service control interface for an AMQPFederation policy manager instance.
 */
public interface AMQPFederationLocalPolicyControl {

   /**
    * {@return the type of the AMQP federation policy manager being controlled}
    */
   @Attribute(desc = "AMQP federation policy manager type that backs this control instance.")
   String getType();

   /**
    * {@return the configured name the AMQP federation policy manager being controlled}
    */
   @Attribute(desc = "The configured AMQP federation policy name that backs this control instance.")
   String getName();

   /**
    * {@return the number of messages this federation policy has received from the remote}
    */
   @Attribute(desc = "returns the number of messages this federation policy has received from the remote")
   long getMessagesReceived();

}

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
package org.apache.activemq.artemis.protocol.amqp.proton;

import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPException;
import org.apache.qpid.proton.amqp.transport.ErrorCondition;
import org.apache.qpid.proton.engine.Delivery;

/**
 * An interface to handle deliveries, either messages, acks or transaction calls
 */
public interface ProtonDeliveryHandler {

   void onFlow(int currentCredits, boolean drain);

   void onMessage(Delivery delivery) throws ActiveMQAMQPException;

   /*
    * We have to distinguish between a remote close on the link and a close via a connection or session as the latter
    * mean that a link reattach can happen and we need to keep the underlying resource (queue/subscription) around for
    * pub subs
    */
   void close(boolean remoteLinkClose) throws ActiveMQAMQPException;

   void close(ErrorCondition condition) throws ActiveMQAMQPException;
}

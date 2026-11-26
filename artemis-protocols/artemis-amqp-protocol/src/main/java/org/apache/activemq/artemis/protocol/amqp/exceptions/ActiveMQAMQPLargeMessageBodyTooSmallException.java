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
package org.apache.activemq.artemis.protocol.amqp.exceptions;

import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.qpid.proton.amqp.transport.AmqpError;

/**
 * Thrown when the stored body of a large message is smaller than the body size recorded for that message, which means
 * the message can never be fully written to a sender no matter how many times the delivery is attempted.
 * <p>
 * This is deliberately distinct from the generic {@link ActiveMQAMQPInternalErrorException} used for transient IO
 * failures: a transient failure is worth retrying, whereas a body that is smaller than its recorded size is a
 * permanent, message specific fault, so the sender routes the message away instead of retrying it forever.
 */
public class ActiveMQAMQPLargeMessageBodyTooSmallException extends ActiveMQAMQPException {

   public ActiveMQAMQPLargeMessageBodyTooSmallException(String message) {
      super(AmqpError.INTERNAL_ERROR, message, ActiveMQExceptionType.LARGE_MESSAGE_ERROR_BODY);
   }
}

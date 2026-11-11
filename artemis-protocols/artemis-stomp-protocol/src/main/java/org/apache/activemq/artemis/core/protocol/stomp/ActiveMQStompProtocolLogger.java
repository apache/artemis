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
package org.apache.activemq.artemis.core.protocol.stomp;

import org.apache.activemq.artemis.api.core.ICoreMessage;
import org.apache.activemq.artemis.logs.annotation.LogBundle;
import org.apache.activemq.artemis.logs.annotation.LogMessage;
import org.apache.activemq.artemis.logs.BundleFactory;

/**
 * Logger Codes 330000 - 338999
 */
@LogBundle(projectCode = "AMQ", regexID = "33[0-8][0-9]{3}")
public interface ActiveMQStompProtocolLogger {

   ActiveMQStompProtocolLogger LOGGER = BundleFactory.newBundle(ActiveMQStompProtocolLogger.class, ActiveMQStompProtocolLogger.class.getPackage().getName());

   @LogMessage(id = 332068, value = "connection closed {}", level = LogMessage.Level.WARN)
   void connectionClosed(StompConnection connection);

   @LogMessage(id = 332069, value = "Sent ERROR frame to STOMP client {}: {}", level = LogMessage.Level.WARN)
   void sentErrorToClient(String address, String message);

   @LogMessage(id = 332070, value = "Unable to send frame {}", level = LogMessage.Level.ERROR)
   void errorSendingFrame(StompFrame frame, Exception e);

   @LogMessage(id = 332071, value = "Unable to send message to client: {}", level = LogMessage.Level.WARN)
   void unableToSendMessageToClient(ICoreMessage message, Exception e);
}

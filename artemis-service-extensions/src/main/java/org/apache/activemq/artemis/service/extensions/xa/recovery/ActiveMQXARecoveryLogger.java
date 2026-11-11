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
package org.apache.activemq.artemis.service.extensions.xa.recovery;

import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.logs.BundleFactory;
import org.apache.activemq.artemis.logs.annotation.LogBundle;
import org.apache.activemq.artemis.logs.annotation.LogMessage;

/**
 * Loggers Code 170000 - 179999
 */
@LogBundle(projectCode = "AMQ", regexID = "17[0-9]{4}", retiredIDs = {171003, 171004, 172005, 172011, 172012, 172017, 172018, 174000, 174002})
public interface ActiveMQXARecoveryLogger {

   ActiveMQXARecoveryLogger LOGGER = BundleFactory.newBundle(ActiveMQXARecoveryLogger.class, ActiveMQXARecoveryLogger.class.getPackage().getName());

   @LogMessage(id = 172007, value = "Queue {} does not exist on the topic {}. It was deleted manually probably.", level = LogMessage.Level.WARN)
   void noQueueOnTopic(String queueName, String name);

   @LogMessage(id = 172008, value = "XA Recovery can not connect to any broker on recovery {}", level = LogMessage.Level.WARN)
   void recoveryConnectFailed(String s);

   @LogMessage(id = 172013, value = "Error in XA Recovery recover", level = LogMessage.Level.WARN)
   void xaRecoverError(Exception e);

   @LogMessage(id = 172014, value = "Notified of connection failure in xa recovery connectionFactory for provider {} will attempt reconnect on next pass",
      level = LogMessage.Level.WARN)
   void xaRecoverConnectionError(ClientSessionFactory csf, Exception e);

   @LogMessage(id = 172015, value = "Can not connect to {} on auto-generated resource recovery",
      level = LogMessage.Level.WARN)
   void xaRecoverAutoConnectionError(XARecoveryConfig csf, Throwable e);

   @LogMessage(id = 172016, value = "Error in XA Recovery", level = LogMessage.Level.DEBUG)
   void xaRecoveryError(Exception e);
}

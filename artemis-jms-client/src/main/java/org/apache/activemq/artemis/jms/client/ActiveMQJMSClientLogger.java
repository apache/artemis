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
package org.apache.activemq.artemis.jms.client;

import org.apache.activemq.artemis.logs.annotation.LogBundle;
import org.apache.activemq.artemis.logs.annotation.LogMessage;
import org.apache.activemq.artemis.logs.BundleFactory;

/**
 * Logger Codes 130000 - 138999
 */
@LogBundle(projectCode = "AMQ", regexID = "13[0-8][0-9]{3}")
public interface ActiveMQJMSClientLogger {

   ActiveMQJMSClientLogger LOGGER = BundleFactory.newBundle(ActiveMQJMSClientLogger.class, ActiveMQJMSClientLogger.class.getPackage().getName());

   @LogMessage(id = 132000, value = "I'm closing a JMS connection you left open. Please make sure you close all JMS connections explicitly before letting them go out of scope! see stacktrace to find out where it was created", level = LogMessage.Level.WARN)
   void connectionLeftOpen(Exception e);

   @LogMessage(id = 132001, value = "Unhandled exception thrown from onMessage", level = LogMessage.Level.WARN)
   void onMessageError(Exception e);

   @LogMessage(id = 134000, value = "Failed to call JMS exception listener", level = LogMessage.Level.ERROR)
   void errorCallingExcListener(Exception e);

   @LogMessage(id = 134002, value = "Queue Browser failed to create message {}", level = LogMessage.Level.ERROR)
   void errorCreatingMessage(String messageToString, Throwable e);

   @LogMessage(id = 134003, value = "Message Listener failed to prepare message for receipt, message={}", level = LogMessage.Level.ERROR)
   void errorPreparingMessageForReceipt(String messagetoString, Throwable e);

   @LogMessage(id = 134004, value = "Message Listener failed to process message", level = LogMessage.Level.ERROR)
   void errorProcessingMessage(Throwable e);

   @LogMessage(id = 134005, value = "Message Listener failed to recover session", level = LogMessage.Level.ERROR)
   void errorRecoveringSession(Throwable e);

   @LogMessage(id = 134006, value = "Failed to call Failover listener", level = LogMessage.Level.ERROR)
   void errorCallingFailoverListener(Exception e);

}

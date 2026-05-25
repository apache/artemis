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
package org.apache.activemq.artemis.ra;

import org.apache.activemq.artemis.logs.annotation.LogBundle;
import org.apache.activemq.artemis.logs.annotation.LogMessage;
import org.apache.activemq.artemis.logs.annotation.Message;
import org.apache.activemq.artemis.logs.BundleFactory;
import org.apache.activemq.artemis.ra.inflow.ActiveMQActivationSpec;

import javax.jms.IllegalStateException;

/**
 * Logger Codes 150000 - 158999
 */
@LogBundle(projectCode = "AMQ", regexID = "15[0-8][0-9]{3}", retiredIDs = {152000})
public interface ActiveMQRALogger {

   /**
    * Note this uses ActiveMQRALogger.class.getName() instead of ActiveMQRALogger.class.getPackage().getName() like the
    * other loggers, this is because some Application Servers use introspection to identify properties which can
    * sometimes use a classloader when the rar is uploaded and its possible getpackage() can return null
    */
   ActiveMQRALogger LOGGER = BundleFactory.newBundle(ActiveMQRALogger.class, ActiveMQRALogger.class.getName());

   @LogMessage(id = 151000, value = "awaiting topic/queue creation {}", level = LogMessage.Level.INFO)
   void awaitingTopicQueueCreation(String destination);

   @LogMessage(id = 151001, value = "Attempting to reconnect {}", level = LogMessage.Level.INFO)
   void attemptingReconnect(ActiveMQActivationSpec spec);

   @LogMessage(id = 151002, value = "Reconnected with broker", level = LogMessage.Level.INFO)
   void reconnected();

   @LogMessage(id = 151003, value = "resource adaptor stopped", level = LogMessage.Level.INFO)
   void raStopped();

   @LogMessage(id = 151004, value = "Instantiating {} \"{}\" directly since UseJNDI=false.", level = LogMessage.Level.DEBUG)
   void instantiatingDestination(String destinationType, String destination);

   @LogMessage(id = 151005, value = "awaiting server availability", level = LogMessage.Level.INFO)
   void awaitingJMSServerCreation();

   @LogMessage(id = 151006, value = "Cluster topology change detected. Re-balancing connections on even {}.", level = LogMessage.Level.INFO)
   void rebalancingConnections(String event);

   @LogMessage(id = 151007, value = "Resource adaptor started", level = LogMessage.Level.INFO)
   void resourceAdaptorStarted();

   @LogMessage(id = 152001, value = "problem resetting xa session after failure", level = LogMessage.Level.WARN)
   void problemResettingXASession(Throwable t);

   @LogMessage(id = 152002, value = "Unable to roll local transaction back", level = LogMessage.Level.WARN)
   void unableToRollbackTX();

   @LogMessage(id = 152003, value = "unable to reset session after failure, we will place the MDB Inflow now in setup mode for activation={}", level = LogMessage.Level.WARN)
   void unableToResetSession(String spec, Exception e);

   @LogMessage(id = 152004, value = "Handling JMS exception failure", level = LogMessage.Level.WARN)
   void handlingJMSFailure(Exception e);

   @LogMessage(id = 152005, value = "Failure in broker activation {}", level = LogMessage.Level.WARN)
   void failureInActivation(ActiveMQActivationSpec spec, Throwable t);

   @LogMessage(id = 152006, value = "Unable to call after delivery", level = LogMessage.Level.WARN)
   void unableToCallAfterDelivery(Exception e);

   @LogMessage(id = 152007, value = "Thread {} could not be finished", level = LogMessage.Level.WARN)
   void threadCouldNotFinish(String thread);

   @LogMessage(id = 152008, value = "Error interrupting handler on endpoint {} handler = {}", level = LogMessage.Level.WARN)
   void errorInterruptingHandler(String endpoint, String handler, Throwable cause);

   @LogMessage(id = 152009, value = "Unable to validate properties", level = LogMessage.Level.WARN)
   void unableToValidateProperties(Exception e);

   @LogMessage(id = 152010, value = "Unable to clear the transaction", level = LogMessage.Level.WARN)
   void unableToClearTheTransaction(Exception e);

   @LogMessage(id = 152011, value = "Unable to close the factory", level = LogMessage.Level.WARN)
   void unableToCloseFactory(Throwable e);

   @LogMessage(id = 154000, value = "Error while creating object Reference.", level = LogMessage.Level.ERROR)
   void errorCreatingReference(Exception e);

   @LogMessage(id = 154001, value = "Unable to stop resource adapter.", level = LogMessage.Level.ERROR)
   void errorStoppingRA(Exception e);

   @LogMessage(id = 154003, value = "Unable to reconnect {}", level = LogMessage.Level.ERROR)
   void errorReconnecting(ActiveMQActivationSpec spec, Throwable t);

   @LogMessage(id = 154004, value = "Failed to deliver message", level = LogMessage.Level.ERROR)
   void errorDeliveringMessage(Throwable t);

   @LogMessage(id = 153001, value = "using different ActiveMQRAConnectionFactory", level = LogMessage.Level.DEBUG)
   void warnDifferentConnectionfactory();

   @Message(id = 153002, value = "Cannot create a subscriber on the durable subscription since it already has subscriber(s)")
   IllegalStateException canNotCreatedNonSharedSubscriber();

   @LogMessage(id = 153003, value = "Unsupported acknowledgement mode {}", level = LogMessage.Level.WARN)
   void invalidAcknowledgementMode(String mode);

   @LogMessage(id = 153004, value = "Invalid number of session (negative) {}, defaulting to {}.", level = LogMessage.Level.WARN)
   void invalidNumberOfMaxSession(int value, int defaultValue);

   @LogMessage(id = 153005, value = "Unable to retrieve \"{}\" from JNDI. Creating a new \"{}\" named \"{}\" to be used by the MDB.", level = LogMessage.Level.WARN)
   void unableToRetrieveDestinationName(String destinationName, String name, String calculatedDestinationName);
}

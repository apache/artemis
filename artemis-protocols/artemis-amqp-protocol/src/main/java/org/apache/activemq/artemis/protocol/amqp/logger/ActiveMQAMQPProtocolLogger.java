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
package org.apache.activemq.artemis.protocol.amqp.logger;

import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.logs.annotation.LogBundle;
import org.apache.activemq.artemis.logs.annotation.LogMessage;
import org.apache.activemq.artemis.logs.BundleFactory;

/**
 * Logger Codes 110000 - 118999
 */
@LogBundle(projectCode = "AMQ", regexID = "11[0-8][0-9]{3}")
public interface ActiveMQAMQPProtocolLogger {

   ActiveMQAMQPProtocolLogger LOGGER = BundleFactory.newBundle(ActiveMQAMQPProtocolLogger.class, ActiveMQAMQPProtocolLogger.class.getPackage().getName());

   @LogMessage(id = 111000, value = "Scheduled task can't be removed from scheduledPool.", level = LogMessage.Level.WARN)
   void cantRemovingScheduledTask();

   @LogMessage(id = 111001, value = """

      *******************************************************************************************************************************
      Could not re-establish AMQP Server Connection {} on {} after {} retries
      *******************************************************************************************************************************
      """, level = LogMessage.Level.WARN)
   void retryConnectionFailed(String name, String hostAndPort, int currentRetry);

   @LogMessage(id = 111002, value = """

      *******************************************************************************************************************************
      Retrying Server AMQP Connection {} on {} retry {} of {}
      *******************************************************************************************************************************
      """, level = LogMessage.Level.INFO)
   void retryConnection(String name, String hostAndPort, int currentRetry, int maxRetry);

   @LogMessage(id = 111003, value = """

      *******************************************************************************************************************************
      Connected on Server AMQP Connection {} on {} after {} retries
      *******************************************************************************************************************************
      """, level = LogMessage.Level.INFO)
   void successReconnect(String name, String hostAndPort, int currentRetry);

   @LogMessage(id = 111004, value = "AddressFullPolicy clash on an anonymous producer between destinations {}(addressFullPolicy={}) and {}(addressFullPolicy={}). This could lead to semantic inconsistencies on your clients. Notice you could have other instances of this scenario however this message will only be logged once. log.debug output would show all instances of this event.", level = LogMessage.Level.WARN)
   void incompatibleAddressFullMessagePolicy(String oldAddress, String oldPolicy, String newAddress, String newPolicy);

   @LogMessage(id = 111005, value = "Failed to convert message. Sending it to Dead Letter Address.", level = LogMessage.Level.WARN)
   void messageConversionFailed(Throwable t);

   @LogMessage(id = 111006, value = "Unable to send message {} to Dead Letter Address.", level = LogMessage.Level.WARN)
   void unableToSendMessageToDLA(MessageReference ref, Throwable t);

   @LogMessage(id = 111007, value = "Invalid Connection State: {} for remote IP {}", level = LogMessage.Level.WARN)
   void invalidAMQPConnectionState(Object state, Object remoteIP);

   @LogMessage(id = 111008, value = "The AckManager timed out waiting for operations to complete on the MirrorTarget. timeout = {} milliseconds", level = LogMessage.Level.WARN)
   void timedOutAckManager(long timeout);

   @LogMessage(id = 111009, value = "The AckManager was interrupt. timeout = {} milliseconds", level = LogMessage.Level.WARN)
   void interruptedAckManager(Exception e);

   @LogMessage(id = 111010, value = "Duplicate AckManager node detected. Queue={}, ServerID={}, recordID={}", level = LogMessage.Level.WARN)
   void duplicateNodeStoreID(String queue, String serverId, long recordID, Exception trace);

   @LogMessage(id = 111011, value = "There are {} consumers on queue {}, what made the Ack for message with nodeID={}, messageID={} enter a retry list", level = LogMessage.Level.WARN)
   void unackWithConsumer(int numberOfConsumers, Object queueName, String nodeID, long messageID);

   @LogMessage(id = 111012, value = "Acknowledgement retry failed for {} on address {}, queueID={}", level = LogMessage.Level.WARN)
   void ackRetryFailed(Object ackRetryInformation, Object address, long queueID);

   @LogMessage(id = 111013, value = "AMQP Federation target skipped federation of address {}, reason={}", level = LogMessage.Level.WARN)
   void federationTargetSkippedAddressFederation(String address, String reason);

   @LogMessage(id = 111014, value = "AMQP Federation target skipped federation of queue {}, reason={}", level = LogMessage.Level.WARN)
   void federationTargetSkippedQueueFederation(String address, String reason);

   @LogMessage(id = 111015, value = "AMQP message {} has invalid application properties encoding. {}", level = LogMessage.Level.WARN)
   void invalidEncodingApplicationProperties(long messageID, String description);
}

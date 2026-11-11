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
package org.apache.activemq.artemis.jms.bridge;

import javax.management.ObjectName;

import org.apache.activemq.artemis.logs.annotation.LogBundle;
import org.apache.activemq.artemis.logs.annotation.LogMessage;
import org.apache.activemq.artemis.logs.BundleFactory;

/**
 * Logger Codes 340000 - 349999
 */
@LogBundle(projectCode = "AMQ", regexID = "34[0-9]{4}")
public interface ActiveMQJMSBridgeLogger {

   ActiveMQJMSBridgeLogger LOGGER = BundleFactory.newBundle(ActiveMQJMSBridgeLogger.class, ActiveMQJMSBridgeLogger.class.getPackage().getName());

   @LogMessage(id = 341000, value = "Failed to set up JMS bridge {}} connections. Most probably the source or target servers are unavailable." + " Will retry after a pause of {} ms", level = LogMessage.Level.INFO)
   void failedToSetUpBridge(String bridgeName, long failureRetryInterval);

   @LogMessage(id = 341001, value = "JMS Bridge {} succeeded in reconnecting to servers", level = LogMessage.Level.INFO)
   void bridgeReconnected(String bridgeName);

   @LogMessage(id = 341002, value = "JMSBridge {} succeeded in connecting to servers", level = LogMessage.Level.INFO)
   void bridgeConnected(String bridgeName);

   @LogMessage(id = 342000, value = "Attempt to start JMS Bridge {}, but is already started", level = LogMessage.Level.WARN)
   void errorBridgeAlreadyStarted(String bridgeName);

   @LogMessage(id = 342001, value = "Failed to start JMS Bridge {}", level = LogMessage.Level.WARN)
   void errorStartingBridge(String bridgeName);

   @LogMessage(id = 342002, value = "Failed to unregisted JMS Bridge {} - {}", level = LogMessage.Level.WARN)
   void errorUnregisteringBridge(ObjectName objectName, String bridgeName);

   @LogMessage(id = 342003, value = "JMS Bridge {} unable to set up connections, bridge will be stopped", level = LogMessage.Level.WARN)
   void errorConnectingBridge(String bridgeName);

   @LogMessage(id = 342004, value = "JMS Bridge {}, will retry after a pause of {} ms", level = LogMessage.Level.WARN)
   void bridgeRetry(String bridgeName, long failureRetryInterval);

   @LogMessage(id = 342005, value = "JMS Bridge {} unable to set up connections, bridge will not be started", level = LogMessage.Level.WARN)
   void bridgeNotStarted(String bridgeName);

   @LogMessage(id = 342006, value = "JMS Bridge {}, detected failure on bridge connection", level = LogMessage.Level.WARN)
   void bridgeFailure(String bridgeName, Exception e);

   @LogMessage(id = 342009, value = "JMS Bridge {} failed to send + acknowledge batch, closing JMS objects", level = LogMessage.Level.WARN)
   void bridgeAckError(String bridgeName, Exception e);

   @LogMessage(id = 342010, value = "Failed to connect JMS Bridge {}", level = LogMessage.Level.WARN)
   void bridgeConnectError(String bridgeName, Exception e);

   @LogMessage(id = 342011, value = "Transaction rolled back, retrying TX", level = LogMessage.Level.WARN)
   void transactionRolledBack(Exception e);

   @LogMessage(id = 344001, value = "JMS Bridge {}, failed to start source connection", level = LogMessage.Level.ERROR)
   void jmsBridgeSrcConnectError(String bridgeName, Exception e);

   @LogMessage(id = 344002, value = "Failed to start JMS Bridge {}.  QoS Mode: {} requires a Transaction Manager, none found", level = LogMessage.Level.ERROR)
   void jmsBridgeTransactionManagerMissing(String bridgeName, QualityOfServiceMode qosMode);
}

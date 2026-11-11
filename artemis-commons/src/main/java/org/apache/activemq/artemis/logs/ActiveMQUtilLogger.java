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
package org.apache.activemq.artemis.logs;

import org.apache.activemq.artemis.logs.annotation.LogBundle;
import org.apache.activemq.artemis.logs.annotation.LogMessage;

/**
 * Logger Codes 200000 - 208999
 */
@LogBundle(projectCode = "AMQ", regexID = "20[0-8][0-9]{3}", retiredIDs = {202000, 202013, 202014})
public interface ActiveMQUtilLogger {

   ActiveMQUtilLogger LOGGER = BundleFactory.newBundle(ActiveMQUtilLogger.class, ActiveMQUtilLogger.class.getPackage().getName());

   @LogMessage(id = 201000, value = "Network is healthy, starting service {}", level = LogMessage.Level.INFO)
   void startingService(String component);

   @LogMessage(id = 201001, value = "Network is unhealthy, stopping service {}", level = LogMessage.Level.WARN)
   void stoppingService(String component);

   @LogMessage(id = 202001, value = "{} is a loopback address and will be discarded.", level = LogMessage.Level.WARN)
   void addressloopback(String address);

   @LogMessage(id = 202002, value = "Ping Address {} wasn't reacheable.", level = LogMessage.Level.WARN)
   void addressWasntReacheable(String address);

   @LogMessage(id = 202003, value = "Ping Url {} wasn't reacheable.", level = LogMessage.Level.WARN)
   void urlWasntReacheable(String url);

   @LogMessage(id = 202004, value = "Error starting component {} ", level = LogMessage.Level.WARN)
   void errorStartingComponent(String component, Exception e);

   @LogMessage(id = 202005, value = "Error stopping component {} ", level = LogMessage.Level.WARN)
   void errorStoppingComponent(String component, Exception e);

   @LogMessage(id = 202006, value = "Failed to check Url {}.", level = LogMessage.Level.WARN)
   void failedToCheckURL(String url, Exception e);

   @LogMessage(id = 202007, value = "Failed to check Address {}.", level = LogMessage.Level.WARN)
   void failedToCheckAddress(String address, Exception e);

   @LogMessage(id = 202008, value = "Failed to check Address list {}.", level = LogMessage.Level.WARN)
   void failedToParseAddressList(String addressList, Exception e);

   @LogMessage(id = 202009, value = "Failed to check Url list {}.", level = LogMessage.Level.WARN)
   void failedToParseUrlList(String urlList, Exception e);

   @LogMessage(id = 202010, value = "Failed to set NIC {}.", level = LogMessage.Level.WARN)
   void failedToSetNIC(String nic, Exception e);

   @LogMessage(id = 202011, value = "Failed to read from stream {}.", level = LogMessage.Level.WARN)
   void failedToReadFromStream(String stream);

   @LogMessage(id = 202012, value = "Object cannot be serialized.", level = LogMessage.Level.WARN)
   void failedToSerializeObject(Exception e);

   @LogMessage(id = 202015, value = "Failed to clean up file {}", level = LogMessage.Level.WARN)
   void failedToCleanupFile(String file);

   @LogMessage(id = 202016, value = "Could not list files to clean up in {}", level = LogMessage.Level.WARN)
   void failedListFilesToCleanup(String path);

   @LogMessage(id = 202017, value = "Algorithm two-way is deprecated and will be removed from the default codec in a future version. Use a custom codec instead. Consult the manual for details.", level = LogMessage.Level.WARN)
   void deprecatedDefaultCodecTwoWayAlgorithm();
}

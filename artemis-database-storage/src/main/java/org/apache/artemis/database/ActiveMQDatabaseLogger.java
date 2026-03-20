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
package org.apache.artemis.database;

import org.apache.activemq.artemis.logs.BundleFactory;
import org.apache.activemq.artemis.logs.annotation.LogBundle;
import org.apache.activemq.artemis.logs.annotation.LogMessage;

/**
 * Logger Codes 230000 - 238999
 */
@LogBundle(projectCode = "AMQ", regexID = "23[0-8][0-9]{3}")
public interface ActiveMQDatabaseLogger {

   ActiveMQDatabaseLogger LOGGER = BundleFactory.newBundle(ActiveMQDatabaseLogger.class, ActiveMQDatabaseLogger.class.getPackage().getName());

   @LogMessage(id = 232000, value = "Queue {} (id={}) on table {} does not have the json configuration. Reconstructing the object individually from the fields.", level = LogMessage.Level.WARN)
   void queueMissingJsonConfig(String queueName, long queueId, String tableName);

   @LogMessage(id = 232001, value = "Retrying SQL action (attempt {} of {}, interval={}ms) after failure: {}", level = LogMessage.Level.WARN)
   void retrySQLAction(int attempt, int maxRetries, long intervalMillis, String errorMessage, Throwable e);

   @LogMessage(id = 232002, value = "Borrowed read connections ({}) exceeded database-max-read-connections ({}). Demanding return. Consider increasing database-max-read-connections or database-connections if this happens frequently:\n{}", level = LogMessage.Level.INFO)
   void demandingBorrowedConnectionsBack(int borrowedCount, int maxReadConnections, String tableOutput);
}

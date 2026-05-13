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
package org.apache.activemq.artemis;

import org.apache.activemq.artemis.logs.BundleFactory;
import org.apache.activemq.artemis.logs.annotation.LogBundle;
import org.apache.activemq.artemis.logs.annotation.LogMessage;

/**
 * Logger Codes 240000 - 249999
 */
@LogBundle(projectCode = "AMQ", regexID = "24[0-9]{4}", retiredIDs = {244003})
public interface ActiveMQWebLogger {

   ActiveMQWebLogger LOGGER = BundleFactory.newBundle(ActiveMQWebLogger.class, ActiveMQWebLogger.class.getPackage().getName());

   @LogMessage(id = 241001, value = "HTTP Server started at {}", level = LogMessage.Level.INFO)
   void webserverStarted(String bind);

   @LogMessage(id = 241002, value = "Artemis Jolokia REST API available at {}", level = LogMessage.Level.INFO)
   void jolokiaAvailable(String bind);

   @LogMessage(id = 241003, value = "Starting embedded web server", level = LogMessage.Level.INFO)
   void startingEmbeddedWebServer();

   @LogMessage(id = 241004, value = "Artemis Console available at {}", level = LogMessage.Level.INFO)
   void consoleAvailable(String bind);

   @LogMessage(id = 241005, value = "Stopping embedded web server", level = LogMessage.Level.INFO)
   void stoppingEmbeddedWebServer();

   @LogMessage(id = 241006, value = "Stopped embedded web server", level = LogMessage.Level.INFO)
   void stoppedEmbeddedWebServer();

   @LogMessage(id = 244005, value = "Web customizer {} not loaded: {}", level = LogMessage.Level.WARN)
   void customizerNotLoaded(String customizer, Throwable t);
}

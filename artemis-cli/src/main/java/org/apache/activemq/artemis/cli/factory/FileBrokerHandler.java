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
package org.apache.activemq.artemis.cli.factory;

import org.apache.activemq.artemis.core.server.ActivateCallback;
import org.apache.activemq.artemis.dto.ServerDTO;
import org.apache.activemq.artemis.integration.Broker;
import org.apache.activemq.artemis.integration.FileBroker;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;

public class FileBrokerHandler implements BrokerHandler {

   @Override
   public String getName() {
      return "file";
   }

   @Override
   public Broker createServer(ServerDTO brokerDTO, ActiveMQSecurityManager security, ActivateCallback activateCallback) {
      return new FileBroker(brokerDTO, security, activateCallback);
   }
}

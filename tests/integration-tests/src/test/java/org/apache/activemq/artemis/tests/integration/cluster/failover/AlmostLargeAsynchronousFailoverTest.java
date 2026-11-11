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
package org.apache.activemq.artemis.tests.integration.cluster.failover;

import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.core.client.impl.ServerLocatorInternal;

/**
 * Validating failover when the size of the message Size > flow Control && message Size < minLargeMessageSize
 */
public class AlmostLargeAsynchronousFailoverTest extends AsynchronousFailoverTest {

   @Override
   protected void createConfigs() throws Exception {
      super.createConfigs();
      primaryServer.getServer().getConfiguration().setJournalFileSize(1024 * 1024);
      backupServer.getServer().getConfiguration().setJournalFileSize(1024 * 1024);
   }

   @Override
   protected ServerLocatorInternal getServerLocator() throws Exception {
      return (ServerLocatorInternal) super.getServerLocator().setMinLargeMessageSize(1024 * 1024).setProducerWindowSize(10 * 1024);
   }

   @Override
   protected void addPayload(ClientMessage message) {
      message.putBytesProperty("payload", new byte[20 * 1024]);
   }

}

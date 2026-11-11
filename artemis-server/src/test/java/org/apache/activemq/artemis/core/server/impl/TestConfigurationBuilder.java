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
package org.apache.activemq.artemis.core.server.impl;

import org.apache.activemq.artemis.core.config.Configuration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Helper for creating minimal Configuration mocks for FileLockNodeManager tests.
 * This allows tests to use the production Configuration-based constructor
 * without needing a full Configuration object.
 */
class TestConfigurationBuilder {

   /**
    * Create a minimal Configuration mock with only lock-related settings.
    *
    * @param lockAcquisitionTimeout journal lock acquisition timeout in milliseconds
    * @param journalLockMonitorTimeout journal lock monitor check period in milliseconds
    * @param journalLockMonitorMaxRetries maximum retries for failed journal lock checks
    * @return Configuration mock configured for lock testing
    */
   static Configuration forLockTesting(long lockAcquisitionTimeout,
                                       long journalLockMonitorTimeout,
                                       int journalLockMonitorMaxRetries) {
      Configuration config = mock(Configuration.class);
      when(config.getJournalLockAcquisitionTimeout()).thenReturn(lockAcquisitionTimeout);
      when(config.getJournalLockMonitorTimeout()).thenReturn(journalLockMonitorTimeout);
      when(config.getJournalLockMonitorMaxRetries()).thenReturn(journalLockMonitorMaxRetries);
      return config;
   }
}

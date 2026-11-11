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

import org.apache.activemq.artemis.core.server.ServiceComponent;

/**
 * used by tests that are simulating a WebServer that should or should not go down
 */
public class FakeServiceComponent implements ServiceComponent {

   final String description;

   public FakeServiceComponent(String description) {
      this.description = description;
   }

   boolean started = false;

   @Override
   public String toString() {
      return description;
   }

   @Override
   public void start() throws Exception {
      started = true;
   }

   @Override
   public void stop() throws Exception {
      stop(true);
   }

   @Override
   public boolean isStarted() {
      return started;
   }

   @Override
   public void stop(boolean shutdown) throws Exception {
      if (shutdown) {
         started = false;
      }
   }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.server.federation;

import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.MessageHandler;

public interface FederatedQueueConsumer extends MessageHandler {

   String FEDERATION_NAME = "federation-name";
   String FEDERATION_UPSTREAM_NAME = "federation-upstream-name";

   static int getNextDelay(int delay, int delayMultiplier, int delayMax) {
      int nextDelay;
      if (delay == 0) {
         nextDelay = 1;
      } else {
         nextDelay = delay * delayMultiplier;
         if (nextDelay > delayMax) {
            nextDelay = delayMax;
         }
      }
      return nextDelay;
   }

   FederationUpstream getFederationUpstream();

   Federation getFederation();

   FederatedConsumerKey getKey();

   ClientSession getClientSession();

   int incrementCount();

   int decrementCount();

   void start();

   void close();
}

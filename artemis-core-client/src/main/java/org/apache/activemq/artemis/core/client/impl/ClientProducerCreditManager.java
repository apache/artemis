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
package org.apache.activemq.artemis.core.client.impl;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.spi.core.remoting.SessionContext;

public interface ClientProducerCreditManager {

   ClientProducerCredits getCredits(SimpleString address, boolean anon, SessionContext context);

   void returnCredits(SimpleString address);

   void receiveCredits(SimpleString address, int credits);

   void receiveFailCredits(SimpleString address, int credits);

   void reset();

   void close();

   int creditsMapSize();

   int getMaxAnonymousCacheSize();

   /**
    * This will determine the flow control as asynchronous, no actual block should happen instead a callback will be
    * sent whenever blockages change
    */
   void setCallback(ClientProducerFlowCallback callback);
}

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
package org.apache.activemq.artemis.junit;

import org.apache.activemq.artemis.api.core.client.ClientMessage;

public interface ActiveMQConsumerOperations {

   long getDefaultReceiveTimeout();

   /**
    * Sets the default timeout in milliseconds used when receiving messages. Defaults to 50 milliseconds
    *
    * @param defaultReceiveTimeout received timeout in milliseconds
    */
   void setDefaultReceiveTimeout(long defaultReceiveTimeout);

   boolean isAutoCreateQueue();

   /**
    * Enable/Disable the automatic creation of non-existent queues. The default is to automatically create non-existent
    * queues
    */
   void setAutoCreateQueue(boolean autoCreateQueue);

   ClientMessage receiveMessage();

   ClientMessage receiveMessage(long timeout);

}
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
package org.apache.activemq.artemis.api.core.client;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.core.remoting.FailureListener;

/**
 * A SessionFailureListener notifies the client when a failure occurred on the session.
 */
public interface SessionFailureListener extends FailureListener {

   /**
    * Notifies that a connection has failed due to the specified exception.
    * <p>
    * This method is called <em>before the session attempts to reconnect/failover</em>.
    *
    * @param exception exception which has caused the connection to fail
    */
   void beforeReconnect(ActiveMQException exception);
}

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
package org.apache.activemq.artemis.api.core;

/**
 * The server version and the client version are incompatible.
 * <p>
 * Normally this means you are trying to use a newer client on an older server.
 */
public final class ActiveMQIncompatibleClientServerException extends ActiveMQException {

   private static final long serialVersionUID = -1662999230291452298L;

   public ActiveMQIncompatibleClientServerException() {
      super(ActiveMQExceptionType.INCOMPATIBLE_CLIENT_SERVER_VERSIONS);
   }

   public ActiveMQIncompatibleClientServerException(String msg) {
      super(ActiveMQExceptionType.INCOMPATIBLE_CLIENT_SERVER_VERSIONS, msg);
   }
}

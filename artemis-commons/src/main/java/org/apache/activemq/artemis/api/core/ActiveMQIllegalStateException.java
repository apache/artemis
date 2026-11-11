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
 * An Apache Artemis resource is not in a legal state (e.g. calling ClientConsumer.receive() if a MessageHandler is
 * set).
 */
public final class ActiveMQIllegalStateException extends ActiveMQException {

   private static final long serialVersionUID = -4480125401057788511L;

   public ActiveMQIllegalStateException() {
      super(ActiveMQExceptionType.ILLEGAL_STATE);
   }

   public ActiveMQIllegalStateException(String message) {
      super(ActiveMQExceptionType.ILLEGAL_STATE, message);
   }
}

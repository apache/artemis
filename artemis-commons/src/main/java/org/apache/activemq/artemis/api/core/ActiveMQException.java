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
 * ActiveMQException is the root exception for the Apache Artemis API.
 */
public class ActiveMQException extends Exception {

   private static final long serialVersionUID = -4802014152804997417L;

   private final ActiveMQExceptionType type;

   public ActiveMQException() {
      type = ActiveMQExceptionType.GENERIC_EXCEPTION;
   }

   public ActiveMQException(final String msg) {
      super(msg);
      type = ActiveMQExceptionType.GENERIC_EXCEPTION;
   }

   public ActiveMQException(final String msg, Throwable t) {
      super(msg, t);
      type = ActiveMQExceptionType.GENERIC_EXCEPTION;
   }

   public ActiveMQException(String msg, ActiveMQExceptionType t) {
      super(msg);
      type = t;
   }

   public ActiveMQException(String message, Throwable t, ActiveMQExceptionType type) {
      super(message, t);
      this.type = type;
   }

   /**
    * This constructor is needed only for the native layer
    */
   public ActiveMQException(int code, String msg) {
      super(msg);

      this.type = ActiveMQExceptionType.getType(code);
   }

   public ActiveMQException(ActiveMQExceptionType type, String msg) {
      super(msg);

      this.type = type;
   }

   public ActiveMQException(ActiveMQExceptionType type) {
      super("");
      this.type = type;
   }

   public ActiveMQException(ActiveMQExceptionType type, String message, Throwable t) {
      super(message, t);
      this.type = type;
   }

   public ActiveMQExceptionType getType() {
      return type;
   }

   @Override
   public String toString() {
      return this.getClass().getName() + ": [errorType=" + type + " message=" + getMessage() + "]";
   }
}
